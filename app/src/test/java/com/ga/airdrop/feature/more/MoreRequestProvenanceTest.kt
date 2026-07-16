package com.ga.airdrop.feature.more

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.AuthInterceptor
import com.ga.airdrop.core.network.StaleAuthSessionException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreRequestProvenanceTest {
    private val expected = AuthTokenStore.RequestProvenance(
        revision = 42L,
        sessionId = "session-a",
    )
    private val jsonBody = "{}".toRequestBody("application/json".toMediaType())

    @Test
    fun `profile backfill GET and PUT stamp both provenance headers`() {
        assertBound(buildMoreRequest("https://example.com/user/profile", "GET", null, expected))
        assertBound(buildMoreRequest("https://example.com/user/profile", "PUT", jsonBody, expected))
    }

    @Test
    fun `avatar POST and DELETE stamp both provenance headers`() {
        assertBound(buildMoreRequest("https://example.com/user/profile/image", "POST", jsonBody, expected))
        assertBound(buildMoreRequest("https://example.com/user/profile/image", "DELETE", null, expected))
    }

    @Test
    fun `documents identity list upload and delete stamp both provenance headers`() {
        assertBound(buildMoreRequest("https://example.com/user/profile", "GET", null, expected))
        assertBound(buildMoreRequest("https://example.com/user/documents", "GET", null, expected))
        assertBound(buildMoreRequest("https://example.com/user/documents", "POST", jsonBody, expected))
        assertBound(buildMoreRequest("https://example.com/user/documents/trn", "DELETE", null, expected))
    }

    @Test
    fun `real document repository rejects same bearer new session before wire`() = runBlocking {
        val wireCalls = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor { chain ->
                wireCalls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        try {
            AuthTokenStore.save("same-bearer", authenticatedAccountId = 101)
            val accountA = AuthTokenStore.snapshot()
            val accountAProvenance = requireNotNull(AuthTokenStore.requestProvenance(accountA))
            AuthTokenStore.save("same-bearer", authenticatedAccountId = 101)
            val accountB = AuthTokenStore.snapshot()
            assertEquals(accountA.token, accountB.token)
            assertNotEquals(accountA.sessionId, accountB.sessionId)
            assertTrue(accountB.revision > accountA.revision)

            val repository = MoreRepository(
                client = client,
                json = Json { ignoreUnknownKeys = true },
                base = "https://example.com",
            )
            val results: List<Result<*>> = listOf(
                repository.currentUserId(accountAProvenance),
                repository.userDocuments(accountAProvenance),
                repository.uploadUserDocument(
                    docType = "trn",
                    fileName = "account-a.jpg",
                    mimeType = "image/jpeg",
                    bytes = byteArrayOf(1, 0, 1),
                    expectedSession = accountAProvenance,
                ),
                repository.deleteUserDocument("trn", accountAProvenance),
            )

            assertTrue(results.all { it.exceptionOrNull() is StaleAuthSessionException })
            assertEquals(0, wireCalls.get())
        } finally {
            AuthTokenStore.clear()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun assertBound(request: okhttp3.Request) {
        assertEquals("42", request.header(AuthTokenStore.REQUEST_REVISION_HEADER))
        assertEquals("session-a", request.header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
    }
}
