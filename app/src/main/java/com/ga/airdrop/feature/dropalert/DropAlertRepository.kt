package com.ga.airdrop.feature.dropalert

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.flexBool
import com.ga.airdrop.data.model.flexString
import com.ga.airdrop.data.model.objectAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// RECONCILE: move these calls into data/api/AirdropApiService (+ data/repo)
// when the shared data layer lands — POST /drop-alerts (multipart) and
// GET /user/profile. The interface below is the seam; screens/VM only see it.

/**
 * Server contract for `shipping_method` — Swift
 * `AirdropAPI.DropAlertShippingMethod` raw values, NOT the display labels.
 */
enum class DropAlertShippingMethod(val apiValue: String, val displayName: String) {
    AIRDROP_STANDARD("AIR", "Airdrop standard"),
    SEADROP_STANDARD("SeaDrop", "SeaDrop Standard"),
    EXPRESS("Express", "Express");

    companion object {
        /** Swift init(displayName:) — substring match, defaults to AIR. */
        fun fromDisplayName(displayName: String): DropAlertShippingMethod {
            val normalized = displayName.lowercase()
            return when {
                normalized.contains("sea") -> SEADROP_STANDARD
                normalized.contains("express") -> EXPRESS
                else -> AIRDROP_STANDARD
            }
        }
    }
}

/** One picked invoice file, bytes already loaded (Swift UploadFile). */
data class DropAlertInvoice(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    /** Content URI string — kept for the preview (eye) action. */
    val uri: String,
)

/** Typed submission — field-per-part, no stringly-typed dictionaries. */
data class DropAlertSubmission(
    val courierNumber: String,
    val shippingMethod: DropAlertShippingMethod,
    val shipper: String,
    val store: String,
    val packageAmount: String,
    val consignee: String,
    val description: String?,
    val invoices: List<DropAlertInvoice>,
)

data class DropAlertResult(val success: Boolean, val message: String?)

interface DropAlertRepository {
    /** Typed multipart POST /drop-alerts — Swift createDropAlert part names. */
    suspend fun createDropAlert(submission: DropAlertSubmission): DropAlertResult

    /** GET /user/profile → "First Last" for the read-only Consignee field. */
    suspend fun consigneeName(): String?
}

class RemoteDropAlertRepository(
    private val client: OkHttpClient = ApiClient.okHttp,
    private val json: Json = ApiClient.json,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : DropAlertRepository {

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun createDropAlert(submission: DropAlertSubmission): DropAlertResult =
        withContext(Dispatchers.IO) {
            // Part names verbatim from AirdropAPI.createDropAlert(...) —
            // including the backend's misspelled `package_couirer_number` and
            // `pckaage_invoice` keys. Empty fields are dropped except
            // `pckaage_invoice`, which is always sent (Swift parity).
            val fields = buildMap {
                put("package_couirer_number", submission.courierNumber)
                put("shipping_method", submission.shippingMethod.apiValue)
                put("package_shipper", submission.shipper)
                put("package_store", submission.store)
                put("package_amount", submission.packageAmount)
                put("package_consignee", submission.consignee)
                put("package_description", submission.description.orEmpty())
            }.filterValues { it.isNotBlank() }

            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            fields.forEach { (name, value) -> builder.addFormDataPart(name, value) }
            builder.addFormDataPart("pckaage_invoice", "")
            submission.invoices.forEachIndexed { index, file ->
                builder.addFormDataPart(
                    name = "preorder_invoice[$index]",
                    filename = file.fileName,
                    body = file.bytes.toRequestBody(file.mimeType.toMediaType()),
                )
            }

            val request = Request.Builder()
                .url(url("/drop-alerts"))
                .post(builder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                val message = root?.flexString("message")
                if (!response.isSuccessful) {
                    throw IOException(message ?: "Drop alert failed (${response.code}).")
                }
                DropAlertResult(
                    success = root?.flexBool("success") ?: true,
                    message = message,
                )
            }
        }

    override suspend fun consigneeName(): String? = withContext(Dispatchers.IO) {
        // Swift currentUser(): GET /user/profile, user under {data{user}}, {data} or {user}.
        val request = Request.Builder().url(url("/user/profile")).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                    ?: return@use null
                val user = root.objectAt("data")?.objectAt("user")
                    ?: root.objectAt("data")
                    ?: root.objectAt("user")
                    ?: root
                listOfNotNull(user.flexString("first_name"), user.flexString("last_name"))
                    .joinToString(" ")
                    .ifBlank { null }
            }
        }.getOrNull()
    }
}
