package com.ga.airdrop.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    }
}
