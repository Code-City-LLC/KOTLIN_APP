package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-09-23 SwiftHawk — create-ncb-session REQUIRES `city`
 * (PaymentController: 'city' => 'required|string|max:100'). The NCB card-entry
 * screen has no City field; the city comes from Profile Information. But its
 * Country and State dropdowns reused the Profile helpers, which blank the city
 * on a change. One tap on either dropdown and the server refused the JMD
 * payment. Swift keeps the profile's city on its card screen.
 */
class NcbCardBillingCityTest {

    private val jamaica = CartBillingForm(
        firstName = "Jane",
        lastName = "Doe",
        currency = "JMD",
        address1 = "22 Paradise Ave",
        state = "St. James",
        city = "Montego Bay",
        country = "Jamaica",
    )

    @Test
    fun `changing the billing country on the card screen keeps the city`() {
        assertEquals("Montego Bay", ncbCardFormWithCountry(jamaica, "United States").city)
    }

    @Test
    fun `changing the billing state on the card screen keeps the city`() {
        assertEquals("Montego Bay", ncbCardFormWithState(jamaica, "St. Ann").city)
    }

    @Test
    fun `Profile Information still clears a city that belonged to the old state`() {
        assertEquals("", checkoutFormWithState(jamaica, "St. Ann").city)
    }
}
