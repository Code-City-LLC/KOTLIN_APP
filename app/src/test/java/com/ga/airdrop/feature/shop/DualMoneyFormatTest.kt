package com.ga.airdrop.feature.shop

import com.ga.airdrop.feature.shipments.ShipmentsFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kemar, 2026-07-26, twice:
 *
 *   "We should show the JMD and the US dollar price for each item, not just the
 *    JMD... once a monetary value is there, we need to see both values. Our
 *    primary currency is US dollar. So if we're not showing US dollar, it is a
 *    problem. rectify this all across the app."
 *   "We need to have JMD slash USD. We need to show both of them."
 *
 * The regression he hit was `AuctionCheckoutViewModel.totalLabel()`, which
 * branched on the chosen currency and showed **one** of them — a customer paying
 * in JMD saw "JA$64,841.58" and no USD figure anywhere on the Buy-Now screen.
 *
 * These tests pin the format itself. The rule they encode: **a customer-visible
 * money string must contain BOTH codes.**
 */
class DualMoneyFormatTest {

    private val rate = 162.0

    @Test
    fun `both currencies appear, JMD first, slash separated`() {
        assertEquals("JMD 16,200.00 / USD 100.00", formatDualMoney(100.0, rate))
    }

    /** The shipments-side helper must agree with the shop-side one. */
    @Test
    fun `the two helpers produce the identical string`() {
        assertEquals(formatDualMoney(403.35, rate), ShipmentsFormat.dual(403.35, rate))
    }

    /**
     * The heart of the ruling: whatever else changes, neither code may vanish.
     * Written as a property over a spread of amounts so a future "simplification"
     * back to one currency cannot slip through on a lucky example.
     */
    @Test
    fun `no amount ever renders with a single currency`() {
        listOf(0.0, 0.01, 1.0, 99.99, 1_000.0, 64_841.58, 1_234_567.89).forEach { amount ->
            val s = formatDualMoney(amount, rate)
            assertTrue("USD missing from \"$s\"", s.contains("USD"))
            assertTrue("JMD missing from \"$s\"", s.contains("JMD"))
            assertTrue("separator missing from \"$s\"", s.contains(" / "))
        }
    }

    /** Grouping separators must survive — "JMD 1,234,567.89", not "1234567.89". */
    @Test
    fun `large amounts stay grouped and readable`() {
        val s = formatDualMoney(1_234_567.89, 1.0)
        assertTrue("expected grouped thousands in \"$s\"", s.contains("1,234,567.89"))
    }

    /** Zero is still money and must still show both codes, not "-" or "". */
    @Test
    fun `zero renders as money in both currencies`() {
        assertEquals("JMD 0.00 / USD 0.00", formatDualMoney(0.0, rate))
    }

    /**
     * ⚠️ The input is USD; JMD is derived. Passing an already-converted JMD
     * figure would multiply it twice — the exact defect an audit agent wrongly
     * claimed was live, so this pins the direction explicitly.
     */
    @Test
    fun `the JMD side is the USD amount multiplied by the rate`() {
        val s = formatDualMoney(10.0, 160.625)
        assertTrue("expected 10 * 160.625 = 1,606.25 in \"$s\"", s.contains("1,606.25"))
        assertTrue("expected the USD side untouched in \"$s\"", s.contains("USD 10.00"))
    }

    /** A null amount is "-" on the shipments helper, not "JMD 0.00 / USD 0.00". */
    @Test
    fun `a missing amount is a dash, not a fabricated zero`() {
        assertEquals("-", ShipmentsFormat.dual(null, rate))
    }
}
