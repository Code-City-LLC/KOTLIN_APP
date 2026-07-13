package com.ga.airdrop.core.auth

import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG_AUDIT C8 regression guard.
 *
 * `save()`/`clear()` must not touch the `lateinit var prefs` before `init()`
 * has bound it. A background service or ContentProvider can run before
 * `Application.onCreate()` calls `init()`; the pre-fix code threw
 * `UninitializedPropertyAccessException` there.
 *
 * These run on a plain JVM (no Android/Robolectric on the unit-test classpath).
 * `init()` is intentionally never called, so `::prefs` stays uninitialized and
 * the guard skips SharedPreferences entirely — a completing test proves the
 * crash is gone AND that the in-memory token flow still updates.
 */
class AuthTokenStoreCrashGuardTest {

    @Test
    fun `save before init updates token without crashing`() {
        AuthTokenStore.save("tok-abc")
        assertEquals("tok-abc", AuthTokenStore.token)
    }

    @Test
    fun `clear before init nulls token without crashing`() {
        AuthTokenStore.save("tok-xyz")
        AuthTokenStore.clear()
        assertNull(AuthTokenStore.token)
        assertNull(AuthTokenStore.snapshot().sessionId)
    }

    @Test
    fun `fresh save replaces session while rotate preserves it`() {
        AuthTokenStore.save("first-token", authenticatedAccountId = 101)
        val first = AuthTokenStore.snapshot()
        assertNotNull(first.sessionId)
        assertEquals(101, first.accountId)

        val rotated = requireNotNull(AuthTokenStore.rotate(first, "rotated-token"))
        assertEquals(first.sessionId, rotated.sessionId)
        assertEquals(101, rotated.accountId)
        assertNotEquals(first.revision, rotated.revision)

        AuthTokenStore.save("fresh-login-token")
        assertNotEquals(rotated.sessionId, AuthTokenStore.snapshot().sessionId)
        AuthTokenStore.clear()
    }

    @Test
    fun `authoritative account id binds once to the same session and conflicts fail closed`() {
        AuthTokenStore.save("token-without-account")
        val sessionId = requireNotNull(AuthTokenStore.snapshot().sessionId)
        assertNull(AuthTokenStore.snapshot().accountId)

        assertTrue(AuthTokenStore.bindAccountId(sessionId, 101))
        assertEquals(101, AuthTokenStore.snapshot().accountId)
        assertEquals(101, AuthTokenStore.requestProvenance(AuthTokenStore.snapshot())?.accountId)
        assertFalse(AuthTokenStore.bindAccountId(sessionId, 202))
        assertFalse(AuthTokenStore.bindAccountId("stale-session", 101))
        assertEquals(101, AuthTokenStore.snapshot().accountId)
        AuthTokenStore.clear()
    }

    @Test
    fun `snapshot flow publishes replacement sessions even for identical bearer text`() {
        AuthTokenStore.save("same-token")
        val first = AuthTokenStore.snapshotFlow.value

        AuthTokenStore.save("same-token")
        val replacement = AuthTokenStore.snapshotFlow.value

        assertEquals(AuthTokenStore.snapshot(), replacement)
        assertNotEquals(first.sessionId, replacement.sessionId)
        assertNotEquals(first.revision, replacement.revision)
        AuthTokenStore.clear()
    }

    @Test
    fun `result application accepts rotation and rejects replacement login`() {
        AuthTokenStore.save("same-token")
        val original = AuthTokenStore.snapshot()
        val sessionId = requireNotNull(original.sessionId)
        requireNotNull(AuthTokenStore.rotate(original, "rotated-token"))

        var applied = false
        assertTrue(AuthTokenStore.applyIfCurrentSession(sessionId) { applied = true })
        assertTrue(applied)

        AuthTokenStore.save("rotated-token")
        assertFalse(AuthTokenStore.applyIfCurrentSession(sessionId) { applied = false })
        assertTrue(applied)

        val replacementSessionId = requireNotNull(AuthTokenStore.snapshot().sessionId)
        AuthTokenStore.clear()
        assertFalse(AuthTokenStore.applyIfCurrentSession(replacementSessionId) { applied = false })
        assertTrue(applied)
    }

    @Test
    fun `session cache clears synchronously on replacement and clear but not rotation`() {
        AuthTokenStore.save("account-a")
        val accountA = AuthTokenStore.snapshot()
        val accountAOwner = AuthenticatedSessionOwner(requireNotNull(accountA.sessionId))
        assertTrue(SessionStore.updateForSession(accountAOwner) { it.copy(firstName = "Account A") })

        requireNotNull(AuthTokenStore.rotate(accountA, "account-a-rotated"))
        assertEquals("Account A", SessionStore.header.value.firstName)

        AuthTokenStore.save("account-b")
        assertEquals(SessionStore.HeaderInfo(), SessionStore.header.value)
        val accountBOwner = AuthenticatedSessionOwner(requireNotNull(AuthTokenStore.snapshot().sessionId))
        assertTrue(SessionStore.updateForSession(accountBOwner) { it.copy(firstName = "Account B") })

        AuthTokenStore.clear()
        assertEquals(SessionStore.HeaderInfo(), SessionStore.header.value)
    }

    @Test
    fun `transition gate propagates action failure and rechecks exact account owner`() {
        AuthTokenStore.save("account-a", authenticatedAccountId = 101)
        val accountA = AuthTokenStore.snapshot()
        val sessionId = requireNotNull(accountA.sessionId)

        assertFalse(
            AuthTokenStore.runWhileCurrentSession(sessionId, expectedAccountId = 101) { false },
        )
        assertFalse(
            AuthTokenStore.runWhileCurrentSession(sessionId, expectedAccountId = 202) { true },
        )
        assertFalse(
            AuthTokenStore.runWhileCurrentSession(sessionId, expectedAccountId = 101) {
                AuthTokenStore.save("account-b", authenticatedAccountId = 202)
                true
            },
        )
        assertEquals(202, AuthTokenStore.snapshot().accountId)
        AuthTokenStore.clear()
    }
}
