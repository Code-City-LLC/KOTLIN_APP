package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CreateNcbSessionRequest
import com.ga.airdrop.data.model.NcbDeliveryLocation
import com.ga.airdrop.data.model.NcbSessionResponse
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The NCB/PowerTranz **wire contract**, tested against the types the NCB path
 * actually uses.
 *
 * ## Why this file exists
 *
 * A previous version of the suite carried tests labelled "NCB approve / decline
 * / 3DS" which all called `verifySession(...)`. That function is **Stripe-only**
 * — `PaymentReturnViewModel` routes it to `payments.checkoutSessionStatus` and
 * requires a Stripe session id. So those tests executed **zero NCB production
 * code** while reading as NCB coverage. BrightHarbor traced the call path and
 * caught it; they are deleted.
 *
 * The real rail is a different shape entirely:
 * ```
 * CreateNcbSessionRequest  -> POST payments/create-ncb-session
 * NcbSessionResponse       -> spi_token + redirect_data      (NOT status/payment_status)
 * completeNcbPayment(spi)  -> POST payments/ncb-complete-payment -> invoice_id
 * ```
 * This file pins that shape. It does **not** place an authorisation — no card
 * is sent anywhere, and a live sandbox charge is still owed by whoever owns the
 * gateway rail.
 *
 * ## The serialization trap this guards
 *
 * `AirdropJson` leaves `encodeDefaults` FALSE, so any property carrying a
 * default silently vanishes from the request body. On a payment request that
 * failure is invisible: the app compiles, the call returns, and the gateway
 * simply never receives the field.
 */
class NcbWireContractTest {

    private fun request(
        deliveryMode: String = "pickup",
        deliveryLocation: NcbDeliveryLocation? = null,
        pickupLocation: String? = "Kingston",
    ) = CreateNcbSessionRequest(
        packageIds = listOf(151611),
        currency = "JMD",
        isAuction = false,
        firstName = "Ada",
        lastName = "Lovelace",
        address = "1 Analytical Way",
        city = "Kingston",
        country = "JM",
        cardName = "Ada Lovelace",
        cardNumber = "4111111111111111",
        cardMonth = "12",
        cardYear = "2030",
        cardCvv = "123",
        deliveryMode = deliveryMode,
        deliveryLocation = deliveryLocation,
        pickupLocation = pickupLocation,
    )

    private fun encoded(r: CreateNcbSessionRequest) =
        AirdropJson.encodeToString(CreateNcbSessionRequest.serializer(), r)

    // ── The request actually reaching the wire ──────────────────────────────

    /**
     * ⚠️ Built with EMPTY strings on purpose, and that is the whole point.
     *
     * `AirdropJson` leaves `encodeDefaults` FALSE, so a property carrying a
     * default is omitted whenever its value EQUALS that default. A request built
     * with realistic values ("123", "Ada") therefore serializes fine even if
     * someone adds `= ""` to a required field — the bug hides until a customer
     * happens to submit the default value.
     *
     * I originally wrote this test with realistic values and claimed it guarded
     * the trap. It did not: injecting `card_cvv: String = ""` left all 7 tests
     * green. Using the default value as the fixture is what makes the guard real.
     */
    @Test
    fun `required fields survive serialization EVEN when they equal the type default`() {
        val json = AirdropJson.parseToJsonElement(
            encoded(
                request().copy(
                    cardCvv = "", cardNumber = "", cardName = "",
                    cardMonth = "", cardYear = "",
                    firstName = "", lastName = "", address = "", city = "", country = "",
                    currency = "", deliveryMode = "",
                ),
            ),
        ).jsonObject

        // Snake_case keys are the contract; a Kotlin rename that forgets
        // @SerialName produces a body the gateway silently ignores.
        listOf(
            "package_ids", "currency", "is_auction",
            "first_name", "last_name", "address", "city", "country",
            "card_name", "card_number", "card_month", "card_year", "card_cvv",
            "delivery_mode",
        ).forEach { key ->
            assertTrue(
                "'$key' MUST reach the wire — a card field that vanishes fails the " +
                    "authorisation with no client-side error at all",
                json.containsKey(key),
            )
        }
    }

    @Test
    fun `every field the gateway needs survives serialization`() {
        val json = AirdropJson.parseToJsonElement(encoded(request())).jsonObject
        listOf(
            "package_ids", "currency", "is_auction",
            "first_name", "last_name", "address", "city", "country",
            "card_name", "card_number", "card_month", "card_year", "card_cvv",
            "delivery_mode",
        ).forEach { key ->
            assertTrue("'$key' MUST reach the wire", json.containsKey(key))
        }
    }

    @Test
    fun `the card expiry is sent as Laravel requires it, not as the customer typed it`() {
        val json = AirdropJson.parseToJsonElement(encoded(request())).jsonObject
        // Laravel validates size:2 on the month and builds the year as 20+yy.
        // "1" instead of "01" is rejected server-side, which surfaces to the
        // customer as an unexplained decline.
        assertEquals("12", json["card_month"]?.jsonPrimitive?.content)
        assertEquals("2030", json["card_year"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a pickup order does NOT carry a delivery location`() {
        val json = AirdropJson.parseToJsonElement(encoded(request())).jsonObject
        assertEquals("pickup", json["delivery_mode"]?.jsonPrimitive?.content)
        assertFalse(
            "sending a delivery_location on a pickup order invites the server to " +
                "charge a delivery fee the customer never agreed to",
            json.containsKey("delivery_location"),
        )
    }

    @Test
    fun `a delivery order carries the coordinates the quote was based on`() {
        val json = AirdropJson.parseToJsonElement(
            encoded(
                request(
                    deliveryMode = "delivery",
                    deliveryLocation = NcbDeliveryLocation(
                        address = "10 Hope Rd, Kingston",
                        latitude = 18.0179,
                        longitude = -76.8099,
                    ),
                    pickupLocation = null,
                ),
            ),
        ).jsonObject
        assertEquals("delivery", json["delivery_mode"]?.jsonPrimitive?.content)
        val loc = json["delivery_location"]?.jsonObject
        assertNotNull("a delivery order without coordinates cannot be priced", loc)
        assertEquals(18.0179, loc!!["latitude"]!!.jsonPrimitive.content.toDouble(), 0.00001)
        assertEquals(-76.8099, loc["longitude"]!!.jsonPrimitive.content.toDouble(), 0.00001)
    }

    // ── The response the client must be able to act on ──────────────────────

    @Test
    fun `a session response decodes into a usable 3DS handoff`() {
        val decoded = AirdropJson.decodeFromString(
            NcbSessionResponse.serializer(),
            """{"spi_token":"spi_abc123","redirect_data":"<html><body><form/></body></html>"}""",
        )
        assertEquals("spi_abc123", decoded.spiToken)
        assertNotNull(decoded.redirectData)
        assertTrue(
            "redirect_data is an HTML BLOB, not a URL — NcbThreeDSScreen feeds it " +
                "to loadDataWithBaseURL. If it ever became a URL the 3DS page would " +
                "render as literal text.",
            decoded.redirectData!!.trimStart().startsWith("<"),
        )
    }

    @Test
    fun `a response missing the SPI token is not silently treated as usable`() {
        val decoded = AirdropJson.decodeFromString(
            NcbSessionResponse.serializer(),
            """{"redirect_data":"<html/>"}""",
        )
        assertNull(
            "no spi_token means completeNcbPayment has nothing to finalise with; " +
                "it must read as absent rather than as an empty-string token",
            decoded.spiToken,
        )
    }

    @Test
    fun `unknown server fields do not break decoding`() {
        // The gateway adds fields over time. A strict decoder would turn a
        // harmless addition into a failed payment.
        val decoded = AirdropJson.decodeFromString(
            NcbSessionResponse.serializer(),
            """{"spi_token":"spi_x","redirect_data":"<html/>","some_new_field":42}""",
        )
        assertEquals("spi_x", decoded.spiToken)
    }
}
