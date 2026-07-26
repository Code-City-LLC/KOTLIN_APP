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
 *
 *   success + token  → bearer rotated
 *   HTTP 401         → session cleared; the server has confirmed it is dead
 *   everything else  → session UNTOUCHED (network error, 5xx, body-less 200)
 *
 * Kemar 2026-07-26, adopting SwiftHawk's rule for both platforms. The line is
 * *confirmed dead* vs *lost signal*: a 401 is the server stating the principal
 * is gone; a timeout is the server saying nothing at all. The app never signs a
 * customer out for losing signal.
 *
 * These must agree with AuthInterceptorRefreshTest exactly — the interceptor
 * and this cold-start path are two ways into the same decision, and if they
 * disagree the customer's fate depends on which one ran first.
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
     * ⚠️ THIS ASSERTION HAS BEEN FLIPPED TWICE IN ONE DAY — read the rule, not
     * the history, before changing it again.
     *
     * It began as `401 clears the dead session`, became `401 keeps the customer
     * signed in` under Kemar's first ruling, and is now back to clearing under
     * his reversal to SwiftHawk's rule. The reversal was not a change of mind
     * about logging people out; it was a narrowing. Before, ANY refresh failure
     * cleared the session, because performRefresh could only say null. Now only
     * an explicit 401 does, and the failure modes that actually hurt customers
     * — dropped connections on mobile data — keep them signed in.
     *
     * Safe as of 2026-07-26 because BronzeMountain shipped response-loss-safe
     * rotation (7b99b26d): verified live on pre-staging that a refresh retried
     * with an already-rotated bearer answers 200, and that the previous bearer
     * still authenticates afterwards. Without that overlap this assertion would
     * be a logout bug, not a fix.
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
