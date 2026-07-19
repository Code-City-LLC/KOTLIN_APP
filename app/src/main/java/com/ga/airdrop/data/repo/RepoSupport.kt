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
        // errorBody() is single-consume — read ONCE, parse message + code.
        val body = runCatching { e.response()?.errorBody()?.string().orEmpty() }
            .getOrDefault("")
        Result.failure(ApiException(friendlyHttpMessage(e, body), e, errorCodeFrom(body)))
    } catch (e: IOException) {
        Result.failure(
            ApiException("Can't reach AirDrop. Check your connection and try again.", e),
        )
    } catch (e: Throwable) {
        Result.failure(e)
    }

/** Carries a user-facing message; ViewModels display `message` directly. */
internal class ApiException(
    message: String,
    cause: Throwable? = null,
    /** Laravel `error_code` (e.g. ACCOUNT_DELETED) when the body carried one. */
    val errorCode: String? = null,
) : Exception(message, cause)

/**
 * Prefer the backend's own `message` (Laravel returns it on 4xx/5xx); fall back
 * to a friendly, status-appropriate line — never the raw "HTTP <code>".
 */
private fun friendlyHttpMessage(e: HttpException, body: String): String {
    runCatching {
        if (body.isNotBlank()) {
            val json = JSONObject(body)
            val msg = json.optString("message").ifBlank { json.optString("error") }
            if (msg.isNotBlank()) return msg
        }
    }
    return when (e.code()) {
        401, 403 -> "Invalid credentials"
        404 -> "We couldn't find what you were looking for."
        in 500..599 -> "Something went wrong on our end. Please try again."
        else -> "Something went wrong. Please try again."
    }
}

/**
 * Laravel error bodies carry a machine `error_code` beside `message`
 * (AuthController ACCOUNT_DELETED / ACCOUNT_INACTIVE, VALIDATION_ERROR, …).
 */
internal fun errorCodeFrom(body: String): String? = runCatching {
    if (body.isBlank()) return null
    JSONObject(body).optString("error_code").takeIf(String::isNotBlank)
}.getOrNull()

// Swift's normalizedSearch: searches shorter than 3 chars are dropped.
internal fun normalizedSearch(search: String?): String? =
    search?.trim()?.takeIf { it.length >= 3 }
