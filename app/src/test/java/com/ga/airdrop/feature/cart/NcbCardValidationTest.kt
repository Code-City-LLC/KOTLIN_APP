package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the NCB card form to the LARAVEL contract, not to the UI formatter.
 *
 * `Api\V1\PaymentController::createNcbCheckout` validates:
 *   card_month  size:2      card_year size:4
 *   card_number 13..19      card_cvv  3..4
 *
 * The submit path sends `expiry.substringBefore("/")` verbatim, so a one-digit
 * month reaches Laravel as "5" and 422s — at the very last step of a JMD
 * checkout, with the customer's card already entered. Until now nothing here
 * checked the month length; the form was correct only because `formatExpiry`
 * happens to emit `substring(0, 2) + "/"`. These tests make the guarantee the
 * validator's, so a change to the formatter fails here instead of in payments.
 */
class NcbCardValidationTest {

    private val goodNumber = "4111111111111111"

    @Test
    fun `a well formed card passes`() {
        assertNull(validateNcbCard("Tamzid Khan", goodNumber, "05/29", "123"))
        assertNull("4-digit CVV (Amex) is valid", validateNcbCard("T K", goodNumber, "12/30", "1234"))
    }

    @Test
    fun `a single digit month is rejected because Laravel requires size 2`() {
        // "5/29" would be sent as card_month="5" and 422 server-side.
        assertEquals(
            "Enter the expiry as MM/YY.",
            validateNcbCard("Tamzid Khan", goodNumber, "5/29", "123"),
        )
    }

    @Test
    fun `a single digit year is rejected because card_year is built as 20 plus yy`() {
        // "05/9" would become card_year="209" — 3 chars, not size:4.
        assertEquals(
            "Enter the expiry as MM/YY.",
            validateNcbCard("Tamzid Khan", goodNumber, "05/9", "123"),
        )
    }

    @Test
    fun `an expiry with no separator is rejected`() {
        assertEquals(
            "Enter the expiry as MM/YY.",
            validateNcbCard("Tamzid Khan", goodNumber, "0529", "123"),
        )
    }

    @Test
    fun `month must be a real month`() {
        assertEquals(
            "Enter the expiry as MM/YY.",
            validateNcbCard("Tamzid Khan", goodNumber, "00/29", "123"),
        )
        assertEquals(
            "Enter the expiry as MM/YY.",
            validateNcbCard("Tamzid Khan", goodNumber, "13/29", "123"),
        )
    }

    @Test
    fun `card number bounds match Laravel 13 to 19`() {
        assertEquals(
            "Enter a valid card number.",
            validateNcbCard("T K", "4".repeat(12), "05/29", "123"),
        )
        assertNull(validateNcbCard("T K", "4".repeat(13), "05/29", "123"))
        assertNull(validateNcbCard("T K", "4".repeat(19), "05/29", "123"))
        assertEquals(
            "Enter a valid card number.",
            validateNcbCard("T K", "4".repeat(20), "05/29", "123"),
        )
    }

    @Test
    fun `cvv bounds match Laravel 3 to 4`() {
        assertEquals("Enter a valid CVV.", validateNcbCard("T K", goodNumber, "05/29", "12"))
        assertEquals("Enter a valid CVV.", validateNcbCard("T K", goodNumber, "05/29", "12345"))
    }

    @Test
    fun `cardholder name is required`() {
        assertEquals("Enter the cardholder name.", validateNcbCard("   ", goodNumber, "05/29", "123"))
    }
}
