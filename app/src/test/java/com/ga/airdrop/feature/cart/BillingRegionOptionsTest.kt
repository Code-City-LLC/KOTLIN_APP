package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.location.CountryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingRegionOptionsTest {

    @Test
    fun `Jamaica exposes all fourteen parishes and parish towns`() {
        val parishes = CountryCatalog.stateOptions("Jamaica")

        assertEquals(14, parishes.size)
        assertTrue("Saint James" in parishes)
        assertEquals(
            listOf("Montego Bay", "Cambridge", "Anchovy", "Adelphi"),
            CountryCatalog.cityOptions("Jamaica", "Saint James"),
        )
        assertEquals("Saint James", CountryCatalog.canonicalState("Jamaica", "St James"))
    }

    @Test
    fun `blank Jamaica parish exposes the complete deduplicated town catalog`() {
        val towns = CountryCatalog.cityOptions("JM")

        assertTrue("Kingston" in towns)
        assertTrue("Montego Bay" in towns)
        assertEquals(towns.distinct(), towns)
    }

    @Test
    fun `United States exposes fifty states plus DC and leaves city free text`() {
        val states = CountryCatalog.stateOptions("US")

        assertEquals(51, states.size)
        assertTrue("District of Columbia" in states)
        assertTrue(CountryCatalog.cityOptions("United States", "Florida").isEmpty())
    }

    @Test
    fun `JMD defaults to Jamaica and loaded aliases are sanitized without data loss`() {
        assertEquals("Jamaica", CountryCatalog.defaultCountryNameForPaymentCurrency("JMD"))
        val form = sanitizeCheckoutBillingForm(
            CartBillingForm(
                currency = "JMD",
                country = "",
                state = "St James",
                city = "Montego Bay",
            ),
        )

        assertEquals("Jamaica", form.country)
        assertEquals("Saint James", form.state)
        assertEquals("Montego Bay", form.city)
    }

    @Test
    fun `country and parish changes clear incompatible dependent fields`() {
        val jamaica = CartBillingForm(
            country = "Jamaica",
            state = "Saint James",
            city = "Montego Bay",
        )

        val us = checkoutFormWithCountry(jamaica, CountryCatalog.displayNameFor("United States"))
        assertEquals("United States", us.country)
        assertEquals("", us.state)
        assertEquals("", us.city)

        val changedParish = checkoutFormWithState(jamaica, "Kingston")
        assertEquals("Kingston", changedParish.state)
        assertEquals("", changedParish.city)
    }
}
