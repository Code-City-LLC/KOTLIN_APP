package com.ga.airdrop.data.api

import java.io.IOException
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
// {"errors": {"field": ["msg", ...]}}.
@Serializable
data class ApiErrorEnvelope(
    val message: String? = null,
    val error: String? = null,
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

    fun errorBodyMessage(body: String?): String? = envelope(body)?.displayMessage

    fun envelope(body: String?): ApiErrorEnvelope? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            AirdropJson.decodeFromString(ApiErrorEnvelope.serializer(), body)
        }.getOrNull()
    }

    /** `{"errors": {"field": ["first", ...]}}` -> {field: first}, in the server's order. */
    fun fieldErrorsOf(envelope: ApiErrorEnvelope?): Map<String, String> {
        val errors = envelope?.errors as? JsonObject ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        for ((key, value) in errors) {
            val first = when (value) {
                is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> value.contentOrNull
                else -> null
            }
            if (!first.isNullOrBlank()) out[key] = first
        }
        return out
    }

    private fun httpMessage(exception: HttpException): String {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        return errorBodyMessage(body)
            ?: "HTTP ${exception.code()}: ${body?.take(160).orEmpty().ifEmpty { exception.message() }}"
    }
}

fun Throwable.toUserMessage(): String = ApiErrors.userMessage(this)

/** A failure read ONCE: the server's message and its `errors` map flattened to the first line per field. */
data class ParsedApiError(val message: String?, val fieldErrors: Map<String, String>)

/**
 * Field-level errors survive whichever wrapper the call went through.
 * `More2Repository.apiCall` hands the ViewModel the raw [HttpException];
 * `RepoSupport.apiResult` hands it an [ApiException] that already kept the map.
 * The error body can only be read once, so message and map are taken together
 * (Kemar 2026-09-15: an authorized user's mobile number was failing silently).
 */
fun Throwable.parseApiError(): ParsedApiError = when (this) {
    is com.ga.airdrop.data.repo.ApiException -> ParsedApiError(message, fieldErrors)
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val envelope = ApiErrors.envelope(body)
        ParsedApiError(envelope?.displayMessage, ApiErrors.fieldErrorsOf(envelope))
    }
    else -> ParsedApiError(toUserMessage(), emptyMap())
}
