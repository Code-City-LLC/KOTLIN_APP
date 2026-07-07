package com.ga.airdrop.data.api

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

// Kotlin mirror of Swift's APIErrorEnvelope: failures arrive as
// {"message": "..."} or {"error": "..."}; Laravel validation failures add
// {"errors": {"field": ["msg", ...]}}. The Tier API also returns a
// machine-readable {"error_code": "NO_RATE_CARD"} the UI branches on.
@Serializable
data class ApiErrorEnvelope(
    val message: String? = null,
    val error: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    val errors: JsonElement? = null,
) {
    val displayMessage: String?
        get() = message?.takeIf { it.isNotBlank() }
            ?: error?.takeIf { it.isNotBlank() }
            ?: firstValidationError

    private val firstValidationError: String?
        get() = (errors as? JsonObject)?.values?.firstNotNullOfOrNull { value ->
            when (value) {
                is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> value.contentOrNull
                else -> null
            }
        }
}

object ApiErrors {

    fun userMessage(throwable: Throwable): String = when (throwable) {
        is HttpException -> httpMessage(throwable)
        is IOException -> "Network error. Please check your connection and try again."
        is SerializationException -> "Unexpected response from the server."
        else -> throwable.message ?: "Something went wrong. Please try again."
    }

    fun errorBodyMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            AirdropJson.decodeFromString(ApiErrorEnvelope.serializer(), body)
        }.getOrNull()?.displayMessage
    }

    private fun httpMessage(exception: HttpException): String {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        return errorBodyMessage(body)
            ?: "HTTP ${exception.code()}: ${body?.take(160).orEmpty().ifEmpty { exception.message() }}"
    }
}

fun Throwable.toUserMessage(): String = ApiErrors.userMessage(this)

/**
 * Machine-readable Tier API error codes (docs/TIER_SYSTEM_API.md) the app
 * branches on — the joint Swift/Kotlin error_code pact. Swift models the same
 * set in APIError.coded(code:message:); these constants + [friendlyCopy] keep
 * the two apps' tier-flow branchings identical.
 */
object ApiErrorCodes {
    const val NOT_FOUND = "NOT_FOUND"
    const val FORBIDDEN = "FORBIDDEN"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"

    /** Non-SAVR customer tried to decline mandatory insurance — snap back to selected. */
    const val INSURANCE_MANDATORY = "INSURANCE_MANDATORY"

    /** Neither/both of selected+declined were sent — force an explicit choice. */
    const val INSURANCE_CHOICE_REQUIRED = "INSURANCE_CHOICE_REQUIRED"

    /** No rate card for the method/destination — show route-unavailable, never a $0 quote. */
    const val NO_RATE_CARD = "NO_RATE_CARD"

    /**
     * Friendly copy for the coded tier errors, or null to fall back to the
     * server's own `message`. Only the codes with a distinct user action get
     * bespoke copy; the rest read fine from the backend message.
     */
    fun friendlyCopy(code: String?): String? = when (code) {
        NO_RATE_CARD ->
            "That shipping option isn't available for this destination right now."
        INSURANCE_MANDATORY ->
            "Insurance is required for your tier and can't be declined."
        INSURANCE_CHOICE_REQUIRED ->
            "Please add or decline insurance to continue."
        else -> null
    }
}
