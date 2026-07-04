package com.ga.airdrop.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val AirdropJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

// Bearer auth is attached by an interceptor on the provided OkHttpClient;
// service methods never carry token parameters.
object AirdropApiFactory {

    fun create(baseUrl: String, client: OkHttpClient): AirdropApiService {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(AirdropJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AirdropApiService::class.java)
    }
}
