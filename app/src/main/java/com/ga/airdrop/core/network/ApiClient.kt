package com.ga.airdrop.core.network

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.data.api.AirdropApiFactory
import com.ga.airdrop.data.api.AirdropApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Single API client, Android counterpart of `AirdropAPI.shared`.
 * Timeouts match the Swift session config (30s request / 60s resource).
 */
object ApiClient {

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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

    val service: AirdropApiService by lazy {
        AirdropApiFactory.create(BuildConfig.API_BASE_URL, okHttp)
    }
}
