package com.ga.airdrop.core.network

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.data.api.AirdropApiFactory
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single API client, Android counterpart of `AirdropAPI.shared`.
 * Timeouts match the Swift session config (30s request / 60s resource).
 */
object ApiClient {

    /** Shared lenient Json (same instance the Retrofit converter uses). */
    val json: kotlinx.serialization.json.Json get() = com.ga.airdrop.data.api.AirdropJson

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // Overall cap across DNS+connect+redirects+body so a trickling or
            // half-dead server can't hang a call forever (BUG_AUDIT H8 — only
            // the per-phase timeouts were set). Comfortably above read+write.
            .callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    /** Shared Retrofit for feature-scoped interfaces (same base URL/converter). */
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(AirdropJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val service: AirdropApiService by lazy {
        AirdropApiFactory.create(BuildConfig.API_BASE_URL, okHttp)
    }
}
