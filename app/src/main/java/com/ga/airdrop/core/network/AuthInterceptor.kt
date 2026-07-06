package com.ga.airdrop.core.network

import com.ga.airdrop.core.auth.AuthTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Sanctum bearer token to every request and force-logs-out on 401,
 * mirroring the RN axios interceptor and Swift AirdropAPI behavior.
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
        val attachedToken = if (isNoAuth || isPreAuth) null else AuthTokenStore.token
        val builder = original.newBuilder()
            .removeHeader(NO_AUTH_HEADER)
            .header("Accept", "application/json")
        attachedToken?.let { builder.header("Authorization", "Bearer $it") }

        val response = chain.proceed(builder.build())

        if (response.code == 401 &&
            // Only clear if the token we sent is still the current one —
            // prevents a stale-request 401 from wiping a freshly-refreshed token.
            attachedToken != null && attachedToken == AuthTokenStore.token &&
            !isPreAuth
        ) {
            AuthTokenStore.clear()
        }
        return response
    }

    companion object {
        const val NO_AUTH_HEADER = "X-Airdrop-No-Auth"
    }
}
