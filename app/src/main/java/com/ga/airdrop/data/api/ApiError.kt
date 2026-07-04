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
