package com.ga.airdrop.core.prefs

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PROBE (BlueDeer) — answers @WildBeaver's question in ORC #87765:
 *
 *   "Does A actually restore accounts whose matrix was already seeded-and-committed
 *    false? Flipping the data-class / readLocked fallbacks fixes *unset* rows, but a
 *    row already committed false reads back false regardless of the new default."
 *
 * The answer is NO, and this proves the mechanism rather than arguing it.
 *
 * `load()` does not merely READ. On an account with no scoped state it seeds
 * `NotificationPreferenceMatrix()` — all sub-flags false — and **commits it to
 * disk** (`hasScopedState` → `legacySeed` → `commitLocked`). After that the keys
 * EXIST with the value `false`, so `getBoolean(key, <default>)` returns the stored
 * `false` and the default is never consulted again.
 *
 * Consequence: option A (flip the defaults to true) would fix only installs that
 * have never called `load()`. Every account that has opened any screen touching
 * these prefs already has `false` on disk, and would stay suppressed.
 */
@RunWith(AndroidJUnit4::class)
class SeededFalseSurvivesDefaultFlipProbe {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val accountId = 987654

    private fun store() =
        context.getSharedPreferences(NotificationAccountPreferences.PREFS, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        NotificationAccountPreferences.init(context)
        store().edit().clear().commit()
    }

    @After
    fun tearDown() {
        store().edit().clear().commit()
    }

    @Test
    fun loadWritesFalseToDiskSoAnyNewDefaultIsIgnored() {
        val smsKey = NotificationAccountPreferences.accountKey(accountId, "packageSMS")
        val masterKey = NotificationAccountPreferences.accountKey(accountId, "packageMaster")

        assertFalse("precondition: nothing on disk yet", store().contains(smsKey))

        // A plain READ of the matrix.
        val matrix = NotificationAccountPreferences.load(accountId)

        assertEquals(
            "the seeded matrix is the all-false default",
            NotificationPreferenceMatrix(),
            matrix,
        )

        // The read PERSISTED. This is the whole point.
        assertTrue(
            "load() committed packageSMS to disk — it is no longer an absent key",
            store().contains(smsKey),
        )
        assertTrue(
            "load() committed packageMaster to disk too",
            store().contains(masterKey),
        )

        // And now the stored false wins over ANY default a future patch supplies.
        assertFalse(
            "OPTION A DOES NOT HELP HERE: the key exists, so a `true` fallback is " +
                "never consulted and the row still reads false",
            store().getBoolean(smsKey, true),
        )
        assertFalse(
            "same for the section master",
            store().getBoolean(masterKey, true),
        )
    }
}
