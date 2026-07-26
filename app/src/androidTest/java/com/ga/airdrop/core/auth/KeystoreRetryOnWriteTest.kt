package com.ga.airdrop.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #182 — a bearer must never be persisted to an unencrypted store.
 *
 * `AuthTokenStore.init()` falls back to plain prefs when the encrypted store
 * will not open, so the app still launches on a device with a damaged keystore
 * — that is deliberate, and it preserves the standing rule that customers stay
 * signed in. What was NOT deliberate is that `prefs` then stayed bound to that
 * plain instance for the whole process, so every later `save()` wrote the
 * bearer in **cleartext**, silently, with no log and no behavioural difference.
 *
 * Kemar's decision (2026-07-26): retry the keystore on each save. Reads may
 * still fall back; writes may not.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreRetryOnWriteTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        AuthTokenStore.init(context)
        AuthTokenStore.clear()
    }

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

    /**
     * The normal path: encryption is available, so the bearer is persisted and
     * survives a re-init. Establishes the baseline the guard must not break —
     * a write guard that quietly stopped persisting anything would also "pass"
     * a plaintext check, and that failure would look like a logout.
     */
    @Test
    fun aBearerIsPersistedAndSurvivesReInitWhenEncryptionIsAvailable() {
        AuthTokenStore.save("encrypted-path-token", authenticatedAccountId = 101)
        assertTrue("the encrypted store should be in use", AuthTokenStore.usedEncryptedStore)

        AuthTokenStore.init(context)

        assertEquals("encrypted-path-token", AuthTokenStore.token)
        assertEquals(101, AuthTokenStore.snapshot().accountId)
        assertNotNull(AuthTokenStore.snapshot().sessionId)
    }

    /**
     * ⚠️ THE ACTUAL SECURITY ASSERTION.
     *
     * Whatever store the bearer went to, it must NOT be readable from the
     * plain `MODE_PRIVATE` prefs of the same name. Before #182, a process that
     * had fallen back wrote it there in the clear and anything with access to
     * the app's data directory could read it.
     *
     * This is deliberately written against the file rather than against the
     * flag: asserting `usedEncryptedStore` would only prove we *believe* the
     * write was encrypted. Reading the plain file proves the bearer is not
     * sitting in it.
     */
    @Test
    fun theBearerIsNeverReadableFromThePlainPreferencesFile() {
        AuthTokenStore.save("must-not-appear-in-cleartext", authenticatedAccountId = 202)

        val plain = context.getSharedPreferences("airdrop_auth", android.content.Context.MODE_PRIVATE)
        val leaked = plain.all.values.any { it?.toString()?.contains("must-not-appear-in-cleartext") == true }

        assertFalse(
            "the bearer was found in cleartext in the plain preferences file — #182",
            leaked,
        )
    }

    /** A rotation must obey the same rule as an initial save. */
    @Test
    fun aRotatedBearerIsAlsoNeverReadableInCleartext() {
        AuthTokenStore.save("first-bearer", authenticatedAccountId = 303)
        val snapshot = AuthTokenStore.snapshot()

        AuthTokenStore.rotate(snapshot, "rotated-must-not-appear-in-cleartext")

        val plain = context.getSharedPreferences("airdrop_auth", android.content.Context.MODE_PRIVATE)
        val leaked = plain.all.values.any {
            it?.toString()?.contains("rotated-must-not-appear-in-cleartext") == true
        }

        assertFalse("the rotated bearer was found in cleartext — #182", leaked)
        assertEquals("rotated-must-not-appear-in-cleartext", AuthTokenStore.token)
    }
}
