package com.ga.airdrop.feature.contacts

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.repo.UserRepository
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val LiveChatJsonMediaType = "application/json".toMediaType()

internal data class AutoPilotIdentity(
    val endpoint: String,
    val publishableKey: String,
    val userId: String,
    val identityHash: String,
    val userProfile: AutoPilotIdentityUserProfile,
)

internal data class AutoPilotIdentityUserProfile(
    val name: String?,
    val email: String?,
    val phone: String?,
    val accountNumber: String?,
)

internal data class AutoPilotAppChatCustomer(
    val externalId: String?,
    val accountNumber: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val platform: String = "android",
    val appBundleId: String = BuildConfig.APPLICATION_ID,
)

internal data class AutoPilotAppChatSession(
    val conversationId: String,
    val channelId: String?,
    val status: String?,
    val assignedAgentName: String?,
    val messages: List<AutoPilotAppChatMessage>,
)

internal data class AutoPilotAppChatMessage(
    val id: String,
    val body: String,
    val direction: String,
    val senderName: String?,
    val senderType: String?,
    val createdAt: String?,
    val deliveryStatus: String?,
) {
    val isCustomerAuthored: Boolean
        get() {
            val normalizedDirection = direction.lowercase()
            val normalizedType = senderType.orEmpty().lowercase()
            return normalizedDirection == "inbound" ||
                normalizedDirection == "customer" ||
                normalizedType == "customer" ||
                normalizedType == "user"
        }
}

internal data class AutoPilotAppChatSendResult(
    val conversationId: String?,
    val reply: String?,
    val message: AutoPilotAppChatMessage?,
)

internal class LiveAgentChatRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val airdropClient: OkHttpClient = ApiClient.okHttp,
    private val directClient: OkHttpClient = directOkHttp(),
    private val json: Json = ApiClient.json,
    private val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    private val now: () -> String = { DateTimeFormatter.ISO_INSTANT.format(Instant.now()) },
) {
    private var cachedIdentity: AutoPilotIdentity? = null

    suspend fun currentUser(): AirdropUser =
        userRepository.currentUser().getOrThrow()

    suspend fun startSession(user: AirdropUser): AutoPilotAppChatSession =
        withContext(Dispatchers.IO) {
            val identity = identity()
            val customer = customer(identity, user)
            val body = buildJsonObject {
                put("source", "airdrop_android")
                put("customer", customer.toJson())
                put("metadata", sessionMetadata(user))
            }.toString()

            val raw = autoPilotDirect(
                identity = identity,
                path = "/api/v1/app-channels/session",
                method = "POST",
                body = body,
            )
            val session = parseSessionResponse(raw, json)
            if (session.conversationId.isBlank()) {
                throw LiveAgentChatException(
                    userMessage = "Couldn't start chat. Please try again.",
                    kind = LiveAgentChatErrorKind.Server,
                )
            }
            session
        }

    suspend fun messages(conversationId: String): List<AutoPilotAppChatMessage> =
        withContext(Dispatchers.IO) {
            val identity = identity()
            parseMessagesResponse(
                autoPilotDirect(
                    identity = identity,
                    path = "/api/v1/app-channels/conversations/$conversationId/messages",
                    method = "GET",
                ),
                json,
            )
        }

    suspend fun sendMessage(
        conversationId: String,
        body: String,
        user: AirdropUser,
    ): AutoPilotAppChatSendResult =
        withContext(Dispatchers.IO) {
            val identity = identity()
            val customer = customer(identity, user)
            val payload = buildJsonObject {
                put("body", body)
                put("message", body)
                put("clientApp", "airdrop-android")
                put("conversationId", conversationId)
                put("externalMessageId", UUID.randomUUID().toString())
                put("sender", "customer")
                put("messageType", "text")
                put("customer", customer.toJson())
                put("metadata", messageMetadata(user))
            }.toString()

            parseSendResultResponse(
                autoPilotDirect(
                    identity = identity,
                    path = "/api/v1/app-channels/messages",
                    method = "POST",
                    body = payload,
                ),
                json,
            )
        }

    private fun identity(): AutoPilotIdentity {
        cachedIdentity?.let { return it }
        val url = "${apiBaseUrl.trimEnd('/')}/autopilot/identity"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post("{}".toRequestBody(LiveChatJsonMediaType))
            .build()
        val raw = execute(airdropClient, request, vendorRequest = false)
        return parseIdentityResponse(raw, json).also { cachedIdentity = it }
    }

    private fun autoPilotDirect(
        identity: AutoPilotIdentity,
        path: String,
        method: String,
        body: String? = null,
    ): String {
        val base = identity.endpoint.trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val url = "$base$normalizedPath".toHttpUrlOrNull()
        if (url == null || url.scheme.lowercase() != "https") {
            throw LiveAgentChatException(
                userMessage = "Chat is misconfigured. Please contact support.",
                kind = LiveAgentChatErrorKind.InvalidUrl,
            )
        }

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("x-autopilot-publishable-key", identity.publishableKey)
            .header("x-autopilot-user-id", identity.userId)
            .header("x-autopilot-identity-hash", identity.identityHash)
            .header("x-autopilot-client-app", "airdrop-android")

        if (body != null) {
            builder.header("Content-Type", "application/json")
        }

        val request = when (method.uppercase()) {
            "GET" -> builder.get().build()
            "POST" -> builder.post((body ?: "{}").toRequestBody(LiveChatJsonMediaType)).build()
            else -> error("Unsupported method $method")
        }

        return try {
            execute(directClient, request, vendorRequest = true)
        } catch (err: LiveAgentChatException) {
            if (err.status == 401 || err.status == 403) cachedIdentity = null
            throw err
        }
    }

    private fun execute(
        client: OkHttpClient,
        request: Request,
        vendorRequest: Boolean,
    ): String {
        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) return raw
                throw exceptionForStatus(response.code, raw, vendorRequest)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: LiveAgentChatException) {
            throw err
        } catch (err: IOException) {
            throw LiveAgentChatException(
                userMessage = "No connection — check your internet.",
                kind = LiveAgentChatErrorKind.Transport,
                cause = err,
            )
        }
    }

    private fun exceptionForStatus(status: Int, body: String, vendorRequest: Boolean): LiveAgentChatException {
        val parsed = serverMessage(body, json)?.takeIf(::isUserSafe)
        val userMessage = when {
            status == 401 || status == 403 -> "Sign in to chat"
            parsed != null -> parsed
            status in 500..599 -> "Chat is reconnecting. Please try again."
            vendorRequest -> "Couldn't reach chat (HTTP $status)."
            else -> "Couldn't start chat. Please try again."
        }
        return LiveAgentChatException(
            userMessage = userMessage,
            kind = if (status == 401 || status == 403) LiveAgentChatErrorKind.Auth else LiveAgentChatErrorKind.Http,
            status = status,
        )
    }

    private fun customer(identity: AutoPilotIdentity, user: AirdropUser): AutoPilotAppChatCustomer {
        val account = identity.userProfile.accountNumber.clean()
            ?: user.accountNumber.clean()
            ?: ""
        val fallbackName = listOfNotNull(user.firstName.clean(), user.lastName.clean())
            .joinToString(" ")
            .ifBlank { null }
        val resolvedName = identity.userProfile.name.clean()
            ?: fallbackName
            ?: "Airdrop Customer $account"
        return AutoPilotAppChatCustomer(
            externalId = identity.userId,
            accountNumber = account,
            name = resolvedName,
            email = identity.userProfile.email.clean() ?: user.email.clean(),
            phone = identity.userProfile.phone.clean() ?: user.phone.clean(),
        )
    }

    private fun sessionMetadata(user: AirdropUser): JsonObject =
        buildJsonObject {
            put("app_name", "Airdrop")
            put("integration", "autopilot_crm_app_channel")
            put("preferred_agent", "nirvana")
            put("agent_name", "Nirvana")
            put("airdrop_context", airdropContext(user))
            put("airdrop_context_version", "1")
            put("airdrop_context_generated_at", now())
        }

    private fun messageMetadata(user: AirdropUser): JsonObject =
        buildJsonObject {
            put("airdrop_context", airdropContext(user))
            put("airdrop_context_version", "1")
            put("airdrop_context_generated_at", now())
        }

    private fun airdropContext(user: AirdropUser): String {
        val name = listOfNotNull(user.firstName.clean(), user.lastName.clean())
            .joinToString(" ")
            .ifBlank { "Unknown" }
        return """
            You are an AI agent (Nirvana) helping a signed-in AirDrop customer. The profile below is the customer's AirDrop account state currently available to the Android app. When package, payment, order, or balance details are not present, ask for the tracking number or order ID rather than guessing.

            ## Customer
            - Name: $name
            - Account Number: ${user.accountNumber.clean() ?: "Unknown"}
            - Email: ${user.email.clean() ?: "Unknown"}
            - Phone: ${user.phone.clean() ?: "Unknown"}
            - Pickup Location: ${user.pickupLocation.clean() ?: "Unknown"}
            - Customer Tier: ${user.customerTierName.clean() ?: "Unknown"}
        """.trimIndent()
    }

    private fun AutoPilotAppChatCustomer.toJson(): JsonObject =
        buildJsonObject {
            externalId?.let { put("externalId", it) }
            put("accountNumber", accountNumber)
            put("name", name)
            email?.let { put("email", it) }
            phone?.let { put("phone", it) }
            put("platform", platform)
            put("appBundleId", appBundleId)
        }

    companion object {
        fun userFacingStatus(error: Throwable): String =
            when (error) {
                is LiveAgentChatException -> error.userMessage
                else -> "Couldn't start chat. Please try again."
            }

        internal fun parseIdentityResponse(raw: String, json: Json = ApiClient.json): AutoPilotIdentity {
            val root = json.parseToJsonElement(raw).objectOrNull()
                ?: throw parseFailure("identity")
            val obj = root.envelopedObject()
            val profile = obj.objectAt("user_profile", "userProfile") ?: JsonObject(emptyMap())
            return AutoPilotIdentity(
                endpoint = obj.flexString("endpoint") ?: throw parseFailure("identity endpoint"),
                publishableKey = obj.flexString("publishable_key", "publishableKey")
                    ?: throw parseFailure("identity key"),
                userId = obj.flexString("user_id", "userId") ?: throw parseFailure("identity user"),
                identityHash = obj.flexString("identity_hash", "identityHash")
                    ?: throw parseFailure("identity hash"),
                userProfile = AutoPilotIdentityUserProfile(
                    name = profile.flexString("name"),
                    email = profile.flexString("email"),
                    phone = profile.flexString("phone"),
                    accountNumber = profile.flexString("account_number", "accountNumber"),
                ),
            )
        }

        internal fun parseSessionResponse(raw: String, json: Json = ApiClient.json): AutoPilotAppChatSession {
            val root = json.parseToJsonElement(raw)
            val obj = when (root) {
                is JsonObject -> {
                    if (root.hasAny("conversation_id", "conversationId", "id")) {
                        root
                    } else {
                        root.objectAt("session", "data", "conversation") ?: JsonObject(emptyMap())
                    }
                }
                else -> JsonObject(emptyMap())
            }
            return AutoPilotAppChatSession(
                conversationId = obj.flexString("conversation_id", "conversationId", "id").orEmpty(),
                channelId = obj.flexString("channel_id", "channelId"),
                status = obj.flexString("status"),
                assignedAgentName = obj.flexString("assigned_agent_name", "assignedAgentName"),
                messages = parseMessagesElement(obj["messages"], json),
            )
        }

        internal fun parseMessagesResponse(raw: String, json: Json = ApiClient.json): List<AutoPilotAppChatMessage> =
            parseMessagesElement(json.parseToJsonElement(raw), json)

        internal fun parseSendResultResponse(raw: String, json: Json = ApiClient.json): AutoPilotAppChatSendResult {
            val root = json.parseToJsonElement(raw).objectOrNull() ?: JsonObject(emptyMap())
            val message = root.objectAt("message")
                ?: root.objectAt("data")?.takeIf { it.hasAny("body", "content", "text", "message") }
                ?: root.objectAt("data")?.objectAt("message")
            return AutoPilotAppChatSendResult(
                conversationId = root.flexString("conversation_id", "conversationId"),
                reply = root.flexString("reply"),
                message = message?.let(::parseMessageObject),
            )
        }

        private fun parseMessagesElement(element: JsonElement?, json: Json): List<AutoPilotAppChatMessage> =
            when (element) {
                is JsonArray -> element.mapNotNull { it.objectOrNull()?.let(::parseMessageObject) }
                is JsonObject -> {
                    val direct = element["messages"] ?: element["data"]
                    when (direct) {
                        is JsonArray -> direct.mapNotNull { it.objectOrNull()?.let(::parseMessageObject) }
                        is JsonObject -> parseMessagesElement(direct["messages"], json)
                        else -> if (element.hasAny("body", "content", "text", "message")) {
                            listOf(parseMessageObject(element))
                        } else {
                            emptyList()
                        }
                    }
                }
                else -> emptyList()
            }

        private fun parseMessageObject(obj: JsonObject): AutoPilotAppChatMessage =
            AutoPilotAppChatMessage(
                id = obj.flexString("id") ?: UUID.randomUUID().toString(),
                body = obj.flexString("body", "content", "text", "message").orEmpty(),
                direction = obj.flexString("direction") ?: "agent",
                senderName = obj.flexString("sender_name", "senderName"),
                senderType = obj.flexString("sender_type", "senderType"),
                createdAt = obj.flexString("created_at", "createdAt"),
                deliveryStatus = obj.flexString("delivery_status", "status"),
            )

        private fun serverMessage(raw: String, json: Json): String? =
            runCatching {
                json.parseToJsonElement(raw).objectOrNull()
                    ?.flexString("message", "error")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

        private fun isUserSafe(message: String): Boolean {
            val lowered = message.lowercase()
            val blocked = listOf("autopilot", "not configured", "missing env", "missing environment")
            return blocked.none { lowered.contains(it) }
        }

        private fun parseFailure(label: String): LiveAgentChatException =
            LiveAgentChatException(
                userMessage = "Chat is reconnecting. Please try again.",
                kind = LiveAgentChatErrorKind.Server,
                cause = IllegalStateException("Unable to parse AutoPilot $label"),
            )

        private fun directOkHttp(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()
    }
}

internal enum class LiveAgentChatErrorKind {
    Auth,
    Http,
    InvalidUrl,
    Server,
    Transport,
}

internal class LiveAgentChatException(
    val userMessage: String,
    val kind: LiveAgentChatErrorKind,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

private fun JsonObject.envelopedObject(): JsonObject =
    objectAt("data") ?: objectAt("identity") ?: this

private fun JsonObject.objectAt(vararg keys: String): JsonObject? =
    keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }

private fun JsonObject.hasAny(vararg keys: String): Boolean =
    keys.any { containsKey(it) }

private fun JsonObject.flexString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

private fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

private fun String?.clean(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
