package com.ga.airdrop.core.push

import com.ga.airdrop.core.config.AirdropFeatureFlags
import com.ga.airdrop.core.prefs.NotificationPreferenceMatrix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate AS SHIPPED — flag off, rows hidden, `master` only.
 *
 * This class exists because a P0 shipped past a green suite. Every test in
 * [PushChannelGateTest] builds its matrix from an `allOn` fixture with all nine
 * flags set true by hand, so none of them ever constructed the matrix the app
 * actually has:
 *
 *   NotificationPreferenceMatrix()   // master=true, everything else FALSE
 *
 * That is not a hypothetical value. `NotificationAccountPreferences.load()` seeds
 * it on first read and **commits it to disk**, `readLocked` reads every sub-flag
 * with a `false` fallback, and nothing fetches the matrix from the server. It is
 * the state of every customer who has not opened Notification Settings — and,
 * because `load()` persists it, flipping the defaults later would not undo it
 * (proved separately by `SeededFalseSurvivesDefaultFlipProbe` on device).
 *
 * With the un-gated code these assertions FAILED: package notifications were
 * suppressed for the entire installed base.
 *
 * ⚠️ Deliberately NO @Before setting the flag. This class must run against the
 * default. If someone flips the shipped default to true, these fail — which is
 * the point.
 */
class PushChannelGateDefaultMatrixProbe {

    private val appDefault = NotificationPreferenceMatrix()

    @Test
    fun `the shipped default is per-category preferences OFF`() {
        assertFalse(
            "flipping this on without server columns AND a way to tell 'never " +
                "answered' from 'answered no' re-introduces the P0",
            AirdropFeatureFlags.notificationCategoryPreferences,
        )
    }

    @Test
    fun `a fresh account still receives its package notifications`() {
        listOf(
            "package_status_update",
            "shipment_ready_for_pickup",
            "package_paid_and_ready_for_delivery",
        ).forEach {
            assertFalse(
                "DEFAULT-STATE DROP: type=$it suppressed for an account that never " +
                    "touched Notification Settings (matrix=$appDefault)",
                PushChannelGate.shouldSuppress(it, appDefault),
            )
        }
    }

    @Test
    fun `a fresh account still receives promos it never opted out of`() {
        assertFalse(
            "DEFAULT-STATE DROP: promo suppressed with no customer action",
            PushChannelGate.shouldSuppress("promo_offer", appDefault),
        )
    }

    /**
     * The hidden rows must not be load-bearing. Even an explicitly all-false
     * matrix — which is what a master off/on round trip leaves behind, see
     * `NotificationSettingsViewModel.setMaster` — changes nothing while the flag
     * is off. There is no visible control that could write these back to true,
     * so honouring them would be a trap with no way out.
     */
    @Test
    fun `hidden category rows cannot suppress anything, however they got set`() {
        val everySubFlagOff = NotificationPreferenceMatrix(
            master = true,
            packageMaster = false, packageEmail = false, packageSms = false, packagePush = false,
            promosMaster = false, promosEmail = false, promosSms = false, promosPush = false,
        )
        listOf("package_status_update", "promo_offer", "payment_reminder", null).forEach {
            assertFalse(
                "type=$it must be delivered while the rows are hidden",
                PushChannelGate.shouldSuppress(it, everySubFlagOff),
            )
        }
    }

    /**
     * The one thing that DOES still suppress with the flag off — the device
     * master, which is real and server-backed via token registration. Swift does
     * exactly this at delivery (`wantsDevicePush { master }`).
     */
    @Test
    fun `the device master is still honoured, because it is the one real control`() {
        val masterOff = NotificationPreferenceMatrix(master = false)
        listOf("package_status_update", "promo_offer", "payment_reminder", null).forEach {
            assertTrue(
                "master OFF must still suppress (type=$it)",
                PushChannelGate.shouldSuppress(it, masterOff),
            )
        }
    }

    /** An unreadable matrix delivers, flag or no flag. */
    @Test
    fun `an unreadable preference set never suppresses anything`() {
        listOf("package_status_update", "promo_offer", null).forEach {
            assertFalse(
                "a null matrix must deliver (type=$it)",
                PushChannelGate.shouldSuppress(it, matrix = null),
            )
        }
    }
}
