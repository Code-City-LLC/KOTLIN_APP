package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.model.CartPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartServerGatewayTest {

    @Test
    fun `canonical kg wins over pounds and legacy display weight`() {
        val line = CartPackage(
            id = 1,
            weightKg = 3.5,
            weightLbs = 99.0,
            weight = 88.0,
        ).toCartLine()

        assertEquals(3.5, line.weightKg!!, 0.0)
    }

    @Test
    fun `canonical pounds converts to kilograms`() {
        val line = CartPackage(id = 2, weightLbs = 10.0).toCartLine()

        assertEquals(4.5359237, line.weightKg!!, 1e-9)
    }

    @Test
    fun `legacy weight falls back as pounds and converts to kilograms`() {
        val line = CartPackage(id = 3, weight = 10.0).toCartLine()

        assertEquals(4.5359237, line.weightKg!!, 1e-9)
    }

    @Test
    fun `charges use exact server precedence and missing status remains unknown`() {
        val line = CartPackage(
            id = 4,
            shippingCost = 1.0,
            additionalCharges = 2.0,
            additionalChargesTotal = 3.0,
            totalCharges = 4.0,
            status = null,
        ).toCartLine()

        assertEquals(4.0, line.priceUsd, 0.0)
        assertNull(line.statusCode)
        assertTrue(line.serverConfirmed)
    }

    @Test
    fun `ready for pickup name restores status seven when cart payload omits code`() {
        val line = CartPackage(
            id = 5,
            status = null,
            statusName = "  rEaDy FoR pIcKuP  ",
        ).toCartLine()

        assertEquals(7, line.statusCode)
        assertTrue(line.isCheckoutEligible())
    }

    @Test
    fun `explicit status code remains authoritative over display name`() {
        val line = CartPackage(
            id = 6,
            status = 18,
            statusName = "Ready for Pickup",
        ).toCartLine()

        assertEquals(18, line.statusCode)
        assertTrue(!line.isCheckoutEligible())
    }

    @Test
    fun `non-ready status name never synthesizes a cart status`() {
        val line = CartPackage(id = 7, statusName = "Delivered").toCartLine()

        assertNull(line.statusCode)
        assertTrue(!line.isCheckoutEligible())
    }

    @Test
    fun `blank status name remains unknown`() {
        val line = CartPackage(id = 8, statusName = "   ").toCartLine()

        assertNull(line.statusCode)
        assertTrue(!line.isCheckoutEligible())
    }

    @Test
    fun `unknown and fuzzy status names remain unknown`() {
        listOf("Unknown", "Ready for Pickup at Kingston", "Ready").forEachIndexed { index, name ->
            val line = CartPackage(id = 9 + index, statusName = name).toCartLine()

            assertNull(name, line.statusCode)
            assertTrue(name, !line.isCheckoutEligible())
        }
    }
}
