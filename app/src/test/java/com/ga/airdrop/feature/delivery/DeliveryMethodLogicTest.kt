package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.DeliveryWarehouse
import com.ga.airdrop.feature.shipments.ShipmentPackage
import com.ga.airdrop.feature.shipments.toCartLine
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
            warehouses = serverWarehouses(),
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

    /**
     * ⚠️ THE ID OFFSET, IN ONE ASSERTION.
     *
     * This test previously ran against the hardcoded fallback list and asserted
     * that **id 2 is Montego Bay**. Against the live server list, **id 2 is
     * Kingston** — Montego Bay is id 1. Selection persists by id, so every
     * customer who chose a pickup branch while the fallback was on screen had a
     * different branch saved against their order. The fallback is gone; this
     * now pins the real mapping.
     */
    @Test
    fun `pickup with selected warehouse saves its name as the label`() {
        val decision = decideContinue(
            mode = DeliveryMode.Pickup,
            selectedWarehouseId = 2,
            warehouses = serverWarehouses(),
            pickupLabel = "Kingston",
            markerCoord = null,
            validatedAddress = null,
            searchQuery = "",
        )
        assertTrue(decision is ContinueDecision.SavePickup)
        assertEquals("Kingston", (decision as ContinueDecision.SavePickup).label)
    }

    @Test
    fun `pickup with unknown selected id falls back to the saved label`() {
        val decision = decideContinue(
            mode = DeliveryMode.Pickup,
            selectedWarehouseId = 99,
            warehouses = serverWarehouses(),
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

    /* ─── Pickup locations come from the server ─────────────────────── */

    /**
     * ⚠️ THIS REPLACED A TEST THAT ASSERTED THE BUG.
     *
     * `fallback warehouses match the four Swift entries exactly` pinned a
     * hardcoded list of four branches that the client rendered whenever
     * /delivery/settings failed or came back empty. Checked against the live
     * response, that list named a branch the server does not have ("Yallas")
     * and its IDs were offset: the fallback had Kingston = 1, the server has
     * Kingston = 2 and Montego Bay = 1. Selection persists BY ID, so a customer
     * picking "Kingston" had Montego Bay saved against their order.
     *
     * There is no fallback now, so what is worth pinning is that the screen
     * stays honest when the list cannot be loaded.
     */
    @Test
    fun `no pickup location is offered when the server list is empty`() {
        assertEquals(
            "nothing may be selected from an empty list",
            null,
            resolveSelectedWarehouseId(
                warehouses = emptyList(),
                currentSelectedId = null,
                pickupLabel = null,
            ),
        )
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

    @Test
    fun `shipment canonical kilograms drive checkout weight`() {
        val line = ShipmentPackage(id = 1, weightKg = "2.5 kg", weightLbs = 99.0).toCartLine()
        assertEquals(2.5, line.weightKg!!, 0.0)
    }

    @Test
    fun `shipment canonical pounds convert to checkout kilograms`() {
        val line = ShipmentPackage(id = 2, weightLbs = 10.0).toCartLine()
        assertEquals(4.5359237, line.weightKg!!, 1e-9)
    }

    @Test
    fun `shipment legacy display weight is never interpreted as kilograms`() {
        val line = ShipmentPackage(id = 3, weight = "10LBS").toCartLine()
        assertNull(line.weightKg)
    }

    /** The live pre-staging response, verbatim: three branches, server IDs. */
    private fun serverWarehouses() = listOf(
        DeliveryWarehouse(
            id = 1, name = "Montego Bay",
            address = "Unit 14, The Annex Fairview Shopping Center, Montego Bay, Jamaica",
            latitude = 18.45323945, longitude = -77.92337403, isPrimary = false,
        ),
        DeliveryWarehouse(
            id = 2, name = "Kingston",
            address = "Unit 19 Pristine Plaza, 15 Eastwood Park Rd, Kingston, Jamaica",
            latitude = 18.0129377, longitude = -76.79937405, isPrimary = false,
        ),
        DeliveryWarehouse(
            id = 4, name = "Savanna-La-Mar",
            address = "33 Beckford St, Savanna la Mar, Jamaica",
            latitude = 18.22456944, longitude = -78.13282102, isPrimary = false,
        ),
    )
}
