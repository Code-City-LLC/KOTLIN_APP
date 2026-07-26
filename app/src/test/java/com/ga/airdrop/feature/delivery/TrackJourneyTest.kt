package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.PackageTimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track renders Laravel's canonical journey **as sent**.
 *
 * These tests used to assert Kotlin's own ordering, dedup, status-8 exclusion
 * and one-pending cap, because the client built the rail. It no longer does —
 * `GET /packages/{id}/timeline` owns all of that, and the swap was made only
 * after checking the endpoint returned byte-for-byte what the client-side merge
 * produced for AIRQA0725003.
 *
 * So what is worth pinning now is the opposite: that the mapper does NOT
 * reorder, filter, cap or relabel. A client that "helps" is how three screens
 * ended up with three different answers.
 */
class TrackJourneyTest {

    private fun entry(
        key: String,
        label: String,
        state: String = "done",
        status: Int? = null,
        icon: String? = null,
        at: String? = null,
    ) = PackageTimelineEntry(
        key = key,
        status = status,
        label = label,
        icon = icon,
        at = at,
        atIso = null,
        state = state,
        source = if (status == null) "delivery" else "status",
    )

    /** The real pre-staging answer for AIRQA0725003. */
    private val liveJourney = listOf(
        entry("status_2_0", "Shipment Received", status = 2, icon = "shipment_received", at = "Jul 7, 2026 at 8:10 PM"),
        entry("status_3_1", "Port of Departure -MIA", status = 3, icon = "port_departure", at = "Jul 11, 2026 at 8:10 PM"),
        entry("delivery_assigned", "Preparing for Dispatch", icon = "dispatch", at = "Jul 23, 2026 at 9:28 AM"),
        entry("out_for_delivery", "Out for Delivery", state = "current", icon = "in_transit", at = "Jul 25, 2026 at 7:58 AM"),
        entry("delivered", "Delivered", state = "pending", icon = "delivered"),
    )

    @Test
    fun `rows preserve the server order exactly`() {
        assertEquals(
            listOf(
                "Shipment Received",
                "Port of Departure -MIA",
                "Preparing for Dispatch",
                "Out for Delivery",
                "Delivered",
            ),
            TrackJourney.rows(liveJourney).map { it.label },
        )
    }

    /**
     * `package_status_datetime` is a varchar and ~1,069 legacy rows use
     * `YYYY/MM/DD`, which sorts AFTER `YYYY-MM-DD`. Kotlin used to reorder by
     * catalogue position because of it. The server owns ordering now — so the
     * client must not "correct" an order that looks wrong to it.
     */
    @Test
    fun `an order the client would once have corrected is left alone`() {
        val outOfCatalogueOrder = listOf(
            entry("status_6_0", "Processing at our Warehouse", status = 6),
            entry("status_2_1", "Shipment Received", status = 2),
            entry("status_9_2", "Processing at Customs", status = 9),
        )
        assertEquals(
            listOf("Processing at our Warehouse", "Shipment Received", "Processing at Customs"),
            TrackJourney.rows(outOfCatalogueOrder).map { it.label },
        )
    }

    /** Nothing is dropped — not status 8, not a repeat, not an unknown status. */
    @Test
    fun `no row is ever filtered out`() {
        val awkward = listOf(
            entry("status_6_0", "Processing at our Warehouse", status = 6),
            entry("status_10_1", "Detained at Customs", status = 10),
            entry("status_6_2", "Processing at our Warehouse", status = 6),
            entry("status_44_3", "Held For Inspection", status = 44),
            entry("status_8_4", "Delivered", status = 8),
            entry("delivered", "Delivered"),
        )
        assertEquals(6, TrackJourney.rows(awkward).size)
    }

    /** The client no longer caps upcoming steps; the server sends one. */
    @Test
    fun `every pending row the server sends is rendered`() {
        val twoPending = listOf(
            entry("a", "A"),
            entry("b", "B", state = "pending"),
            entry("c", "C", state = "pending"),
        )
        assertEquals(3, TrackJourney.rows(twoPending).size)
        assertEquals(2, TrackJourney.rows(twoPending).count { it.state == "pending" })
    }

    /** Labels are the server's words, never rewritten. */
    @Test
    fun `labels pass through untouched`() {
        val ops = listOf(entry("assigned", "Driver Assigned"))
        assertEquals("Driver Assigned", TrackJourney.rows(ops).single().label)
    }

    /** A row with no label is not renderable, so it is skipped rather than faked. */
    @Test
    fun `a row with no label is skipped rather than invented`() {
        val rows = TrackJourney.rows(listOf(entry("a", "A"), PackageTimelineEntry(key = "b")))
        assertEquals(listOf("A"), rows.map { it.label })
    }

    /**
     * Kemar chose "show them, with a Contact us action" for statuses where the
     * journey has gone wrong. This is the last piece of journey policy left on
     * the client.
     */
    @Test
    fun `only statuses that have gone wrong offer help`() {
        listOf(10, 15, 16, 19).forEach {
            assertTrue("status $it must offer Contact us", TrackJourney.needsHelp(it))
        }
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 14, 18, null).forEach {
            assertFalse("status $it is normal progress", TrackJourney.needsHelp(it))
        }
    }

    @Test
    fun `a detained row is flagged for the contact action`() {
        val rows = TrackJourney.rows(
            listOf(
                entry("status_9_0", "Processing at Customs", status = 9),
                entry("status_10_1", "Detained at Customs", status = 10),
            ),
        )
        assertFalse(rows.first().needsHelp)
        assertTrue(rows.last().needsHelp)
    }

    /**
     * The icon vocabulary is still moving — within minutes on 2026-07-26 the
     * same "Out for Delivery" row came back as `in_transit` on one package and
     * `out_for_delivery` on another.
     */
    @Test
    fun `both observed spellings of the out-for-delivery glyph resolve alike`() {
        assertEquals(
            TrackJourney.iconRes("out_for_delivery", null, dark = false),
            TrackJourney.iconRes("in_transit", null, dark = false),
        )
    }

    /** An unknown key falls back to the status catalogue, never to a guess. */
    @Test
    fun `an unknown icon key falls back to the status catalogue`() {
        assertEquals(
            com.ga.airdrop.feature.shipments.ShipmentStatusCatalog.iconRes(2, dark = false),
            TrackJourney.iconRes("something_new_the_server_added", 2, dark = false),
        )
    }

    /** With neither a known key nor a status id, a neutral glyph — not a guess. */
    @Test
    fun `an unknown key with no status id is neutral`() {
        assertEquals(
            com.ga.airdrop.R.drawable.ic_packages,
            TrackJourney.iconRes("mystery", null, dark = false),
        )
    }
}
