package com.ga.airdrop.feature.contacts

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropUser
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Live Agent Chat data path — Android counterpart of Swift
 * `AirdropAPI.startAutoPilotAppChat` / `sendAutoPilotAppChatMessage`
 * / `autoPilotAppChatMessages` (AirdropAPI.swift:1702-2012).
 *
 * Flow (per-user HMAC architecture, 2026-05-24):
 *   1. POST {laravel}/autopilot/identity with the Sanctum bearer — mints a
 *      short-lived publishable_key + user_id + identity_hash trio.
 *   2. Talk DIRECTLY to the chat vendor endpoint with that trio in headers.
 *      The Sanctum bearer MUST NOT leak to the vendor host, so vendor calls
 *      use a bare OkHttpClient without AuthInterceptor.
 *   3. POST /session -> conversation_id (+ history). NOTE: a brand-new
 *      user's first session can return the EXTERNAL id; the send response
 *      always carries the canonical UUID and we adopt it (Swift does the
 *      same at FigmaRouteViewController.swift:1871-1875).
 *   4. POST /messages -> inline bot reply in `message`/`reply` +
 *      canonical conversation_id. GET /conversations/{id}/messages polls
 *      for anything that arrives after the inline reply.
 */
object LiveAgentChatRepository {

    // ── Wire models (all lenient — the vendor has shipped several envelope
    // shapes concurrently, same tolerance as Swift's decoders). ──

    data class ChatIdentity(
        val endpoint: String,
        val publishableKey: String,
        val userId: String,
        val identityHash: String,
        val profileName: String?,
        val profileEmail: String?,
        val profilePhone: String?,
        val profileAccountNumber: String?,
    )

    data class ChatMessage(
        val id: String,
        val body: String,
        val isCustomer: Boolean,
        val senderName: String?,
        val createdAt: String?,
    )

    data class ChatSession(
        val conversationId: String,
        val messages: List<ChatMessage>,
    )

    data class ChatSendResult(
        val conversationId: String?,
        val replyMessage: ChatMessage?,
    )

    class ChatException(message: String) : Exception(message)

    // Bare client for vendor-direct calls. Long read timeout: the vendor
    // generates the bot reply inline during POST /messages (~5-15s).
    private val vendorHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // ── Public API ──

    /** Mint the per-user identity trio via Airdrop Laravel (Sanctum). */
    suspend fun mintIdentity(): ChatIdentity = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/autopilot/identity")
            .post("{}".toRequestBody(JSON))
            .build()
        ApiClient.okHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ChatException("Identity mint failed (${response.code})")
            }
            parseIdentity(body)
                ?: throw ChatException("Identity mint returned an unreadable payload")
        }
    }

    /** Current profile for the customer payload (best-effort). */
    suspend fun currentUser(): AirdropUser? = runCatching {
        ApiClient.service.currentUser().user
    }.getOrNull()

    /** Open (or resume) the chat session; returns conversation id + history. */
    suspend fun startSession(identity: ChatIdentity, user: AirdropUser?): ChatSession =
        withContext(Dispatchers.IO) {
            val payload = buildString {
                append("""{"source":"airdrop_android","customer":""")
                append(customerJson(identity, user))
                append(
                    ""","metadata":{"app_name":"Airdrop",""" +
                        """"integration":"autopilot_crm_app_channel",""" +
                        """"preferred_agent":"nirvana","agent_name":"Nirvana"}}""",
                )
            }
            val body = vendorPost(identity, "/api/v1/app-channels/session", payload)
            parseSession(body)
                ?: throw ChatException("Chat session did not return a conversation")
        }

    /** Send one customer message; returns the canonical id + inline reply. */
    suspend fun sendMessage(
        identity: ChatIdentity,
        conversationId: String,
        text: String,
        user: AirdropUser?,
    ): ChatSendResult = withContext(Dispatchers.IO) {
        val payload = buildString {
            append("""{"body":""")
            append(jsonString(text))
            append(""","message":""")
            append(jsonString(text))
            append(""","clientApp":"airdrop-android","conversationId":""")
            append(jsonString(conversationId))
            append(""","externalMessageId":""")
            append(jsonString(UUID.randomUUID().toString()))
            append(""","sender":"customer","messageType":"text","customer":""")
            append(customerJson(identity, user))
            append("}")
        }
        val body = vendorPost(identity, "/api/v1/app-channels/messages", payload)
        parseSendResult(body)
    }

    /** Poll conversation history (canonical UUID only — see class doc). */
    suspend fun pollMessages(identity: ChatIdentity, conversationId: String): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(
                    identity.endpoint.trimEnd('/') +
                        "/api/v1/app-channels/conversations/$conversationId/messages",
                )
                .get()
                .apply { vendorHeaders(identity) }
                .build()
            vendorHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parseMessages(response.body?.string().orEmpty())
            }
        }

    // ── HTTP helpers ──

    private fun Request.Builder.vendorHeaders(identity: ChatIdentity): Request.Builder = apply {
        header("Accept", "application/json")
        header("x-autopilot-publishable-key", identity.publishableKey)
        header("x-autopilot-user-id", identity.userId)
        header("x-autopilot-identity-hash", identity.identityHash)
    }

    private fun vendorPost(identity: ChatIdentity, path: String, jsonPayload: String): String {
        val request = Request.Builder()
            .url(identity.endpoint.trimEnd('/') + path)
            .post(jsonPayload.toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .apply { vendorHeaders(identity) }
            .build()
        vendorHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ChatException("Chat request failed (${response.code})")
            }
            return body
        }
    }

    private fun customerJson(identity: ChatIdentity, user: AirdropUser?): String {
        val name = listOfNotNull(user?.firstName, user?.lastName)
            .joinToString(" ")
            .ifBlank { identity.profileName ?: "" }
        val account = user?.accountNumber ?: identity.profileAccountNumber ?: ""
        val email = user?.email ?: identity.profileEmail
        val phone = user?.phone ?: identity.profilePhone
        return buildString {
            append("""{"externalId":""")
            append(jsonString(identity.userId))
            append(""","accountNumber":""")
            append(jsonString(account))
            append(""","name":""")
            append(jsonString(name))
            append(""","email":""")
            append(jsonString(email ?: ""))
            append(""","phone":""")
            append(jsonString(phone ?: ""))
            append(""","platform":"android","appBundleId":""")
            append(jsonString(BuildConfig.APPLICATION_ID))
            append("}")
        }
    }

    // ── Parsing (pure functions — unit-tested without Android) ──

    internal fun parseIdentity(body: String): ChatIdentity? {
        val root = runCatching { AirdropJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        val obj = (root["data"] as? JsonObject) ?: root
        fun field(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull
        val endpoint = field("endpoint") ?: return null
        val publishableKey = field("publishable_key") ?: return null
        val userId = field("user_id") ?: return null
        val identityHash = field("identity_hash") ?: return null
        val profile = obj["user_profile"] as? JsonObject
        fun profileField(key: String): String? = (profile?.get(key) as? JsonPrimitive)?.contentOrNull
        return ChatIdentity(
            endpoint = endpoint,
            publishableKey = publishableKey,
            userId = userId,
            identityHash = identityHash,
            profileName = profileField("name"),
            profileEmail = profileField("email"),
            profilePhone = profileField("phone"),
            profileAccountNumber = profileField("account_number"),
        )
    }

    internal fun parseSession(body: String): ChatSession? {
        val root = runCatching { AirdropJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        // Envelope tolerance: bare, {session:{}}, {data:{}}, {conversation:{}}.
        val obj = listOfNotNull(
            root["session"] as? JsonObject,
            root["data"] as? JsonObject,
            root["conversation"] as? JsonObject,
        ).firstOrNull { it.conversationId() != null } ?: root
        val conversationId = obj.conversationId() ?: return null
        return ChatSession(conversationId = conversationId, messages = parseMessages(body))
    }

    internal fun parseSendResult(body: String): ChatSendResult {
        val root = runCatching { AirdropJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return ChatSendResult(conversationId = null, replyMessage = null)
        val conversationId = root.conversationId()
        val replyObj = root["message"] as? JsonObject
        val replyMessage = replyObj?.let(::messageFrom)
            ?: (root["reply"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    body = it,
                    isCustomer = false,
                    senderName = "Nirvana",
                    createdAt = null,
                )
            }
        return ChatSendResult(conversationId = conversationId, replyMessage = replyMessage)
    }

    internal fun parseMessages(body: String): List<ChatMessage> {
        val root = runCatching { AirdropJson.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val array = when {
            root is kotlinx.serialization.json.JsonArray -> root
            root is JsonObject -> runCatching { root.jsonObject["messages"]?.jsonArray }.getOrNull()
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let(::messageFrom) }
    }

    private fun messageFrom(obj: JsonObject): ChatMessage? {
        fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull {
            (obj[it] as? JsonPrimitive)?.contentOrNull
        }
        val bodyText = field("body", "content", "text", "message") ?: return null
        if (bodyText.isBlank()) return null
        val direction = field("direction").orEmpty().lowercase()
        val senderType = field("sender_type", "senderType").orEmpty().lowercase()
        val isCustomer = direction == "inbound" || direction == "customer" ||
            senderType == "customer" || senderType == "user"
        return ChatMessage(
            id = field("id") ?: UUID.randomUUID().toString(),
            body = bodyText,
            isCustomer = isCustomer,
            senderName = field("sender_name", "senderName"),
            createdAt = field("created_at", "createdAt"),
        )
    }

    private fun JsonObject.conversationId(): String? =
        (this["conversation_id"] as? JsonPrimitive)?.contentOrNull
            ?: (this["conversationId"] as? JsonPrimitive)?.contentOrNull

    private fun jsonString(value: String): String =
        AirdropJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))

    private val JSON = "application/json".toMediaType()
}
