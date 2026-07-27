package com.ga.airdrop.core.auth

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A device restore used to sign the customer out FOREVER.
 *
 * `airdrop_auth` is an EncryptedSharedPreferences file whose Tink keyset is
 * wrapped by an AndroidKeyStore key. Keystore keys are device-bound and are
 * never backed up — so Auto Backup / device-transfer copied the keyset to the
 * new phone WITHOUT the key that decrypts it. From there:
 *
 *   create() throws -> plain-prefs read fallback -> at login,
 *   ensureEncryptedForWrite() reopens the SAME dead file -> fails again ->
 *   the #182/#184 guard correctly refuses to write the bearer in cleartext ->
 *   signed out on every cold start, permanently, with no in-app remedy.
 *
 * Two changes fix it, and they cover different populations:
 *   - res/xml/backup_rules.xml + data_extraction_rules.xml stop it happening
 *     to anyone in future;
 *   - the discard-and-retry in [AuthTokenStore] repairs devices ALREADY
 *     poisoned by a restore that happened before those rules shipped.
 *
 * This test covers the second. It simulates the poisoned state the only way it
 * can be simulated on a live device: writing a file at `airdrop_auth` that is
 * NOT a valid encrypted store, which is exactly what the restored keyset looks
 * like to `EncryptedSharedPreferences.create()`.
 */
@RunWith(AndroidJUnit4::class)
class RestoredKeysetRecoveryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        AuthTokenStore.clear()
        context.deleteSharedPreferences("airdrop_auth")
    }

    /** Poison the store the way a restore does: junk where a keyset belongs. */
    private fun writeUndecryptableStore() {
        context.getSharedPreferences("airdrop_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "not-a-keyset")
            .putString("__androidx_security_crypto_encrypted_prefs_value_keyset__", "not-a-keyset")
            .commit()
    }

    /**
     * THE BUG. Before the fix, init() fell back to plain prefs and every later
     * save was refused, so the bearer never persisted and the customer was
     * signed out again on the next cold start — permanently.
     */
    @Test
    fun aRestoredUndecryptableStoreRecoversInsteadOfLockingTheCustomerOut() {
        writeUndecryptableStore()

        AuthTokenStore.init(context)

        assertTrue(
            "init() must recover an ENCRYPTED store by discarding the dead keyset. " +
                "Falling back to plain prefs is what made the sign-out permanent.",
            AuthTokenStore.usedEncryptedStore,
        )

        AuthTokenStore.save("restored-bearer", authenticatedAccountId = 4242)

        assertEquals("restored-bearer", AuthTokenStore.token)
        assertNotNull(AuthTokenStore.snapshot().sessionId)
    }

    /** The bearer must SURVIVE — that is the whole point; a re-init is a cold start. */
    @Test
    fun theBearerSurvivesTheNextColdStartAfterRecovery() {
        writeUndecryptableStore()
        AuthTokenStore.init(context)
        AuthTokenStore.save("survives-restart", authenticatedAccountId = 7)

        // Second init = the next cold start on the customer's restored phone.
        AuthTokenStore.init(context)

        assertEquals(
            "the customer was signed out on EVERY cold start before this fix",
            "survives-restart",
            AuthTokenStore.token,
        )
    }

    /**
     * ⚠️ The discard must NOT fire when there is simply nothing stored. A clean
     * install has no file; deleting on every miss would be a pointless wipe and
     * would mask a genuinely unavailable keystore, which #182 handles by
     * retrying at write time instead.
     */
    @Test
    fun aCleanInstallIsUntouchedAndStillPersists() {
        context.deleteSharedPreferences("airdrop_auth")

        AuthTokenStore.init(context)
        AuthTokenStore.save("fresh-install-bearer", authenticatedAccountId = 1)
        AuthTokenStore.init(context)

        assertEquals("fresh-install-bearer", AuthTokenStore.token)
    }

    /** Recovery must not resurrect a signed-out session. */
    @Test
    fun clearStillSignsTheCustomerOutAfterRecovery() {
        writeUndecryptableStore()
        AuthTokenStore.init(context)
        AuthTokenStore.save("to-be-cleared", authenticatedAccountId = 9)

        AuthTokenStore.clear()
        AuthTokenStore.init(context)

        assertEquals(null, AuthTokenStore.token)
        assertFalse(AuthTokenStore.snapshot().token != null)
    }
}
