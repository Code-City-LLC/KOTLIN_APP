package com.ga.airdrop.feature.more

import com.ga.airdrop.core.auth.AuthTokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
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

    private fun assertBound(request: okhttp3.Request) {
        assertEquals("42", request.header(AuthTokenStore.REQUEST_REVISION_HEADER))
        assertEquals("session-a", request.header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
    }
}
