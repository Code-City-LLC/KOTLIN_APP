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
        Result.failure(ApiException(parsed.message, e, parsed.code))
    } catch (e: IOException) {
        Result.failure(
            ApiException("Can't reach AirDrop. Check your connection and try again.", e),
        )
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Carries a user-facing [message] (ViewModels display it directly) and the
 * Laravel machine-readable [errorCode] when present, so tier flows can branch
 * on it (e.g. NO_RATE_CARD, INSURANCE_MANDATORY) — the joint Swift/Kotlin pact.
 */
internal class ApiException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String? = null,
) : Exception(message, cause)

/** The Tier API `error_code` on a failed call, or null (non-HTTP/absent). */
fun Throwable.serverErrorCode(): String? = (this as? ApiException)?.errorCode

private data class ParsedHttpError(val message: String, val code: String?)

/**
 * Prefer the backend's own `message` (Laravel returns it on 4xx/5xx); fall back
 * to a friendly, status-appropriate line — never the raw "HTTP <code>". Also
 * lifts the machine-readable `error_code` so the caller can branch on it.
 */
private fun parseHttpError(e: HttpException): ParsedHttpError {
    var code: String? = null
    runCatching {
        val body = e.response()?.errorBody()?.string().orEmpty()
        if (body.isNotBlank()) {
            val json = JSONObject(body)
            code = json.optString("error_code").ifBlank { null }
            val msg = json.optString("message").ifBlank { json.optString("error") }
            if (msg.isNotBlank()) return ParsedHttpError(msg, code)
        }
    }
    val fallback = when (e.code()) {
        401, 403 -> "Invalid credentials"
        404 -> "We couldn't find what you were looking for."
        in 500..599 -> "Something went wrong on our end. Please try again."
        else -> "Something went wrong. Please try again."
    }
    return ParsedHttpError(fallback, code)
}

// Swift's normalizedSearch: searches shorter than 3 chars are dropped.
internal fun normalizedSearch(search: String?): String? =
    search?.trim()?.takeIf { it.length >= 3 }
