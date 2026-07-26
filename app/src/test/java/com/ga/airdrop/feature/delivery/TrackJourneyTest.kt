package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.repo.TrackedDelivery
import com.ga.airdrop.data.repo.TrackedDeliveryStage
import com.ga.airdrop.feature.shipments.PackageHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track must show the package's REAL journey.
 *
 * Kemar, 2026-07-26, looking at the shipped app: *"you started preparing for
 * dispatch, but that's not where it starts. It starts at shipment received or
 * drop alerted... It needs to show the REAL statuses"* and *"use only real
 * generated info not static"*.
 *
 * The screen used to render a hardcoded three-stage skeleton beginning at
 * "Preparing for Dispatch", so a customer whose package had been received,
 * cleared customs and been processed saw none of it. These tests pin the merge
 * so that skeleton cannot come back.
 */
class TrackJourneyTest {

    private fun history(vararg pairs: Pair<Int, String>) =
        pairs.map { (id, name) -> PackageHistoryItem(status = id, statusName = name) }

    private fun lastMile(vararg stages: Triple<String, String, String>) =
        TrackedDelivery(
            status = "assigned",
            scheduledDate = null,
            assignedAt = null,
            outForDeliveryAt = null,
            deliveredAt = null,
            stages = stages.map { (key, label, state) ->
                TrackedDeliveryStage(key = key, label = label, state = state, at = null)
            },
        )

    /** THE regression. The first thing on screen is the first real event. */
    @Test
    fun `the journey begins at the first recorded warehouse status`() {
        val rows = TrackJourney.build(
            history(1 to "Drop Alerted", 2 to "Shipment Received"),
            lastMile(Triple("assigned", "Driver Assigned", "current")),
        )
        assertEquals("Drop Alerted", rows.first().label)
        assertTrue(rows.map { it.label }.contains("Shipment Received"))
    }

    /** And it is never allowed to START at the last mile again. */
    @Test
    fun `preparing for dispatch is not the first row when history exists`() {
        val rows = TrackJourney.build(
            history(2 to "Shipment Received", 6 to "Processing at our Warehouse"),
            lastMile(Triple("assigned", "Driver Assigned", "current")),
        )
        assertFalse(rows.first().label == "Preparing for Dispatch")
        assertEquals("Shipment Received", rows.first().label)
    }

    /**
     * `package_status_datetime` is a varchar and ~1,069 legacy rows use
     * `YYYY/MM/DD`, which sorts AFTER `YYYY-MM-DD`. Ordering by the string
     * would put 2019 records at the end of the rail. Order by the catalogue.
     */
    @Test
    fun `rows follow catalogue progression not the recorded order`() {
        val rows = TrackJourney.warehouseRail(
            history(
                6 to "Processing at our Warehouse",
                1 to "Drop Alerted",
                9 to "Processing at Customs",
                2 to "Shipment Received",
            ),
        )
        assertEquals(
            listOf(
                "Drop Alerted",
                "Shipment Received",
                "Processing at Customs",
                "Processing at our Warehouse",
            ),
            rows.map { it.label },
        )
    }

    /**
     * markDelivered() writes BOTH a status-8 history row and a
     * `package_deliveries` row, so keeping status 8 prints Delivered twice.
     */
    @Test
    fun `status 8 is dropped from the warehouse rail so Delivered is not doubled`() {
        val rows = TrackJourney.build(
            history(2 to "Shipment Received", 8 to "Delivered"),
            lastMile(
                Triple("assigned", "Driver Assigned", "done"),
                Triple("out_for_delivery", "Out for Delivery", "done"),
                Triple("delivered", "Delivered", "done"),
            ),
        )
        assertEquals(1, rows.count { it.label == "Delivered" })
    }

    /**
     * SwiftHawk #79761 §1: a fixed list may ORDER the rail; it must never
     * FILTER it. An unmodelled status still reaches the customer, using the
     * server's own label, after the statuses we do know.
     */
    @Test
    fun `an unknown status still renders with the server label`() {
        val rows = TrackJourney.warehouseRail(
            history(2 to "Shipment Received", 44 to "Held For Inspection"),
        )
        assertEquals(listOf("Shipment Received", "Held For Inspection"), rows.map { it.label })
    }

    /** Internal staff notes and names never leave the warehouse. */
    @Test
    fun `staff comments never reach a row`() {
        val rows = TrackJourney.warehouseRail(
            listOf(
                PackageHistoryItem(
                    status = 2,
                    statusName = "Shipment Received",
                    comment = "customer is difficult, flagged by Marcia",
                ),
            ),
        )
        assertEquals("Shipment Received", rows.single().label)
    }

    /** A status can legitimately recur; only consecutive repeats collapse. */
    @Test
    fun `a status that recurs after another status is shown twice`() {
        val rows = TrackJourney.warehouseRail(
            history(6 to "Processing at our Warehouse", 6 to "Processing at our Warehouse"),
        )
        assertEquals(1, rows.size)
    }

    /** Kemar rule 3 (#79650): everything done, plus exactly ONE upcoming step. */
    @Test
    fun `only one upcoming step survives the merge`() {
        val rows = TrackJourney.build(
            history(2 to "Shipment Received"),
            lastMile(
                Triple("assigned", "Driver Assigned", "current"),
                Triple("out_for_delivery", "Out for Delivery", "pending"),
                Triple("delivered", "Delivered", "pending"),
            ),
        )
        assertEquals(1, rows.count { it.state == "pending" })
        assertEquals("Out for Delivery", rows.last().label)
    }

    /**
     * History can fail to load while tracking succeeds (it is fetched with
     * runCatching precisely so Track degrades instead of blanking). The last
     * mile must still render.
     */
    @Test
    fun `an empty history still renders the last mile`() {
        val rows = TrackJourney.build(
            emptyList(),
            lastMile(Triple("assigned", "Driver Assigned", "current")),
        )
        assertEquals(1, rows.size)
    }

    /** No delivery row yet: the warehouse rail alone is the journey. */
    @Test
    fun `a package with no last mile shows its warehouse rail`() {
        val rows = TrackJourney.build(history(1 to "Drop Alerted", 2 to "Shipment Received"), null)
        assertEquals(listOf("Drop Alerted", "Shipment Received"), rows.map { it.label })
        assertTrue(rows.all { it.state == "done" })
    }

    /** Warehouse rows carry their status id so the row gets its real glyph. */
    @Test
    fun `warehouse rows keep their status id for icon lookup`() {
        val rows = TrackJourney.warehouseRail(history(2 to "Shipment Received"))
        assertEquals(2, rows.single().statusId)
    }
}
