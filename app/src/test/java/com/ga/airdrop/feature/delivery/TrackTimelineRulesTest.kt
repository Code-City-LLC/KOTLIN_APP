package com.ga.airdrop.feature.delivery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kemar's timeline rule 3 (#79650): *"Don't generate the last delivery. Just
 * say Out for Delivery. The next one is not gonna come until... we're only
 * gonna have one blurred out."*
 *
 * Everything that HAPPENED plus exactly ONE upcoming step — never a queue of
 * greyed-out future stages.
 */
class TrackTimelineRulesTest {

    private data class Row(val key: String, val state: String)

    private fun truncate(rows: List<Row>) =
        truncateAfterFirstPending(rows) { it.state == "pending" }

    /** assigned -> rail ends at Out for Delivery. Delivered must not appear. */
    @Test
    fun `at assigned the rail stops after the single next step`() {
        val rows = listOf(
            Row("preparing_dispatch", "current"),
            Row("out_for_delivery", "pending"),
            Row("delivered", "pending"),
        )
        assertEquals(
            listOf("preparing_dispatch", "out_for_delivery"),
            truncate(rows).map { it.key },
        )
    }

    /** out_for_delivery -> Delivered is the one pending row shown. */
    @Test
    fun `at out for delivery the single pending row is Delivered`() {
        val rows = listOf(
            Row("preparing_dispatch", "done"),
            Row("out_for_delivery", "current"),
            Row("delivered", "pending"),
        )
        assertEquals(
            listOf("preparing_dispatch", "out_for_delivery", "delivered"),
            truncate(rows).map { it.key },
        )
    }

    /** Terminal: every row done, nothing truncated, nothing invented. */
    @Test
    fun `a fully delivered journey keeps every completed row`() {
        val rows = listOf(
            Row("preparing_dispatch", "done"),
            Row("out_for_delivery", "done"),
            Row("delivered", "done"),
        )
        assertEquals(3, truncate(rows).size)
    }

    /** Never a queue of future steps, however many the server sends. */
    @Test
    fun `only one upcoming step survives no matter how many are pending`() {
        val rows = listOf(
            Row("a", "done"), Row("b", "pending"), Row("c", "pending"), Row("d", "pending"),
        )
        assertEquals(listOf("a", "b"), truncate(rows).map { it.key })
    }
}
