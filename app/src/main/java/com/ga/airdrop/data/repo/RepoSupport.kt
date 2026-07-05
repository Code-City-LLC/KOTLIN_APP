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
        Result.failure(ApiException(friendlyHttpMessage(e), e))
    } catch (e: IOException) {
        Result.failure(
            ApiException("Can't reach AirDrop. Check your connection and try again.", e),
        )
    } catch (e: Throwable) {
        Result.failure(e)
    }

/** Carries a user-facing message; ViewModels display `message` directly. */
internal class ApiException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Prefer the backend's own `message` (Laravel returns it on 4xx/5xx); fall back
 * to a friendly, status-appropriate line — never the raw "HTTP <code>".
 */
private fun friendlyHttpMessage(e: HttpException): String {
    runCatching {
        val body = e.response()?.errorBody()?.string().orEmpty()
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

// Swift's normalizedSearch: searches shorter than 3 chars are dropped.
internal fun normalizedSearch(search: String?): String? =
    search?.trim()?.takeIf { it.length >= 3 }
