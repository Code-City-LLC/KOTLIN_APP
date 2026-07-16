package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckoutProfileValidationTest {

    private fun complete(country: String, postal: String = "") = CartBillingForm(
        firstName = "Jane",
        lastName = "Doe",
        currency = "JMD",
        address1 = "1 Main Street",
        state = "State",
        city = "City",
        country = country,
        postal = postal,
    )

    @Test
    fun `only United States requires postal code`() {
        assertEquals("Please enter ZIP Code", validateCheckoutProfile(complete("United States")))
        assertNull(validateCheckoutProfile(complete("United States", "10001")))
        assertNull(validateCheckoutProfile(complete("Canada")))
        assertNull(validateCheckoutProfile(complete("United Kingdom")))
        assertNull(validateCheckoutProfile(complete("Jamaica")))
    }

    @Test
    fun `hosted checkout URL requires https and a real host`() {
        assertEquals(
            "https://checkout.airdropja.test/session",
            validatedHostedCheckoutUrl(" https://checkout.airdropja.test/session "),
        )
        assertNull(validatedHostedCheckoutUrl("http://checkout.airdropja.test/session"))
        assertNull(validatedHostedCheckoutUrl("https:///missing-host"))
        assertNull(validatedHostedCheckoutUrl("https://user:secret@checkout.airdropja.test/session"))
    }
}
