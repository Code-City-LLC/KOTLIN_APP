package com.ga.airdrop.core.network

import com.ga.airdrop.core.auth.AuthTokenStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Foreground session refresh — Swift SceneDelegate.refreshStoredSessionIfNeeded
 * parity (SceneDelegate:429/:436). MainActivity.onStart feeds the HTTP outcome
 * into [TokenRefresher.applyForegroundRefresh]; these pin the three arms:
 * 401 → session cleared (AppRoot reactive logout takes over, C6),
 * success+token → bearer rotated,
 * network error / body-less → session untouched (Swift logs and skips).
 */
class ForegroundRefreshTest {

    @Before
    fun setUp() {
        AuthTokenStore.save("stored-token")
    }

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

    @Test
    fun `401 clears the dead session`() {
        TokenRefresher.applyForegroundRefresh(httpCode = 401, newToken = null)
        assertNull(AuthTokenStore.token)
    }

    @Test
    fun `success rotates the bearer`() {
        TokenRefresher.applyForegroundRefresh(httpCode = null, newToken = "rotated")
        assertEquals("rotated", AuthTokenStore.token)
    }

    @Test
    fun `network error keeps the session untouched`() {
        TokenRefresher.applyForegroundRefresh(httpCode = null, newToken = null)
        assertEquals("stored-token", AuthTokenStore.token)
    }

    @Test
    fun `server error other than 401 keeps the session untouched`() {
        TokenRefresher.applyForegroundRefresh(httpCode = 503, newToken = null)
        assertEquals("stored-token", AuthTokenStore.token)
    }

    @Test
    fun `body-less success does not rotate`() {
        TokenRefresher.applyForegroundRefresh(httpCode = null, newToken = "")
        assertEquals("stored-token", AuthTokenStore.token)
    }
}
