package com.ga.airdrop.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
        AuthTokenStore.save("first-token")
        val first = AuthTokenStore.snapshot()
        assertNotNull(first.sessionId)

        val rotated = requireNotNull(AuthTokenStore.rotate(first, "rotated-token"))
        assertEquals(first.sessionId, rotated.sessionId)
        assertNotEquals(first.revision, rotated.revision)

        AuthTokenStore.save("fresh-login-token")
        assertNotEquals(rotated.sessionId, AuthTokenStore.snapshot().sessionId)
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
}
