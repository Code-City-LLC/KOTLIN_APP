package com.ga.airdrop.core.network

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.LoginResponse
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Adds the Sanctum bearer token to every request; on 401 it attempts ONE
 * token refresh + retry before force-logging-out — mirror of Swift
 * AirdropAPI.makeRequestWithResponse:347 (refresh-then-retry) and
 * refreshToken() at :678 (single-flight, reject body-less 200).
 */
class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isNoAuth = original.header(NO_AUTH_HEADER)?.equals("true", ignoreCase = true) == true
        // Pre-auth endpoints MUST be sent unauthenticated — a leftover bearer
        // from a prior session makes the backend reject the sign-in/registration
        // request (the reported "login shows 400"). Swift's AirdropAPI omits the
        // token for these too. `auth/refresh` and `auth/logout` still need it.
        // NB: match `auth/register`, not the unrelated `device-tokens/register`.
        val isPreAuth = path.endsWith("auth/login") ||
            path.endsWith("auth/register") ||
            path.endsWith("auth/forgot-password") ||
            path.endsWith("auth/reset-password")
        // Swift `isRefreshRequest` recursion guard: a 401 from the refresh
        // endpoint itself must never trigger another refresh attempt.
        val isRefresh = path.endsWith("auth/refresh")
        val attachedToken = if (isNoAuth || isPreAuth) null else AuthTokenStore.token
        val builder = original.newBuilder()
            .removeHeader(NO_AUTH_HEADER)
            .header("Accept", "application/json")
        attachedToken?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()

        val response = chain.proceed(request)

        if (response.code != 401 || attachedToken == null || isPreAuth || isRefresh) {
            return response
        }

        val original401 = response.newBuilder()
            .body(response.peekBody(MAX_ERROR_BODY_BYTES))
            .build()
        response.close()

        // Swift :347 — try a single refresh + retry before tearing down the
        // session. TokenRefresher coalesces concurrent 401s onto one network
        // refresh; callers queued behind it see the rotated bearer.
        val refreshed = TokenRefresher.refresh(attachedToken) { performRefresh(chain) }
        if (refreshed) {
            AuthTokenStore.token?.let { rotated ->
                return chain.proceed(
                    request.newBuilder()
                        .header("Authorization", "Bearer $rotated")
                        .build(),
                )
            }
        }

        // Refresh failed: force-logout, but only if the token we sent is
        // still the current one — a stale-request 401 must not wipe a
        // freshly-refreshed token.
        if (attachedToken == AuthTokenStore.token) {
            AuthTokenStore.clear()
        }
        return original401
    }

    /**
     * POST /auth/refresh through the remainder of the chain (does not
     * re-enter this interceptor, so no recursion). Sends the CURRENT bearer
     * because Laravel Sanctum's refresh requires it (Swift refreshToken()
     * doc), and rejects a 2xx without a token exactly like Swift's
     * "body-less success" hardening. Returns the new token or null.
     */
    private fun performRefresh(chain: Interceptor.Chain): String? = runCatching {
        val bearer = AuthTokenStore.token ?: return null
        val refreshRequest = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/refresh")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $bearer")
            .build()
        chain.proceed(refreshRequest).use { refreshResponse ->
            if (!refreshResponse.isSuccessful) return null
            val body = refreshResponse.body?.string().orEmpty()
            if (body.isBlank()) return null
            AirdropJson.decodeFromString(LoginResponse.serializer(), body)
                .token
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    companion object {
        const val NO_AUTH_HEADER = "X-Airdrop-No-Auth"
        private const val MAX_ERROR_BODY_BYTES = 1024L * 1024L
    }
}
