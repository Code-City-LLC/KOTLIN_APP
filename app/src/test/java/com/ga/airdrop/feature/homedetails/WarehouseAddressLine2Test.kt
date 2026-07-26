package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.Warehouse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Address Line 2 is the string a customer copies into a US merchant's checkout.
 * If it is wrong the parcel arrives unroutable, so every part of it is pinned.
 */
class WarehouseAddressLine2Test {

    private val warehouse = Warehouse(
        unit = "Unit G36",
        addressLine2Tokens = mapOf(
            "standard" to "AIR",
            "express" to "EXPRESS",
            "seadrop" to "SEA",
        ),
        addressLine2Separator = " - ",
    )

    @Test
    fun `Kemar's exact format, per shipping method`() {
        assertEquals(
            "Unit G36 - AIR – 14823",
            WarehouseAddressLine2.format(warehouse, "standard", "14823"),
        )
        assertEquals(
            "Unit G36 - SEA – 14823",
            WarehouseAddressLine2.format(warehouse, "seadrop", "14823"),
        )
        assertEquals(
            "Unit G36 - EXPRESS – 14823",
            WarehouseAddressLine2.format(warehouse, "express", "14823"),
        )
    }

    /** The bug: SeaDrop used to render "SEADROP – 14823". */
    @Test
    fun `SeaDrop is SEA, never SEADROP`() {
        val line = WarehouseAddressLine2.format(warehouse, "seadrop", "14823")!!
        assertEquals("SEA", WarehouseAddressLine2.token(warehouse, "seadrop"))
        assert(!line.contains("SEADROP")) { "SeaDrop must render as SEA, got: $line" }
    }

    /** The other bug: the unit prefix was missing entirely. */
    @Test
    fun `the warehouse unit is included`() {
        assert(WarehouseAddressLine2.format(warehouse, "standard", "14823")!!.startsWith("Unit G36"))
    }

    @Test
    fun `server tokens win over the local fallback`() {
        val renamed = warehouse.copy(addressLine2Tokens = mapOf("seadrop" to "OCEAN"))
        assertEquals(
            "Unit G36 - OCEAN – 14823",
            WarehouseAddressLine2.format(renamed, "seadrop", "14823"),
        )
    }

    @Test
    fun `falls back to Laravel's tokens when the payload omits them`() {
        val bare = Warehouse(unit = "Unit G36")
        assertEquals(
            "Unit G36 - SEA – 14823",
            WarehouseAddressLine2.format(bare, "seadrop", "14823"),
        )
    }

    /** No account number => no line. Never invite a shipment without one. */
    @Test
    fun `absent account number yields no line`() {
        assertNull(WarehouseAddressLine2.format(warehouse, "standard", null))
        assertNull(WarehouseAddressLine2.format(warehouse, "standard", "   "))
    }

    @Test
    fun `missing unit degrades to token and account rather than a stray separator`() {
        assertEquals(
            "AIR – 14823",
            WarehouseAddressLine2.format(Warehouse(), "standard", "14823"),
        )
    }
}
