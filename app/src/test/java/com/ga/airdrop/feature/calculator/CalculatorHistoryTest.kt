package com.ga.airdrop.feature.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CalculatorHistory.capped] ring-buffer parity with Swift FigmaCalculatorHistory
 * (§B.6): newest-first, capped at [CalculatorHistory.MAX], oldest fall off.
 */
class CalculatorHistoryTest {

    private fun entry(id: Int) =
        CalculatorHistory.Entry(method = "STANDARD", weightLbs = id.toDouble(), invoiceUsd = 100.0)

    @Test
    fun `keeps every entry below the cap`() {
        val list = (1..3).map { entry(it) }
        assertEquals(list, CalculatorHistory.capped(list))
    }

    @Test
    fun `caps at MAX and drops the oldest tail`() {
        // Newest-first list of 7 -> only the first MAX survive.
        val newestFirst = (1..7).map { entry(it) }
        val capped = CalculatorHistory.capped(newestFirst)
        assertEquals(CalculatorHistory.MAX, capped.size)
        assertEquals(newestFirst.take(CalculatorHistory.MAX), capped)
        // The two oldest (entries 6 & 7) fell off.
        assertEquals(1.0, capped.first().weightLbs, 0.0)
        assertEquals(5.0, capped.last().weightLbs, 0.0)
    }

    @Test
    fun `record semantics — newest goes to the front, capped`() {
        // Mirror record()'s (listOf(new) + existing).take(MAX).
        val existing = (1..5).map { entry(it) }
        val next = CalculatorHistory.capped(listOf(entry(99)) + existing)
        assertEquals(CalculatorHistory.MAX, next.size)
        assertEquals(99.0, next.first().weightLbs, 0.0)
        assertEquals(4.0, next.last().weightLbs, 0.0)
    }
}
