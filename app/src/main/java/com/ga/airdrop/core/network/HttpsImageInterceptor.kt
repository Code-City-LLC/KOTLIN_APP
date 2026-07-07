package com.ga.airdrop.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Upgrades cleartext `http://` AirDrop image URLs to `https://` before the
 * request is routed. Registered as an *application* interceptor on Coil's
 * OkHttpClient so the rewrite happens ahead of connection setup — OkHttp never
 * attempts a cleartext socket, so Android's default cleartext block is never
 * hit. See [secureImageUrl] for why the prod build needs this.
 */
class HttpsImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val original = request.url.toString()
        val secured = secureImageUrl(original)
        val outgoing = if (secured != null && secured != original) {
            request.newBuilder().url(secured).build()
        } else {
            request
        }
        return chain.proceed(outgoing)
    }
}
