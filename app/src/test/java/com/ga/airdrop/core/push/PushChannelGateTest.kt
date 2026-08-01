package com.ga.airdrop.core.push

import com.ga.airdrop.core.config.AirdropFeatureFlags
import com.ga.airdrop.core.prefs.NotificationPreferenceMatrix
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * These switches were theatre for months, so the tests care about two things in
 * this order:
 *
 *  1. **A switch that is ON must never lose a notification.** Dropping a package
 *     update is worse than any promo getting through, so every uncertain path is
 *     pinned to "deliver".
 *  2. A switch that is OFF must actually suppress.
 */
class PushChannelGateTest {

    private val allOn = NotificationPreferenceMatrix(
        master = true,
        packageMaster = true, packageEmail = true, packageSms = true, packagePush = true,
        promosMaster = true, promosEmail = true, promosSms = true, promosPush = true,
    )

    /**
     * ⚠️ These tests describe what the gate does when per-category preferences are
     * TURNED ON. That is not the shipped configuration — the flag is off, the rows
     * are hidden, and the gate reads `master` only.
     *
     * They are still worth keeping: they pin the category logic for the day the
     * flag flips. But they must opt in explicitly, or they would be asserting
     * behaviour no customer can reach — and, worse, would have gone on passing
     * while the shipped path did something entirely different. The flag-off
     * behaviour is covered separately below and in
     * [PushChannelGateDefaultMatrixProbe].
     */
    @Before
    fun enableCategoryPreferences() {
        AirdropFeatureFlags.notificationCategoryPreferences = true
    }

    @After
    fun restoreShippedDefault() {
        AirdropFeatureFlags.notificationCategoryPreferences = false
    }

    // ── Classification ──────────────────────────────────────────────────────

    @Test
    fun `the many spellings the backend actually sends all classify as package`() {
        // Every one of these is a real type from NOTIFICATION_TYPE_ROUTES. An
        // exact-match list would have missed most of them.
        listOf(
            "package_status_update", "package_received", "shipment_received",
            "shipment_update", "shipment_status", "shipment_status_update",
            "package_ready_for_pickup", "shipment_ready_for_pickup",
            "ready_for_pickup", "paid_ready_for_pickup",
            "paid_and_ready_for_pickup", "package_paid_and_ready_for_pickup",
            "package_paid_and_ready_for_delivery",
        ).forEach {
            assertEquals("$it must classify as PACKAGE", PushChannelGate.Category.PACKAGE, PushChannelGate.categorize(it))
        }
    }

    @Test
    fun `promotional spellings classify as promo`() {
        listOf("promotion", "promo_offer", "special_offer", "flash_sale", "discount_alert", "marketing_blast")
            .forEach {
                assertEquals("$it must classify as PROMO", PushChannelGate.Category.PROMO, PushChannelGate.categorize(it))
            }
    }

    @Test
    fun `casing and hyphens do not defeat classification`() {
        assertEquals(PushChannelGate.Category.PACKAGE, PushChannelGate.categorize("Package-Status-Update"))
        assertEquals(PushChannelGate.Category.PROMO, PushChannelGate.categorize("  PROMO_OFFER  "))
    }

    @Test
    fun `anything unrecognised is OTHER and is never suppressed`() {
        listOf(null, "", "   ", "payment_reminder", "app_update_available", "wat")
            .forEach {
                assertEquals(PushChannelGate.Category.OTHER, PushChannelGate.categorize(it))
                assertFalse(
                    "an unclassified push must ALWAYS be delivered (type=$it)",
                    PushChannelGate.shouldSuppress(it, allOn),
                )
            }
    }

    // ── Suppression ─────────────────────────────────────────────────────────

    @Test
    fun `everything on delivers everything`() {
        assertFalse(PushChannelGate.shouldSuppress("package_status_update", allOn))
        assertFalse(PushChannelGate.shouldSuppress("promo_offer", allOn))
    }

    @Test
    fun `turning Promotions Push OFF suppresses promos and NOTHING else`() {
        val m = allOn.copy(promosPush = false)
        assertTrue("the promo must be dropped", PushChannelGate.shouldSuppress("promo_offer", m))
        // The bug this whole gate exists to prevent: silencing the wrong thing.
        assertFalse("a package update must STILL arrive", PushChannelGate.shouldSuppress("package_status_update", m))
        assertFalse("an unrelated push must STILL arrive", PushChannelGate.shouldSuppress("payment_reminder", m))
    }

    @Test
    fun `turning Package Push OFF suppresses package updates and NOTHING else`() {
        val m = allOn.copy(packagePush = false)
        assertTrue(PushChannelGate.shouldSuppress("shipment_ready_for_pickup", m))
        assertFalse("a promo must still arrive", PushChannelGate.shouldSuppress("promo_offer", m))
    }

    @Test
    fun `the SECTION switch gates the whole section, not just its Push row`() {
        val m = allOn.copy(promosMaster = false)
        assertTrue(PushChannelGate.shouldSuppress("promo_offer", m))
        assertFalse(PushChannelGate.shouldSuppress("package_status_update", m))
    }

    @Test
    fun `master OFF suppresses everything`() {
        val m = allOn.copy(master = false)
        listOf("package_status_update", "promo_offer", "payment_reminder", null)
            .forEach { assertTrue("type=$it must be suppressed", PushChannelGate.shouldSuppress(it, m)) }
    }

    // ── The safety property that matters most ───────────────────────────────

    @Test
    fun `an UNREADABLE preference set never suppresses anything`() {
        // No session, prefs not initialised, account read failed. Absence of a
        // preference is NOT permission to withhold someone's notification —
        // the same rule as the nirvana packagesKnown fix.
        listOf("package_status_update", "promo_offer", "payment_reminder", null)
            .forEach {
                assertFalse(
                    "a null matrix must deliver (type=$it)",
                    PushChannelGate.shouldSuppress(it, matrix = null),
                )
            }
    }

    @Test
    fun `Email and SMS switches do NOT affect push delivery`() {
        // They are server-owned channels. If a future edit accidentally wires
        // them into this gate, a customer who turned off promo EMAIL would stop
        // getting promo PUSH — a silent, wrong side effect.
        val m = allOn.copy(packageEmail = false, packageSms = false, promosEmail = false, promosSms = false)
        assertFalse(PushChannelGate.shouldSuppress("package_status_update", m))
        assertFalse(PushChannelGate.shouldSuppress("promo_offer", m))
    }
}
