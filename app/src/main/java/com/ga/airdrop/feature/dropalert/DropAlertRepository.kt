package com.ga.airdrop.feature.dropalert

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.api.UploadFile
import com.ga.airdrop.data.model.flexString
import com.ga.airdrop.data.model.objectAt
import com.ga.airdrop.data.repo.PackagesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

// The create-drop-alert multipart path delegates to the shared data repository
// so the backend wire names live in one place. Profile prefill stays here until
// the data layer exposes the exact Swift `/user/profile` response shape.

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
    private val packagesRepository: PackagesRepository = PackagesRepository(ApiClient.service),
) : DropAlertRepository {

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun createDropAlert(submission: DropAlertSubmission): DropAlertResult =
        packagesRepository.createDropAlert(
            courierNumber = submission.courierNumber,
            shipper = submission.shipper,
            shippingMethod = submission.shippingMethod.toDataModel(),
            store = submission.store,
            packageAmount = submission.packageAmount,
            consignee = submission.consignee,
            description = submission.description,
            invoiceFiles = submission.invoices.map { file ->
                UploadFile(
                    fileName = file.fileName,
                    mimeType = file.mimeType,
                    bytes = file.bytes,
                )
            },
        ).getOrElse { throw it }
            .let { response ->
                DropAlertResult(
                    success = response.success ?: true,
                    message = response.message,
                )
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

private fun DropAlertShippingMethod.toDataModel(): com.ga.airdrop.data.model.DropAlertShippingMethod =
    when (this) {
        DropAlertShippingMethod.AIRDROP_STANDARD ->
            com.ga.airdrop.data.model.DropAlertShippingMethod.AIRDROP_STANDARD
        DropAlertShippingMethod.SEADROP_STANDARD ->
            com.ga.airdrop.data.model.DropAlertShippingMethod.SEADROP_STANDARD
        DropAlertShippingMethod.EXPRESS ->
            com.ga.airdrop.data.model.DropAlertShippingMethod.EXPRESS
    }
