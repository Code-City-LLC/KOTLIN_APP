package com.ga.airdrop.data.model

import com.ga.airdrop.feature.auth.pickupLocationOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kemar product ruling 2026-07-19: exactly THREE pickup counters — Montego
 * Bay, Kingston, Savanna-La-Mar — shown at sign-up and every pickup
 * selector. Delivery is NOT limited (routing keeps all warehouses
 * server-side); only pickup lists filter.
 */
class PickupCountersTest {

    @Test
    fun `the canonical list is exactly the three counters`() {
        assertEquals(
            listOf("Montego Bay", "Kingston", "Savanna-La-Mar"),
            PICKUP_COUNTER_NAMES,
        )
    }

    @Test
    fun `matcher accepts the three counters in any punctuation or case`() {
        assertTrue(isPickupCounter("Montego Bay"))
        assertTrue(isPickupCounter("Kingston"))
        assertTrue(isPickupCounter("Savanna-La-Mar"))
        assertTrue(isPickupCounter("Savanna La Mar"))
        assertTrue(isPickupCounter("savanna-la-mar"))
        assertTrue(isPickupCounter("KINGSTON"))
    }

    @Test
    fun `matcher rejects delivery-only warehouses and unknowns`() {
        assertFalse(isPickupCounter("Ocho Rios Main"))
        assertFalse(isPickupCounter("Yallas"))
        assertFalse(isPickupCounter("Ocho Rios"))
        assertFalse(isPickupCounter(""))
        assertFalse(isPickupCounter(null))
    }

    @Test
    fun `a five-warehouse settings payload filters to the three pickup counters`() {
        val served = listOf(
            DeliveryWarehouse(id = 1, name = "Montego Bay"),
            DeliveryWarehouse(id = 2, name = "Kingston"),
            DeliveryWarehouse(id = 3, name = "Ocho Rios Main"),
            DeliveryWarehouse(id = 4, name = "Savanna-La-Mar"),
            DeliveryWarehouse(id = 5, name = "Yallas"),
        )
        val pickup = served.filter { it.supportsPickup != false && isPickupCounter(it.name) }
        assertEquals(listOf(1, 2, 4), pickup.map { it.id })
    }

    @Test
    fun `a server supports_pickup=false flag excludes even a named counter`() {
        val served = listOf(
            DeliveryWarehouse(id = 1, name = "Kingston", supportsPickup = false),
            DeliveryWarehouse(id = 2, name = "Montego Bay", supportsPickup = true),
        )
        val pickup = served.filter { it.supportsPickup != false && isPickupCounter(it.name) }
        assertEquals(listOf(2), pickup.map { it.id })
    }

    @Test
    fun `sign-up options are the same canonical instance — no duplicate list`() {
        assertTrue(pickupLocationOptions === PICKUP_COUNTER_NAMES)
    }
}
