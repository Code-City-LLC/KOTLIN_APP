package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiFactory
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TierRequestProvenanceTest {

    @Test
    fun tierPatchCarriesExactRequestGenerationHeaders() = runTest {
        val recorded = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.request().also(recorded::set).let { request ->
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"data":{"status":"applied","requested_tier_code":"GOLD"}}"""
                                .toResponseBody("application/json".toMediaType()),
                        )
                        .build()
                }
            }
            .build()
        val repository = TierRepository(
            AirdropApiFactory.create("https://example.test/api/v1/", client),
        )
        val expected = AuthTokenStore.RequestProvenance(
            revision = 42L,
            sessionId = "session-a",
            accountId = 1,
        )

        val result = repository.changeTier("GOLD", expected)
        val request = recorded.get()

        assertTrue(result.isSuccess)
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/customers/me/tier", request.url.encodedPath)
        assertEquals("42", request.header(AuthTokenStore.REQUEST_REVISION_HEADER))
        assertEquals("session-a", request.header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
        val requestBody = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        assertEquals("""{"requested_tier_code":"GOLD"}""", requestBody)
    }
}
