package com.ga.airdrop.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeliveryCenterRouteTest {

    @Test
    fun routeCarriesOptionalRefAndPositivePackageId() {
        assertEquals("deliveryCenter?ref={ref}&packageId={packageId}", Routes.DELIVERY_CENTER)
        assertEquals("deliveryCenter?ref=&packageId=", Routes.deliveryCenter())
        assertEquals("deliveryCenter?ref=&packageId=41", Routes.deliveryCenter(packageId = 41))
        assertEquals("deliveryCenter?ref=&packageId=", Routes.deliveryCenter(packageId = 0))
        assertEquals(
            "deliveryCenter?ref=INV-100238&packageId=",
            Routes.deliveryCenter(ref = "INV-100238"),
        )
        assertFalse(Routes.deliveryCenter(packageId = 41).contains("payment", ignoreCase = true))
        assertFalse(Routes.deliveryCenter(packageId = 41).contains("session", ignoreCase = true))
    }
}
