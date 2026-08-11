package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.PackageTimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    /**
     * The pre-staging answer for AIRQA0725003.
     *
     * ⚠️ The `out_for_delivery` leg used to carry `icon = "in_transit"` here.
     * That was a REAL capture, not a typo — the projection genuinely sent it,
     * and it is what made me wrongly conclude the two keys were synonyms. The
     * server corrected it in 65130780, and `StatusIcons::DELIVERY_ICONS` in
     * deployed source now maps the leg `out_for_delivery` to icon
     * `out_for_delivery`. Keeping the old capture would pin a payload the
     * server can no longer produce — flagged by BrightHarbor (#80258).
     */
    private val liveJourney = listOf(
        entry("status_2_0", "Shipment Received", status = 2, icon = "shipment_received", at = "Jul 7, 2026 at 8:10 PM"),
        entry("status_3_1", "Port of Departure -MIA", status = 3, icon = "port_departure", at = "Jul 11, 2026 at 8:10 PM"),
        entry("delivery_assigned", "Preparing for Dispatch", icon = "dispatch", at = "Jul 23, 2026 at 9:28 AM"),
        entry("out_for_delivery", "Out for Delivery", state = "current", icon = "out_for_delivery", at = "Jul 25, 2026 at 7:58 AM"),
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

    /**
     * ⚠️ THIS TEST USED TO EXPECT THE ROW TO BE SKIPPED, AND THAT WAS THE BUG.
     *
     * Dropping an unlabelled entry made a real recorded event vanish from the
     * customer's journey with nothing anywhere to notice it — the same
     * "absence rendered as fact" class the repository guards close. The
     * repository now rejects such a payload outright (#95189); rows() fails
     * closed rather than silently repairing input, so the guard cannot be made
     * pointless by feeding this from a new source. Corrected after
     * @Codex-CodexKotlinAudit #95347.
     */
    @Test
    fun `a row with no label fails closed instead of being skipped`() {
        assertThrows(IllegalArgumentException::class.java) {
            // ⚠️ state MUST be valid here. My first version omitted it, so the
            // STATE guard threw and this test passed for the wrong reason —
            // relaxing the LABEL guard left it green. Only the label is absent.
            TrackJourney.rows(
                listOf(entry("a", "A"), PackageTimelineEntry(key = "b", state = "done")),
            )
        }
    }

    @Test
    fun `a row with no key fails closed instead of being faked`() {
        // `entry_<index>` is not an identity: it changes when the list
        // reorders, so per-row state attaches to the wrong row.
        assertThrows(IllegalArgumentException::class.java) {
            TrackJourney.rows(listOf(PackageTimelineEntry(label = "A", state = "done")))
        }
    }

    @Test
    fun `a row with no state fails closed instead of defaulting to done`() {
        // Defaulting told the customer a step COMPLETED that the server never
        // reported.
        assertThrows(IllegalArgumentException::class.java) {
            TrackJourney.rows(listOf(PackageTimelineEntry(key = "a", label = "A")))
        }
    }

    @Test
    fun `a row whose state is not the exact server vocabulary fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackJourney.rows(listOf(PackageTimelineEntry(key = "a", label = "A", state = "DONE")))
        }
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
     * ⚠️ THIS TEST HAS NOW BEEN WRONG IN BOTH DIRECTIONS. Read the history
     * before changing it again.
     *
     * v1 pinned `in_transit` and `out_for_delivery` to ONE glyph — my own
     * assumption that the server vocabulary was still settling. Wrong.
     * v2 pinned them to DIFFERENT glyphs, on the correct observation that they
     * are different EVENTS (`App\Support\StatusIcons`, deployed source):
     * `in_transit` is package status 12, a warehouse movement to the counter
     * with no driver; `out_for_delivery` is a delivery leg where a driver
     * physically has the package. Both can appear on one package.
     *
     * v3 — this one — is the OWNER'S CALL, and it is a product decision, not a
     * correction of v2. Kemar, 2026-07-26, verbatim: "In transit to counter can
     * be the same as out for delivery. Why? Because we hardly ever use that
     * update. When the time comes, we will update it, but we hardly ever use
     * it. So make note of that." Figma 40000692-4169 is assigned to both rows.
     *
     * So the events stay distinct and the ARTWORK is deliberately shared. This
     * test now pins exactly that, and would catch someone "fixing" the
     * duplicate back out — which is the likely next mistake, because it looks
     * like the bug v2 fixed.
     */
    @Test
    fun `in_transit deliberately shares the out_for_delivery glyph, by owner decision`() {
        val warehouseMovement = TrackJourney.iconRes("in_transit", null, dark = false)
        val driverLeg = TrackJourney.iconRes("out_for_delivery", null, dark = false)

        assertEquals(
            "Kemar assigned Figma 40000692-4169 to BOTH rows — this duplicate is authorised",
            driverLeg,
            warehouseMovement,
        )
        assertEquals(
            com.ga.airdrop.R.drawable.ic_shipments_status_out_for_delivery,
            warehouseMovement,
        )
    }

    /**
     * The glyph is shared; the EVENTS are not. Merging the keys would be a real
     * defect and is not what the owner authorised — only the artwork is shared.
     * Kept as a separate test so that lifting the duplicate later (when status
     * 12 gets its own icon) cannot quietly take this guarantee with it.
     */
    @Test
    fun `sharing the glyph does not merge the two events`() {
        val rows = TrackJourney.rows(
            listOf(
                entry("status_12_0", "In-Transit to counter", status = 12, icon = "in_transit"),
                entry("leg_ofd_1", "Out for Delivery", status = null, icon = "out_for_delivery"),
            ),
        )

        assertEquals(2, rows.size)
        assertEquals("in_transit", rows.first().iconKey)
        assertEquals("out_for_delivery", rows.last().iconKey)
        assertEquals("status 12 is a warehouse movement", 12, rows.first().statusId)
        assertEquals("a delivery leg carries no status id", null, rows.last().statusId)
    }

    /**
     * The three delivery legs, transcribed from `StatusIcons::DELIVERY_ICONS`
     * in deployed source. These carry NO status id, so the status-catalogue
     * fallback cannot rescue a wrong key here — a mismatch renders the neutral
     * package glyph and the customer sees the last mile lose its icons.
     */
    @Test
    fun `the three delivery legs resolve to their published glyphs`() {
        val legs = TrackJourney.rows(
            listOf(
                entry("delivery_assigned", "Preparing for Dispatch", icon = "dispatch"),
                entry("out_for_delivery", "Out for Delivery", icon = "out_for_delivery"),
                entry("delivered", "Delivered", icon = "delivered"),
            ),
        )
        assertEquals(3, legs.size)
        legs.forEach { row ->
            assertEquals("a delivery leg carries no status id", null, row.statusId)
            assertNotEquals(
                "${row.iconKey} fell through to the neutral fallback",
                com.ga.airdrop.R.drawable.ic_packages,
                TrackJourney.iconRes(row.iconKey, row.statusId, dark = false),
            )
        }
        assertEquals(
            com.ga.airdrop.R.drawable.ic_shipments_status_out_for_delivery,
            TrackJourney.iconRes("out_for_delivery", null, dark = false),
        )
    }

    /**
     * The vocabulary is CLOSED — `StatusIcons::KEYS` plus the delivery legs.
     * Every one must resolve without falling through to the status-id
     * fallback, because a delivery leg has no status id to fall back to.
     *
     * Four of these were wrong until 2026-07-26: I had spelled them
     * `customs_detained`, `dangerous_goods` and `returned_merchant`, and had no
     * `auction` branch at all. The status-id fallback was quietly covering for
     * three of them, which is exactly how a mismatch survives review.
     */
    @Test
    fun `every published icon key resolves without the status fallback`() {
        val published = listOf(
            "auction", "customs_processing", "customs_released", "dangerous",
            "delivered", "detained", "dispatch", "drop_alerted", "in_transit",
            "out_for_delivery", "paid_ready_pickup", "port_arrived",
            "port_departure", "ready_pickup", "returned", "shipment_received",
            "uncollected", "warehouse",
        )
        listOf(false, true).forEach { dark ->
            published.forEach { key ->
                assertNotEquals(
                    "$key (dark=$dark) fell through to the neutral fallback",
                    com.ga.airdrop.R.drawable.ic_packages,
                    TrackJourney.iconRes(key, statusId = null, dark = dark),
                )
            }
        }
        assertEquals("the published set is 18 keys", 18, published.size)
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
