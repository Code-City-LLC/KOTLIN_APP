package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.DeliveryWarehouse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic parity tests for the Delivery Method screen
 * (docs/PARITY_GAP_SPECS.md §3 / Swift FigmaDeliveryMethodViewController):
 *  - Continue gating: pickup requires a warehouse ("Pick a location");
 *    delivery proceeds with a marker, falls back to the search path with a
 *    typed query, errors on empty ("Enter a delivery address");
 *  - the 4 hard-coded fallback warehouses match Swift fallbackWarehouses()
 *    exactly (names, addresses, coords, is_primary);
 *  - the stale-search guard applies results only when the request is still
 *    the latest AND the field text is unchanged (Swift does BOTH checks);
 *  - warehouse auto-selection prefers isPrimary, then first, and a saved
 *    pickup label overrides case-insensitively (Swift renderPickupList).
 */
class DeliveryMethodLogicTest {

    private fun warehouse(id: Int, name: String, primary: Boolean = false) =
        DeliveryWarehouse(id = id, name = name, address = "$name address", isPrimary = primary)

    /* ─── Continue gating ──────────────────────────────────────────────── */

    @Test
    fun `pickup without selected warehouse errors Pick a location`() {
        val decision = decideContinue(
            mode = DeliveryMode.Pickup,
            selectedWarehouseId = null,
            warehouses = fallbackWarehouses(),
            pickupLabel = null,
            markerCoord = null,
            validatedAddress = null,
            searchQuery = "",
        )
        assertTrue(decision is ContinueDecision.ShowError)
        decision as ContinueDecision.ShowError
        assertEquals("Pick a location", decision.title)
        assertEquals("Please select a pickup warehouse to continue.", decision.message)
    }

    @Test
    fun `pickup with selected warehouse saves its name as the label`() {
        val decision = decideContinue(
            mode = DeliveryMode.Pickup,
            selectedWarehouseId = 2,
            warehouses = fallbackWarehouses(),
            pickupLabel = "Kingston",
            markerCoord = null,
            validatedAddress = null,
            searchQuery = "",
        )
        assertTrue(decision is ContinueDecision.SavePickup)
        assertEquals("Montego Bay", (decision as ContinueDecision.SavePickup).label)
    }

    @Test
    fun `pickup with unknown selected id falls back to the saved label`() {
        val decision = decideContinue(
            mode = DeliveryMode.Pickup,
            selectedWarehouseId = 99,
            warehouses = fallbackWarehouses(),
            pickupLabel = "Somewhere Saved",
            markerCoord = null,
            validatedAddress = null,
            searchQuery = "",
        )
        assertTrue(decision is ContinueDecision.SavePickup)
        assertEquals("Somewhere Saved", (decision as ContinueDecision.SavePickup).label)
    }

    @Test
    fun `delivery with marker proceeds with the validated address`() {
        val decision = decideContinue(
            mode = DeliveryMode.Delivery,
            selectedWarehouseId = null,
            warehouses = emptyList(),
            pickupLabel = null,
            markerCoord = 18.0 to -76.8,
            validatedAddress = "Half Way Tree, Kingston",
            searchQuery = "half way",
        )
        assertTrue(decision is ContinueDecision.SaveDelivery)
        decision as ContinueDecision.SaveDelivery
        assertEquals(18.0, decision.latitude, 0.0)
        assertEquals(-76.8, decision.longitude, 0.0)
        assertEquals("Half Way Tree, Kingston", decision.address)
    }

    @Test
    fun `delivery with marker but no validated address falls back to the typed query`() {
        val decision = decideContinue(
            mode = DeliveryMode.Delivery,
            selectedWarehouseId = null,
            warehouses = emptyList(),
            pickupLabel = null,
            markerCoord = 18.0 to -76.8,
            validatedAddress = null,
            searchQuery = "  Ocho Rios  ",
        )
        assertTrue(decision is ContinueDecision.SaveDelivery)
        assertEquals("Ocho Rios", (decision as ContinueDecision.SaveDelivery).address)
    }

    @Test
    fun `delivery with query but no marker triggers the search path`() {
        val decision = decideContinue(
            mode = DeliveryMode.Delivery,
            selectedWarehouseId = null,
            warehouses = emptyList(),
            pickupLabel = null,
            markerCoord = null,
            validatedAddress = null,
            searchQuery = "Portmore",
        )
        assertTrue(decision is ContinueDecision.RunSearch)
    }

    @Test
    fun `delivery with nothing errors Enter a delivery address`() {
        val decision = decideContinue(
            mode = DeliveryMode.Delivery,
            selectedWarehouseId = null,
            warehouses = emptyList(),
            pickupLabel = null,
            markerCoord = null,
            validatedAddress = null,
            searchQuery = "   ",
        )
        assertTrue(decision is ContinueDecision.ShowError)
        decision as ContinueDecision.ShowError
        assertEquals("Enter a delivery address", decision.title)
        assertEquals(
            "Type a town or parish to look up your delivery address, " +
                "or tap Use Current Location.",
            decision.message,
        )
    }

    /* ─── Fallback warehouses (exact Swift fallbackWarehouses()) ───────── */

    @Test
    fun `fallback warehouses match the four Swift entries exactly`() {
        val list = fallbackWarehouses()
        assertEquals(4, list.size)

        assertEquals(1, list[0].id)
        assertEquals("Kingston", list[0].name)
        assertEquals(
            "Unit 19 Pristine Plaza, 15 Eastwood Park Rd, Kingston, Jamaica",
            list[0].address,
        )
        assertEquals(18.012, list[0].latitude!!, 1e-9)
        assertEquals(-76.793, list[0].longitude!!, 1e-9)
        assertEquals(true, list[0].isPrimary)

        assertEquals(2, list[1].id)
        assertEquals("Montego Bay", list[1].name)
        assertEquals(
            "Unit 14, The Annex Fairview Shopping Center, Montego Bay, Jamaica",
            list[1].address,
        )
        assertEquals(18.470, list[1].latitude!!, 1e-9)
        assertEquals(-77.918, list[1].longitude!!, 1e-9)
        assertEquals(false, list[1].isPrimary)

        assertEquals(3, list[2].id)
        assertEquals("Savanna-La-Mar", list[2].name)
        assertEquals("33 Beckford St, Savanna la Mar, Jamaica", list[2].address)
        assertEquals(18.219, list[2].latitude!!, 1e-9)
        assertEquals(-78.135, list[2].longitude!!, 1e-9)
        assertEquals(false, list[2].isPrimary)

        assertEquals(4, list[3].id)
        assertEquals("Yallas", list[3].name)
        assertEquals("VCJ5+XMH, Poor Mans Corner, Jamaica", list[3].address)
        assertEquals(17.881, list[3].latitude!!, 1e-9)
        assertEquals(-76.564, list[3].longitude!!, 1e-9)
        assertEquals(false, list[3].isPrimary)
    }

    /* ─── Stale-search guard (Swift latestSearchQuery + field-text) ────── */

    @Test
    fun `search results apply when request is latest and field unchanged`() {
        assertTrue(shouldApplySearchResults("kingston", "kingston", "kingston"))
        // Field text is compared trimmed (the request query is pre-trimmed).
        assertTrue(shouldApplySearchResults("kingston", "kingston", "  kingston  "))
    }

    @Test
    fun `search results dropped when a newer request superseded this one`() {
        assertFalse(shouldApplySearchResults("montego bay", "kingston", "kingston"))
    }

    @Test
    fun `search results dropped when the user kept typing`() {
        assertFalse(shouldApplySearchResults("kingston", "kingston", "kingston har"))
    }

    /* ─── Warehouse auto-selection (Swift renderPickupList) ────────────── */

    @Test
    fun `auto-select prefers the primary warehouse`() {
        val warehouses = listOf(
            warehouse(1, "Kingston"),
            warehouse(2, "Montego Bay", primary = true),
        )
        assertEquals(2, resolveSelectedWarehouseId(warehouses, null, null))
    }

    @Test
    fun `auto-select falls back to the first warehouse without a primary`() {
        val warehouses = listOf(warehouse(1, "Kingston"), warehouse(2, "Montego Bay"))
        assertEquals(1, resolveSelectedWarehouseId(warehouses, null, null))
    }

    @Test
    fun `saved pickup label overrides case-insensitively`() {
        val warehouses = listOf(
            warehouse(1, "Kingston", primary = true),
            warehouse(2, "Montego Bay"),
        )
        assertEquals(2, resolveSelectedWarehouseId(warehouses, null, "montego bay"))
        // An existing manual selection is also overridden by a matching label
        // (Swift re-applies the saved label on every render).
        assertEquals(2, resolveSelectedWarehouseId(warehouses, 1, "MONTEGO BAY"))
    }

    @Test
    fun `existing selection is kept when the label matches nothing`() {
        val warehouses = listOf(warehouse(1, "Kingston"), warehouse(2, "Montego Bay"))
        assertEquals(2, resolveSelectedWarehouseId(warehouses, 2, "Nowhere"))
    }

    @Test
    fun `empty warehouse list selects nothing`() {
        assertNull(resolveSelectedWarehouseId(emptyList(), null, "Kingston"))
    }

    // ── Swift-parity deltas 2026-07-12 (delivery audit) ─────────────────────

    @Test
    fun `packageWeightKg prefers the kg string and strips its suffix`() {
        assertEquals(1.3, com.ga.airdrop.feature.shipments.packageWeightKg("1.30", null)!!, 1e-9)
        assertEquals(2.5, com.ga.airdrop.feature.shipments.packageWeightKg("2.5 kg", 99.0)!!, 1e-9)
        assertEquals(4.0, com.ga.airdrop.feature.shipments.packageWeightKg("4KG", null)!!, 1e-9)
    }

    @Test
    fun `packageWeightKg falls back to pounds at the Swift factor`() {
        assertEquals(0.45359237, com.ga.airdrop.feature.shipments.packageWeightKg(null, 1.0)!!, 1e-9)
        assertEquals(0.45359237, com.ga.airdrop.feature.shipments.packageWeightKg("", 1.0)!!, 1e-9)
        // Unparseable kg string also falls through to lbs.
        assertEquals(0.90718474, com.ga.airdrop.feature.shipments.packageWeightKg("n/a", 2.0)!!, 1e-9)
    }

    @Test
    fun `packageWeightKg is null for weightless and zero-weight lines`() {
        assertNull(com.ga.airdrop.feature.shipments.packageWeightKg(null, null))
        assertNull(com.ga.airdrop.feature.shipments.packageWeightKg("0", 0.0))
        assertNull(com.ga.airdrop.feature.shipments.packageWeightKg(null, -1.0))
    }

    @Test
    fun `looksLikeCoordPair matches only raw lat-lng strings`() {
        assertTrue(looksLikeCoordPair("18.10960, -77.29750"))
        assertTrue(looksLikeCoordPair("-18.1,77.2"))
        assertFalse(looksLikeCoordPair("Mandeville, Manchester"))
        assertFalse(looksLikeCoordPair("18.1"))
        assertFalse(looksLikeCoordPair("18.1, 77.2, 3"))
    }
}
