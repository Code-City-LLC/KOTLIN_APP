package com.ga.airdrop.data.repo

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Wraps a suspend API call in a [Result], translating failures into
 * user-facing messages the way Swift does. Swift surfaces the API's own error
 * copy (or a clean fallback) via `errorLabel` / `showError(...)` — it never
 * shows a raw "HTTP 401". Retrofit's [HttpException.message] is exactly that
 * raw string, so we parse the API error body (`{"success":false,
 * "message":"Invalid credentials", ...}` from the Laravel backend) and expose
 * its `message` instead. Every ViewModel reads `throwable.message`, so fixing
 * it here corrects error copy app-wide.
 */
internal suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val parsed = parseHttpError(e)
        Result.failure(ApiException(parsed.message, e, parsed.fieldErrors))
    } catch (e: IOException) {
        Result.failure(
            ApiException("Can't reach AirDrop. Check your connection and try again.", e),
        )
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Carries a user-facing message; ViewModels display `message` directly.
 * [fieldErrors] is Laravel's `errors` map flattened to its first message per
 * field, so a form can put a 422 under the field it names (Kemar 2026-09-15:
 * an authorized user's mobile number was failing silently).
 */
internal class ApiException(
    message: String,
    cause: Throwable? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) : Exception(message, cause)

internal data class ParsedHttpError(val message: String, val fieldErrors: Map<String, String>)

/** The body can be read once; read it once and keep both the message and the field map. */
private fun parseHttpError(e: HttpException): ParsedHttpError {
    val body = runCatching { e.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
    val fieldErrors = linkedMapOf<String, String>()
    var message = ""
    runCatching {
        if (body.isNotBlank()) {
            val json = JSONObject(body)
            message = json.optString("message").ifBlank { json.optString("error") }
            json.optJSONObject("errors")?.let { errors ->
                for (key in errors.keys()) {
                    val value = errors.opt(key)
                    val first = when (value) {
                        is org.json.JSONArray -> value.optString(0)
                        else -> value?.toString().orEmpty()
                    }
                    if (first.isNotBlank()) fieldErrors[key] = first
                }
            }
        }
    }
    if (message.isBlank()) message = fieldErrors.values.firstOrNull().orEmpty()
    if (message.isBlank()) message = friendlyHttpMessage(e)
    return ParsedHttpError(message, fieldErrors)
}

/**
 * Prefer the backend's own `message` (Laravel returns it on 4xx/5xx); fall back
 * to a friendly, status-appropriate line — never the raw "HTTP <code>".
 */
private fun friendlyHttpMessage(e: HttpException): String {
    return when (e.code()) {
        401, 403 -> "Invalid credentials"
        404 -> "We couldn't find what you were looking for."
        in 500..599 -> "Something went wrong on our end. Please try again."
        else -> "Something went wrong. Please try again."
    }
}

// Swift's normalizedSearch: searches shorter than 3 chars are dropped.
internal fun normalizedSearch(search: String?): String? =
    search?.trim()?.takeIf { it.length >= 3 }
