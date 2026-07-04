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
        val builder = original.newBuilder()
            .header("Accept", "application/json")
        AuthTokenStore.token?.let { builder.header("Authorization", "Bearer $it") }

        val response = chain.proceed(builder.build())

        if (response.code == 401 && AuthTokenStore.token != null &&
            !original.url.encodedPath.contains("/auth/login")
        ) {
            AuthTokenStore.clear()
        }
        return response
    }
}
