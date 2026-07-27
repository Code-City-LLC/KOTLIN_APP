package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Status 20 — "Paid and Ready for Delivery" — is what a package becomes right
 * after checkout, and it was missing from the Android catalogue entirely.
 *
 * Two separate holes, both closed here:
 *
 *  1. `defaults` stopped at 19, so status 20 could not be filtered for at all —
 *     and if `/package-statuses` failed, the fallback list silently omitted it.
 *  2. `iconRes` stopped at 19, so a status-20 package fell through to the
 *     neutral `ic_packages` box while Laravel's `StatusIcons::MAP` says
 *     `20 => paid_ready_pickup`.
 *
 * ⚠️ THE COLOUR IS INHERITED, NOT DESIGNED. Kemar chose to reuse the "Paid and
 * Ready for Pick Up" styling rather than have me invent one. The duplicate
 * `#19d144` is therefore deliberate and recorded — a later reader must not
 * "fix" it as an accident, and if a distinct colour is ever wanted this is the
 * decision that changes.
 */
class StatusTwentyCatalogTest {

    private fun entry(id: Int) = ShipmentStatusCatalog.defaults.firstOrNull { it.id == id }

    @Test
    fun `status 20 is present in the fallback catalogue`() {
        val twenty = entry(20)

        assertNotNull(
            "a package is status 20 immediately after checkout — omitting it means " +
                "the customer cannot filter for the packages they just paid for",
            twenty,
        )
        assertEquals("Paid and Ready for Delivery", twenty!!.name)
    }

    /** Deliberate duplicate, per the owner. Pinned so nobody "corrects" it. */
    @Test
    fun `status 20 intentionally reuses the Pick Up colour`() {
        val pickUp = entry(18)
        val delivery = entry(20)

        assertNotNull(pickUp); assertNotNull(delivery)
        assertEquals(
            "Kemar chose to inherit this rather than have a colour invented",
            pickUp!!.colorCode,
            delivery!!.colorCode,
        )
    }

    /** Same colour, different statuses — the names must not collapse too. */
    @Test
    fun `pick up and delivery remain distinct statuses`() {
        assertNotEquals(entry(18)!!.name, entry(20)!!.name)
        assertNotEquals(entry(18)!!.id, entry(20)!!.id)
    }

    /**
     * Transcribed from Laravel's `StatusIcons::MAP` (`20 => paid_ready_pickup`),
     * which is the server's contract — not a glyph chosen on this side.
     */
    @Test
    fun `status 20 resolves to the paid-ready glyph, not the neutral box`() {
        val light = ShipmentStatusCatalog.iconRes(20, dark = false)
        val dark = ShipmentStatusCatalog.iconRes(20, dark = true)
        val neutral = ShipmentStatusCatalog.iconRes(9999, dark = false)

        assertNotEquals("status 20 must not fall through to ic_packages", neutral, light)
        assertEquals(ShipmentStatusCatalog.iconRes(18, dark = false), light)
        assertEquals(ShipmentStatusCatalog.iconRes(18, dark = true), dark)
    }

    /** An unmapped status still falls back — this change must not break that. */
    @Test
    fun `an unknown status still resolves to the neutral glyph`() {
        assertTrue(ShipmentStatusCatalog.iconRes(9999, dark = false) > 0)
        assertEquals(
            ShipmentStatusCatalog.iconRes(9999, dark = false),
            ShipmentStatusCatalog.iconRes(4242, dark = false),
        )
    }
}
