package com.ga.airdrop.feature.shipments

import com.ga.airdrop.feature.delivery.TrackJourney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Package Details "Shipment Timeline" must be the package's REAL history.
 *
 * It used to be `PackageTimelineProgression`: a fixed seven-rung ladder (Drop
 * Alerted → Shipment Received → Port of Departure MIA → Arrived at Port JAM →
 * Ready for Pickup → Paid and Ready for Pick Up → Delivered) derived from the
 * package's CURRENT status. It never read `history` for the row list. Both
 * failure directions were live on pre-staging:
 *
 *  - **It invented events.** Package 153897 has status 20 and ZERO history
 *    rows. An unrecognised status fell through to `return visibleStatuses`, so
 *    the screen printed all seven rungs for a package that had recorded none.
 *  - **It deleted events.** The ladder has no entry for Released From Customs
 *    (5) or Processing at our Warehouse (6), so package 153907 — which really
 *    passed through both — showed neither.
 *
 * Kemar 2026-07-26: *"once we go into details on packages, we would see the
 * full thing as well, the full tracking history as well, which could be up to
 * twelve statuses if needs be or even more."*
 *
 * These tests run against the same builder the screen calls.
 */
class PackageDetailsTimelineTest {

    /** Package 153907's real history, verbatim from pre-staging. */
    private val realHistory = listOf(
        2 to "Shipment Received",
        3 to "Port of Departure -MIA",
        4 to "Arrived at Port -JAM",
        5 to "Released From Customs",
        6 to "Processing at our Warehouse",
        7 to "Ready for Pickup",
        8 to "Delivered",
    ).map { (id, name) -> PackageHistoryItem(status = id, statusName = name) }

    @Test
    fun `the rail shows the statuses the ladder used to drop`() {
        val labels = TrackJourney.warehouseRail(realHistory).map { it.label }
        assertTrue("Released From Customs", labels.contains("Released From Customs"))
        assertTrue("Processing at our Warehouse", labels.contains("Processing at our Warehouse"))
    }

    @Test
    fun `the rail never invents a status the package did not record`() {
        val labels = TrackJourney.warehouseRail(realHistory).map { it.label }
        // Neither appears in this package's history; the ladder printed both.
        assertFalse(labels.contains("Drop Alerted"))
        assertFalse(labels.contains("Paid and Ready for Pick Up"))
    }

    @Test
    fun `a twelve status journey renders every one of them`() {
        val long = listOf(
            1 to "Drop Alerted",
            2 to "Shipment Received",
            3 to "Port of Departure -MIA",
            4 to "Arrived at Port -JAM",
            9 to "Processing at Customs",
            10 to "Detained at Customs",
            5 to "Released From Customs",
            6 to "Processing at our Warehouse",
            12 to "In-Transit to counter",
            7 to "Ready for Pickup",
            18 to "Paid and Ready for Pick Up",
            14 to "Proof of Delivery",
        ).map { (id, name) -> PackageHistoryItem(status = id, statusName = name) }
        assertEquals(12, TrackJourney.warehouseRail(long).size)
    }

    /**
     * Kemar chose "show them, with a Contact us action" for statuses where the
     * journey has gone wrong.
     */
    @Test
    fun `statuses that have gone wrong are flagged for a contact action`() {
        val rows = TrackJourney.warehouseRail(
            listOf(
                PackageHistoryItem(status = 9, statusName = "Processing at Customs"),
                PackageHistoryItem(status = 10, statusName = "Detained at Customs"),
            ),
        )
        assertFalse("Processing at Customs is normal progress", rows.first().needsHelp)
        assertTrue("Detained at Customs needs a human", rows.last().needsHelp)
    }

    @Test
    fun `detained uncollected dangerous goods and returned all offer help`() {
        listOf(10, 15, 16, 19).forEach { id ->
            assertTrue("status $id must offer Contact us", TrackJourney.needsHelp(id))
        }
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 14, 18).forEach { id ->
            assertFalse("status $id is normal progress", TrackJourney.needsHelp(id))
        }
    }
}
