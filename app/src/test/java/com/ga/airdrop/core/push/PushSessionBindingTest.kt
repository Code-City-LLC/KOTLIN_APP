package com.ga.airdrop.core.push

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.shouldConsumePendingPush
import com.ga.airdrop.core.session.SessionIdentity
import com.ga.airdrop.feature.homedetails.guardedMarkRead
import com.ga.airdrop.feature.homedetails.sessionStillCurrent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #90 proof matrix: pending push navigation and notification work are
 * bound to the active unlocked session.
 *
 * Plain-JVM per house pattern (C7/C8): stores run memory-only with init()
 * never called, so the owner matrix is exercised on the real objects. Routes
 * enter through the internal [PushDeepLink.setPending] seam — the same
 * function every capture path funnels into.
 */
class PushSessionBindingTest {

    private val route = "packageDetails/1234"

    @Before
    fun resetSession() {
        AuthTokenStore.clear()
        SessionIdentity.clear()
        PushDeepLink.clearAll()
    }

    // ── same-session replay ────────────────────────────────────────────────

    @Test
    fun `authenticated capture replays in the same session`() {
        AuthTokenStore.save("token-a")
        SessionIdentity.bind("101")
        PushDeepLink.setPending(route)

        assertEquals(route, PushDeepLink.consume())
        assertNull("one-shot: second consume is empty", PushDeepLink.consume())
    }

    // ── account-switch rejection ───────────────────────────────────────────

    @Test
    fun `route owned by account A never replays under account B`() {
        AuthTokenStore.save("token-a")
        SessionIdentity.bind("101")
        PushDeepLink.setPending(route)

        // Worst case: account switch without a full teardown sweep.
        AuthTokenStore.save("token-b")
        SessionIdentity.bind("202")

        assertNull(PushDeepLink.consume())
        assertNull("rejected route must be purged", PushDeepLink.pending.value)
    }

    @Test
    fun `account-bound route is rejected when the current identity is unknown`() {
        AuthTokenStore.save("token-a")
        SessionIdentity.bind("101")
        PushDeepLink.setPending(route)

        SessionIdentity.clear()

        assertNull(PushDeepLink.consume())
        assertNull(PushDeepLink.pending.value)
    }

    // ── logout / teardown clear ────────────────────────────────────────────

    @Test
    fun `teardown clears account-bound pending state`() {
        AuthTokenStore.save("token-a")
        SessionIdentity.bind("101")
        PushDeepLink.setPending(route)

        PushDeepLink.clearAll()

        AuthTokenStore.save("token-b")
        SessionIdentity.bind("202")
        assertNull(PushDeepLink.consume())
    }

    // ── pre-login capture binds once ───────────────────────────────────────

    @Test
    fun `logged-out capture is never released logged-out but survives until first login`() {
        AuthTokenStore.clear()
        PushDeepLink.setPending(route)

        assertNull("no release without a bearer", PushDeepLink.consume())
        assertEquals("stays pending for the login replay", route, PushDeepLink.pending.value)

        AuthTokenStore.save("token-first")
        assertEquals(route, PushDeepLink.consume())
        assertNull("bound once — nothing left for a second session", PushDeepLink.pending.value)
    }

    @Test
    fun `same-account token refresh is not an account switch`() {
        AuthTokenStore.save("token-a")
        SessionIdentity.bind("101")
        PushDeepLink.setPending(route)

        // Rotation bumps the process-local revision; the durable identity is
        // unchanged, so the route still belongs to this session.
        AuthTokenStore.save("token-a-rotated")

        assertEquals(route, PushDeepLink.consume())
    }

    // ── biometric gate ─────────────────────────────────────────────────────

    @Test
    fun `locked launch cannot consume, unlock releases at most one route`() {
        assertFalse(shouldConsumePendingPush(route, "token", navigationLocked = true))
        assertTrue(shouldConsumePendingPush(route, "token", navigationLocked = false))
        assertFalse(shouldConsumePendingPush(null, "token", navigationLocked = false))
        assertFalse(shouldConsumePendingPush(route, null, navigationLocked = false))
    }

    // ── notification work revision guards ─────────────────────────────────

    @Test
    fun `late notification work is rejected after an auth revision change`() {
        AuthTokenStore.save("token-a")
        val startRevision = AuthTokenStore.snapshot().revision
        assertTrue(sessionStillCurrent(startRevision))

        AuthTokenStore.save("token-b")
        assertFalse(sessionStillCurrent(startRevision))
    }

    @Test
    fun `guarded mark-read fires only while the session is unchanged`() = runBlocking {
        AuthTokenStore.save("token-a")
        val marked = mutableListOf<String>()

        val liveRevision = AuthTokenStore.snapshot().revision
        guardedMarkRead(liveRevision, "7") { marked.add(it) }
        assertEquals(listOf("7"), marked)

        val staleRevision = AuthTokenStore.snapshot().revision
        AuthTokenStore.save("token-b")
        guardedMarkRead(staleRevision, "8") { marked.add(it) }
        assertEquals("stale session must not reach the server", listOf("7"), marked)
    }

    // ── identity store guards ──────────────────────────────────────────────

    @Test
    fun `identity binds and clears in-memory before init`() {
        SessionIdentity.bind("101")
        assertEquals("101", SessionIdentity.current)
        SessionIdentity.bind("  ")
        assertEquals("blank bind is ignored", "101", SessionIdentity.current)
        SessionIdentity.clear()
        assertNull(SessionIdentity.current)
    }
}
