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
 * success+token → bearer rotated,
 * everything else — network error, body-less response, or a 401 → session
 * UNTOUCHED. Kemar 2026-07-26: the app never signs the customer out; only the
 * customer does.
 */
class ForegroundRefreshTest {

    private lateinit var storedSession: AuthTokenStore.Snapshot

    @Before
    fun setUp() {
        AuthTokenStore.save("stored-token")
        storedSession = AuthTokenStore.snapshot()
    }

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

    /**
     * ⚠️ THIS ASSERTED THE LOGOUT. It was `401 clears the dead session`.
     *
     * A foreground refresh answering 401 no longer costs the customer their
     * session. If the token really is finished every screen will say so, and
     * the customer can log out themselves — the app will not do it for them.
     */
    @Test
    fun `a 401 on foreground refresh confirms the session is dead`() {
        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = 401, newToken = null)
        assertNull(AuthTokenStore.token)
    }

    @Test
    fun `success rotates the bearer`() {
        val sessionId = storedSession.sessionId
        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = null, newToken = "rotated")
        assertEquals("rotated", AuthTokenStore.token)
        assertEquals(sessionId, AuthTokenStore.snapshot().sessionId)
    }

    @Test
    fun `network error keeps the session untouched`() {
        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = null, newToken = null)
        assertEquals("stored-token", AuthTokenStore.token)
    }

    @Test
    fun `server error other than 401 keeps the session untouched`() {
        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = 503, newToken = null)
        assertEquals("stored-token", AuthTokenStore.token)
    }

    @Test
    fun `body-less success does not rotate`() {
        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = null, newToken = "")
        assertEquals("stored-token", AuthTokenStore.token)
    }

    /** A newer login is never disturbed by an older refresh result. */
    @Test
    fun `a stale 401 cannot disturb a newer bearer`() {
        AuthTokenStore.save("newer-token")

        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = 401, newToken = null)

        assertEquals("newer-token", AuthTokenStore.token)
    }

    @Test
    fun `replacement installed at apply boundary survives stale 401`() {
        TokenRefresher.applyForegroundRefresh(
            storedSession,
            httpCode = 401,
            newToken = null,
            beforeApply = { AuthTokenStore.save("replacement-login-token") },
        )

        assertEquals("replacement-login-token", AuthTokenStore.token)
    }

    @Test
    fun `stale success cannot overwrite a newer bearer`() {
        AuthTokenStore.save("newer-token")

        TokenRefresher.applyForegroundRefresh(
            storedSession,
            httpCode = null,
            newToken = "stale-rotation",
        )

        assertEquals("newer-token", AuthTokenStore.token)
    }

    @Test
    fun `re-saving the same bearer still invalidates an older refresh generation`() {
        AuthTokenStore.save("stored-token")

        TokenRefresher.applyForegroundRefresh(storedSession, httpCode = 401, newToken = null)

        assertEquals("stored-token", AuthTokenStore.token)
    }
}
