package com.ga.airdrop.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeliveryCenterRouteTest {

    @Test
    fun routeCarriesOptionalRefAndPositivePackageId() {
        assertEquals(
            "deliveryCenter?ref={ref}&packageId={packageId}&packageIds={packageIds}",
            Routes.DELIVERY_CENTER,
        )
        assertEquals("deliveryCenter?ref=&packageId=&packageIds=", Routes.deliveryCenter())
        assertEquals(
            "deliveryCenter?ref=&packageId=41&packageIds=",
            Routes.deliveryCenter(packageId = 41),
        )
        assertEquals("deliveryCenter?ref=&packageId=&packageIds=", Routes.deliveryCenter(packageId = 0))
        assertEquals(
            "deliveryCenter?ref=INV-100238&packageId=&packageIds=",
            Routes.deliveryCenter(ref = "INV-100238"),
        )
        assertFalse(Routes.deliveryCenter(packageId = 41).contains("payment", ignoreCase = true))
        assertFalse(Routes.deliveryCenter(packageId = 41).contains("session", ignoreCase = true))
    }

    /**
     * The just-paid package ids ride the route because the store that knows
     * about a checkout is cleared the moment it commits — the ids have to
     * survive past that, and a route argument is the only thing here that does.
     */
    @Test
    fun theJustPaidPackageIdsSurviveTheRoundTrip() {
        val route = Routes.deliveryCenter(ref = "INV-1", packageIds = listOf(153901, 153902))
        assertEquals("deliveryCenter?ref=INV-1&packageId=&packageIds=153901,153902", route)
        assertEquals(listOf(153901, 153902), Routes.decodePackageIds("153901,153902"))
    }

    /**
     * A malformed argument must never become a request for package 0 or a
     * crash. Blanks, junk, negatives and duplicates are all dropped rather
     * than propagated into an API call.
     */
    @Test
    fun malformedPackageIdArgumentsAreDroppedNotPropagated() {
        assertEquals(emptyList<Int>(), Routes.decodePackageIds(null))
        assertEquals(emptyList<Int>(), Routes.decodePackageIds(""))
        assertEquals(emptyList<Int>(), Routes.decodePackageIds("abc,,-4,0"))
        assertEquals(listOf(7), Routes.decodePackageIds("7,7,junk,0"))
        assertEquals("", Routes.encodePackageIds(listOf(0, -1)))
    }
}
