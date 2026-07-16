package com.ga.airdrop.feature.more

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.flexBool
import com.ga.airdrop.data.model.flexInt
import com.ga.airdrop.data.model.flexString
import com.ga.airdrop.data.model.objectAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/*
 * More-tab data access. Mirrors the Swift AirdropAPI call set used by the
 * More VCs (FigmaSpecificPages / FigmaProfile / FigmaDocuments /
 * FigmaPreferences / FigmaNotificationSettings):
 *
 *   GET    /user/profile          → currentUser (envelope {data:{user}} tolerated)
 *   PUT    /user/profile          → updateProfile (snake_case ProfileUpdateRequest)
 *   GET    /user/profile/image    → profileImage {url|path|image_url|...}
 *   POST   /user/profile/image    → multipart, field "image"
 *   DELETE /user/profile/image
 *   GET    /user/documents        → per-doc-type file map
 *   POST   /user/documents        → multipart, field name = doc type + document_type field
 *   DELETE /user/documents/{id}
 *   POST   /auth/logout
 *   GET    /aircoins/status
 *
 * Self-contained on OkHttp (ApiClient.okHttp carries the bearer token via
 * AuthInterceptor) so this feature does not depend on the shared
 * data/api/AirdropApiService surface while it is still being built.
 * RECONCILE: fold into data/repo/UserRepository + DocumentsRepository once
 * the shared data layer lands.
 */

/** Profile fields consumed by the More tab. Laravel returns snake_case with legacy aliases. */
data class MoreUser(
    val id: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val accountNumber: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val trnNumber: String? = null,
    val identityType: String? = null,
    val identityNumber: String? = null,
    val dob: String? = null,
    val language: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val pickupLocation: String? = null,
    val paymentCurrency: String? = null,
    val tierName: String? = null,
    val profileImageUrl: String? = null,
) {
    val fullName: String
        get() = listOfNotNull(firstName?.trim(), lastName?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** Swift `formattedAccount`: digits prefixed with AIR. */
    val formattedAccount: String?
        get() {
            val digits = accountNumber.orEmpty().filter { it.isDigit() }
            return if (digits.isEmpty()) null else "AIR$digits"
        }
}

/** The fields Laravel `UpdateUserRequest` marks `required` on PUT /user/profile. */
private val PROFILE_REQUIRED_KEYS = listOf("user_id", "email", "first_name", "last_name")

/** True if any Laravel-required profile field is absent/blank in a sparse payload. */
internal fun profileRequiredFieldsMissing(fields: Map<String, String?>): Boolean =
    PROFILE_REQUIRED_KEYS.any { fields[it].isNullOrBlank() }

/**
 * Swift `AirdropAPI.completedProfileUpdateRequest` parity: given a (possibly
 * sparse) profile payload and the cached [user], fill any missing required field
 * (user_id/email/first_name/last_name) from the cache so PUT /user/profile passes
 * Laravel validation. Present values win; a null [user] leaves the payload as-is.
 */
internal fun completeProfileFields(fields: Map<String, String?>, user: MoreUser?): Map<String, String?> {
    if (user == null) return fields
    val out = fields.toMutableMap()
    fun fill(key: String, value: String?) {
        if (out[key].isNullOrBlank()) value?.trim()?.takeIf { it.isNotEmpty() }?.let { out[key] = it }
    }
    fill("user_id", user.id?.toString())
    fill("email", user.email)
    fill("first_name", user.firstName)
    fill("last_name", user.lastName)
    return out
}

/**
 * Fail-closed back-fill decision (NavyCave #21904, Swift truth at AirdropAPI
 * 1732-1745): when the sparse payload misses required fields, the current-user
 * fetch is MANDATORY — a failed GET or a still-incomplete fetched profile means
 * ZERO PUT and a user-visible error, never a sparse request Laravel will 422 or
 * half-apply. Pure so the branches are unit-testable.
 */
internal fun resolveProfileBackfill(
    fields: Map<String, String?>,
    cached: Result<MoreUser>,
): Result<Map<String, String?>> {
    val user = cached.getOrElse {
        // Swift's async-throws completion helper propagates this fetch error.
        return Result.failure(it)
    }
    val filled = completeProfileFields(fields, user)
    return if (profileRequiredFieldsMissing(filled)) {
        Result.failure(
            IllegalStateException(
                "Your profile is missing required details (name or email). " +
                    "Update your profile and try again.",
            ),
        )
    } else {
        Result.success(filled)
    }
}

/**
 * The single GET-if-needed -> completed PUT operation used by [MoreRepository].
 * Lambdas keep request sequencing directly testable without a second repo path.
 */
internal suspend fun updateProfileWithBackfill(
    fields: Map<String, String?>,
    fetchCurrentUser: suspend () -> Result<MoreUser>,
    putProfile: suspend (Map<String, String?>) -> Result<String?>,
): Result<String?> {
    val completed = if (profileRequiredFieldsMissing(fields)) {
        resolveProfileBackfill(fields, fetchCurrentUser())
            .getOrElse { return Result.failure(it) }
    } else {
        fields
    }
    return putProfile(completed)
}

data class ProfileAsset(val url: String?, val path: String?) {
    val resolvedUrl: String?
        get() = (url ?: path)?.trim()?.takeIf { it.isNotEmpty() }
}

data class MoreDocumentFile(
    val id: Int?,
    val fileName: String?,
    val fileUrl: String?,
    val docType: String?,
    val uploadStatus: Boolean?,
) {
    val hasFile: Boolean get() = !fileUrl.isNullOrBlank() || !fileName.isNullOrBlank()
}

class MoreRepositoryException(message: String) : IOException(message)

interface MoreProfileRepository {
    suspend fun currentUser(
        expectedSession: AuthTokenStore.RequestProvenance? = null,
    ): Result<MoreUser>
    suspend fun updateProfile(
        fields: Map<String, String?>,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<String?>
    suspend fun profileImage(): Result<ProfileAsset>
    suspend fun uploadProfileImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<ProfileAsset>
    suspend fun deleteProfileImage(expectedSession: AuthTokenStore.RequestProvenance): Result<Unit>
    suspend fun fetchImage(url: String): Result<ByteArray>
}

interface MoreHubRepository : MoreProfileRepository {
    suspend fun airCoinsBalance(): Result<Int>
}

interface MoreSettingsRepository {
    suspend fun logout(): Result<Unit>
}

class MoreRepository internal constructor(
    private val client: OkHttpClient = ApiClient.okHttp,
    private val json: Json = ApiClient.json,
    private val base: String = BuildConfig.API_BASE_URL.trimEnd('/'),
) : DocumentsRepository, MoreHubRepository, MoreSettingsRepository {

    private val jsonMedia = "application/json".toMediaType()

    // ─── User profile ───

    override suspend fun currentUser(
        expectedSession: AuthTokenStore.RequestProvenance?,
    ): Result<MoreUser> = request("GET", "/user/profile", expectedSession = expectedSession) { root ->
        val user = root.objectAt("data")?.objectAt("user")
            ?: root.objectAt("data")
            ?: root.objectAt("user")
            ?: root
        parseUser(user)
    }

    /**
     * PUT /user/profile — sparse update, snake_case keys matching the Swift
     * ProfileUpdateRequest. Null values are omitted (server keeps old value).
     */
    override suspend fun updateProfile(
        fields: Map<String, String?>,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<String?> {
        // Swift AirdropAPI.completedProfileUpdateRequest (AirdropAPI.swift:1699):
        // PUT /user/profile validates user_id + email + first_name + last_name as
        // REQUIRED (Laravel UpdateUserRequest), so a sparse caller (Preferences
        // sends only user_id/email/pickup_location/payment_currency) must be
        // COMPLETED with the cached profile's required fields before the request,
        // or every Save 422s. Mirror Swift: back-fill from currentUser() only when
        // a required field is missing.
        // Fail closed (NavyCave #21904): required fields missing + failed or
        // incomplete current-user fetch => zero PUT, error surfaces to Save.
        return updateProfileWithBackfill(
            fields = fields,
            fetchCurrentUser = { currentUser(expectedSession) },
        ) { completed ->
            val body = buildJsonObject {
                completed.forEach { (key, value) -> if (value != null) put(key, value) }
            }
            request(
                "PUT",
                "/user/profile",
                body.toString().toRequestBody(jsonMedia),
                expectedSession,
            ) { root ->
                root.flexString("message")
            }
        }
    }

    // ─── Profile image ───

    override suspend fun profileImage(): Result<ProfileAsset> =
        request("GET", "/user/profile/image") { it.parseAsset() }

    override suspend fun uploadProfileImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<ProfileAsset> {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        return request("POST", "/user/profile/image", multipart, expectedSession) { it.parseAsset() }
    }

    override suspend fun deleteProfileImage(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = request("DELETE", "/user/profile/image", expectedSession = expectedSession) { }

    /** Raw image download with the bearer token attached (avatar URLs are protected). */
    override suspend fun fetchImage(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) throw MoreRepositoryException("Image download failed (${it.code}).")
                it.body?.bytes() ?: throw MoreRepositoryException("Empty image response.")
            }
        }
    }

    // ─── Documents ───

    override suspend fun currentUserId(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Int?> = currentUser(expectedSession).map(MoreUser::id)

    /** GET /user/documents → map keyed by doc-type string ("file_1583", "trn", …). */
    override suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>> =
        request("GET", "/user/documents", expectedSession = expectedSession) { root ->
            val payload = root.objectAt("data") ?: root
            buildMap {
                for ((key, value) in payload) {
                    val obj = value as? JsonObject ?: continue
                    put(
                        key,
                        MoreDocumentFile(
                            id = obj.flexInt("id"),
                            fileName = obj.flexString("file_name", "fileName"),
                            fileUrl = obj.flexString("file_url", "fileURL", "url"),
                            docType = obj.flexString("doc_type", "docType") ?: key,
                            uploadStatus = obj.flexBool("upload_status"),
                        ),
                    )
                }
            }
        }

    /** POST /user/documents multipart — field name is the doc type (Swift parity). */
    override suspend fun uploadUserDocument(
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("document_type", docType)
            .addFormDataPart(docType, fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        return request("POST", "/user/documents", multipart, expectedSession) { }
    }

    override suspend fun deleteUserDocument(
        identifier: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> =
        request("DELETE", "/user/documents/$identifier", expectedSession = expectedSession) { }

    // ─── Session ───

    override suspend fun logout(): Result<Unit> =
        request("POST", "/auth/logout", "{}".toRequestBody(jsonMedia)) { }

    /** GET /aircoins/status → available ?? balance, rendered as the header label. */
    override suspend fun airCoinsBalance(): Result<Int> = request("GET", "/aircoins/status") { root ->
        val payload = root.objectAt("data") ?: root
        payload.flexInt("available") ?: payload.flexInt("balance") ?: 0
    }

    // ─── Internals ───

    private suspend fun <T> request(
        method: String,
        path: String,
        body: RequestBody? = null,
        expectedSession: AuthTokenStore.RequestProvenance? = null,
        parse: (JsonObject) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildMoreRequest(base + path, method, body, expectedSession)
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val root = runCatching {
                    json.parseToJsonElement(text.ifBlank { "{}" }) as? JsonObject
                }.getOrNull() ?: JsonObject(emptyMap())
                if (!response.isSuccessful) {
                    throw MoreRepositoryException(
                        root.flexString("message")
                            ?: "Request failed (${response.code}). Please try again.",
                    )
                }
                parse(root)
            }
        }
    }

    private fun JsonObject.parseAsset(): ProfileAsset {
        val payload = objectAt("data") ?: this
        return ProfileAsset(
            url = payload.flexString("url", "image_url", "file_url"),
            path = payload.flexString("path", "image_path", "file_path"),
        )
    }

    private fun parseUser(obj: JsonObject): MoreUser = MoreUser(
        id = obj.flexInt("id"),
        firstName = obj.flexString("first_name", "user_first_name"),
        lastName = obj.flexString("last_name", "user_last_name"),
        accountNumber = obj.flexString("account_number", "user_account_number"),
        email = obj.flexString("email", "user_email"),
        phone = obj.flexString("phone", "user_phone"),
        mobile = obj.flexString("mobile", "user_mobile"),
        trnNumber = obj.flexString("user_trn_number", "trn_number"),
        identityType = obj.flexString("user_identity_type", "identity_type"),
        identityNumber = obj.flexString("user_identity_number", "identity_number"),
        dob = obj.flexString("user_dob", "dob"),
        language = obj.flexString("user_language", "language"),
        addressLine1 = obj.flexString("address", "user_address_line_1")
            ?: obj.objectAt("address")?.flexString("line_1", "address_line_1"),
        addressLine2 = obj.flexString("user_address_line_2")
            ?: obj.objectAt("address")?.flexString("line_2", "address_line_2"),
        city = obj.flexString("city", "user_address_city")
            ?: obj.objectAt("address")?.flexString("city"),
        state = obj.flexString("state", "user_address_state")
            ?: obj.objectAt("address")?.flexString("state"),
        country = obj.flexString("country", "user_address_country")
            ?: obj.objectAt("address")?.flexString("country"),
        pickupLocation = obj.flexString("pickup_location"),
        paymentCurrency = obj.flexString("payment_currency"),
        tierName = obj.objectAt("customer_tier")?.flexString("name")
            ?: obj.flexString("customer_tier"),
        profileImageUrl = obj.flexString("profile_image_url", "profile_image_remote"),
    )
}

internal fun buildMoreRequest(
    url: String,
    method: String,
    body: RequestBody?,
    expectedSession: AuthTokenStore.RequestProvenance?,
): Request {
    val builder = Request.Builder().url(url).method(method, body)
    expectedSession?.let { provenance ->
        builder
            .header(AuthTokenStore.REQUEST_REVISION_HEADER, provenance.revision.toString())
            .header(AuthTokenStore.REQUEST_SESSION_ID_HEADER, provenance.sessionId)
    }
    return builder.build()
}
