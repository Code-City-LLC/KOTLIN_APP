package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.model.NcbDeliveryLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports of Swift's NCB checkout validation (FigmaCartViewController
 * validatedNcbCheckoutInput/parseExpiry/ncbCountryCode) and the 3DS
 * callback matcher — the money rules that must not drift.
 */
class NcbCheckoutLogicTest {

    private val billing = CartBillingForm(
        firstName = "Tamzid",
        lastName = "Customer",
        currency = "JMD",
        address1 = "22 Paradise Ave",
        address2 = "",
        state = "Florida",
        city = "Kingston",
        country = "Jamaica",
        postal = "12345",
    )

    private val card = NcbCardFields(
        cardName = "Joshua Ricketts",
        cardNumber = "4111 1111 1111 1111",
        expiry = "12/30",
        cvv = "123",
    )

    private fun build(
        billing: CartBillingForm = this.billing,
        card: NcbCardFields = this.card,
        packageIds: List<Int> = listOf(11, 12),
        deliveryMode: String? = "pickup",
        deliveryAddress: String? = null,
        deliveryLat: Double? = null,
        deliveryLng: Double? = null,
        pickup: String? = "Kingston",
        fee: Double? = null,
        feeCurrency: String? = null,
    ) = buildNcbCheckoutInput(
        packageIds = packageIds,
        isAuction = true,
        billing = billing,
        card = card,
        deliveryMode = deliveryMode,
        deliveryAddress = deliveryAddress,
        deliveryLatitude = deliveryLat,
        deliveryLongitude = deliveryLng,
        pickupLocation = pickup,
        deliveryFee = fee,
        deliveryFeeCurrency = feeCurrency,
        currentYear = 2026,
    )

    /* ── expiry ───────────────────────────────────────────────────────── */

    @Test
    fun `expiry accepts MMYY and MMYYYY and rejects the past`() {
        assertEquals("02" to "2027", parseNcbExpiry("02/27", 2026))
        // Swift requires the zero-padded month — "2/27" is 3 digits, rejected.
        assertNull(parseNcbExpiry("2/27", 2026))
        assertEquals("12" to "2030", parseNcbExpiry("12/30", 2026))
        assertEquals("07" to "2029", parseNcbExpiry("07/2029", 2026))
        assertNull(parseNcbExpiry("13/30", 2026))
        assertNull(parseNcbExpiry("00/30", 2026))
        assertNull(parseNcbExpiry("12/25", 2026))
        assertNull(parseNcbExpiry("1230", 2031))
        assertNull(parseNcbExpiry("12", 2026))
    }

    /* ── country ──────────────────────────────────────────────────────── */

    @Test
    fun `country resolves display names and codes, JM and US only`() {
        assertEquals("JM", resolveNcbCountryCode("Jamaica"))
        assertEquals("US", resolveNcbCountryCode("United States"))
        assertEquals("JM", resolveNcbCountryCode("jm"))
        assertEquals("US", resolveNcbCountryCode("US"))
        assertNull(resolveNcbCountryCode("Canada"))
        assertNull(resolveNcbCountryCode(""))
    }

    /* ── name parts ───────────────────────────────────────────────────── */

    @Test
    fun `billing names win, else the card name splits`() {
        assertEquals("Tamzid" to "Customer", resolveNcbNameParts("J R", "Tamzid", "Customer"))
        assertEquals("Joshua" to "Ricketts", resolveNcbNameParts("Joshua Ricketts", "", ""))
        assertEquals(
            "Joshua" to "St. Ricketts Jr",
            resolveNcbNameParts("Joshua St. Ricketts Jr", "", ""),
        )
        assertEquals("Cher" to "Cher", resolveNcbNameParts("Cher", "", ""))
    }

    /* ── full build ───────────────────────────────────────────────────── */

    @Test
    fun `a valid pickup build produces the exact request`() {
        val input = build()
        assertTrue(input is NcbCheckoutInput.Ready)
        val request = (input as NcbCheckoutInput.Ready).request
        assertEquals(listOf(11, 12), request.packageIds)
        assertEquals("JMD", request.currency)
        assertTrue(request.isAuction)
        assertEquals("Tamzid", request.firstName)
        assertEquals("Customer", request.lastName)
        assertEquals("22 Paradise Ave", request.address)
        assertEquals("Kingston", request.city)
        assertEquals("JM", request.country)
        assertEquals("4111111111111111", request.cardNumber)
        assertEquals("12", request.cardMonth)
        assertEquals("2030", request.cardYear)
        assertEquals("123", request.cardCvv)
        assertEquals("pickup", request.deliveryMode)
        assertNull(request.deliveryLocation)
        assertEquals("Kingston", request.pickupLocation)
    }

    @Test
    fun `a delivery build requires and carries the delivery location`() {
        val incomplete = build(deliveryMode = "delivery")
        assertTrue(incomplete is NcbCheckoutInput.Invalid)

        val input = build(
            deliveryMode = "delivery",
            deliveryAddress = "Spanish Town, Jamaica",
            deliveryLat = 18.0179,
            deliveryLng = -76.9852,
            pickup = null,
            fee = 1200.0,
            feeCurrency = "jmd",
        )
        assertTrue(input is NcbCheckoutInput.Ready)
        val request = (input as NcbCheckoutInput.Ready).request
        assertEquals(
            NcbDeliveryLocation("Spanish Town, Jamaica", 18.0179, -76.9852),
            request.deliveryLocation,
        )
        assertEquals(1200.0, request.deliveryChargeTotal)
        assertEquals("JMD", request.deliveryChargeCurrency)
    }

    @Test
    fun `each broken field yields its Swift error`() {
        fun msg(input: NcbCheckoutInput): String =
            (input as NcbCheckoutInput.Invalid).message

        assertEquals("Your cart is empty.", msg(build(packageIds = emptyList())))
        assertEquals(
            "Enter the cardholder name.",
            msg(build(card = card.copy(cardName = "  "))),
        )
        assertEquals(
            "Enter a valid card number.",
            msg(build(card = card.copy(cardNumber = "4111"))),
        )
        assertEquals(
            "Enter the expiry date as MM/YY.",
            msg(build(card = card.copy(expiry = "12/20"))),
        )
        assertEquals("Enter a valid CVV.", msg(build(card = card.copy(cvv = "12"))))
        assertEquals(
            "Enter the billing address.",
            msg(build(billing = billing.copy(address1 = ""))),
        )
        assertEquals(
            "City is required from Profile Information before JMD checkout.",
            msg(build(billing = billing.copy(city = ""))),
        )
        assertEquals(
            "NCB checkout currently supports Jamaica and United States billing addresses.",
            msg(build(billing = billing.copy(country = "Canada"))),
        )
        assertTrue(msg(build(deliveryMode = null)).contains("pickup or delivery"))
    }

    /* ── 3DS callback matcher ─────────────────────────────────────────── */

    @Test
    fun `callback matches path markers and spi_token params only`() {
        assertTrue(isNcbThreeDsCallback("https://x.test/payments/ncb-3ds-callback"))
        assertTrue(isNcbThreeDsCallback("https://x.test/a?b=1&NCB_3DS_CALLBACK=1"))
        assertTrue(isNcbThreeDsCallback("https://x.test/return?spi_token=abc"))
        assertTrue(isNcbThreeDsCallback("https://x.test/return?SpiToken=abc"))
        assertTrue(isNcbThreeDsCallback("https://x.test/return?spi-token=abc"))
        assertFalse(isNcbThreeDsCallback("https://x.test/acs/challenge"))
        assertFalse(isNcbThreeDsCallback("https://x.test/?token=abc"))
        assertFalse(isNcbThreeDsCallback(null))
        assertFalse(isNcbThreeDsCallback(""))
    }

    @Test
    fun `web base url strips the api suffix`() {
        assertEquals(
            "https://pre-staging.airdropja.com",
            ncbWebBaseUrl("https://pre-staging.airdropja.com/api/v1"),
        )
        assertEquals(
            "https://app.airdropja.com",
            ncbWebBaseUrl("https://app.airdropja.com/api/v1/"),
        )
    }
}
