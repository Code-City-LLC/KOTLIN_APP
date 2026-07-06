package com.ga.airdrop.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature C — Quiet Hours window math (Swift QuietHoursStore.isInQuietWindow).
 * The pure [QuietHoursStore.inWindow]/[QuietHoursStore.formatMinutes] are the
 * only end-to-end-verifiable layer while FCM is inert (no google-services.json),
 * so pin the boundary/wrap semantics here.
 *
 * Contract: inclusive of start, EXCLUSIVE of end; start==end is an empty window
 * (never in); start>end wraps past midnight.
 */
class QuietHoursStoreTest {

    // Same-day window 9:00 AM (540) → 5:00 PM (1020).
    @Test
    fun `same-day window includes start, excludes end`() {
        assertTrue(QuietHoursStore.inWindow(600, 540, 1020)) // 10 AM inside
        assertTrue(QuietHoursStore.inWindow(540, 540, 1020)) // start inclusive
        assertFalse(QuietHoursStore.inWindow(1020, 540, 1020)) // end exclusive
        assertFalse(QuietHoursStore.inWindow(300, 540, 1020)) // before
        assertFalse(QuietHoursStore.inWindow(1100, 540, 1020)) // after
    }

    // Overnight-wrap window 10:00 PM (1320) → 7:00 AM (420) — the default.
    @Test
    fun `overnight wrap window spans midnight`() {
        assertTrue(QuietHoursStore.inWindow(1380, 1320, 420)) // 11 PM
        assertTrue(QuietHoursStore.inWindow(60, 1320, 420)) // 1 AM
        assertTrue(QuietHoursStore.inWindow(1320, 1320, 420)) // start inclusive
        assertFalse(QuietHoursStore.inWindow(420, 1320, 420)) // end exclusive
        assertFalse(QuietHoursStore.inWindow(720, 1320, 420)) // noon, outside
    }

    @Test
    fun `empty window is never in`() {
        assertFalse(QuietHoursStore.inWindow(500, 500, 500))
        assertFalse(QuietHoursStore.inWindow(0, 0, 0))
    }

    @Test
    fun `defaults are 10 PM to 7 AM`() {
        assertEquals(22 * 60, QuietHoursStore.DEFAULT_START_MINUTES)
        assertEquals(7 * 60, QuietHoursStore.DEFAULT_END_MINUTES)
    }

    @Test
    fun `formatMinutes renders 12-hour AM PM`() {
        assertEquals("10:00 PM", QuietHoursStore.formatMinutes(1320))
        assertEquals("7:00 AM", QuietHoursStore.formatMinutes(420))
        assertEquals("12:00 AM", QuietHoursStore.formatMinutes(0)) // midnight
        assertEquals("12:00 PM", QuietHoursStore.formatMinutes(720)) // noon
        assertEquals("9:05 AM", QuietHoursStore.formatMinutes(545))
    }
}
