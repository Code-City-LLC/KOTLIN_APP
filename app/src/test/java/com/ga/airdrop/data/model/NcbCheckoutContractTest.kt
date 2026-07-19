package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the NCB PowerTranz (JMD) wire contract to Laravel
 * PaymentController::createNcbCheckout + Swift AirdropAPI. Money-critical:
 * the request is FLAT snake_case; the response carries spi_token/redirect_data/
 * checkout_id; complete carries invoice_id.
 */
class NcbCheckoutContractTest {

    @Test
    fun `create-ncb-session request serializes to the exact flat snake_case payload`() {
        val req = CreateNcbSessionRequest(
            packageIds = listOf(101, 102),
            currency = "JMD",
            isAuction = false,
            firstName = "Tam",
            lastName = "Zid",
            address = "1 Test St",
            city = "Kingston",
            country = "JM",
            cardName = "TAM ZID",
            cardNumber = "4111111111111111",
            cardMonth = "12",
            cardYear = "2030",
            cardCvv = "123",
            deliveryMode = "pickup",
            pickupLocation = "Kingston",
            deliveryChargeTotal = 0.0,
            deliveryChargeCurrency = "JMD",
        )
        val obj = AirdropJson.encodeToString(CreateNcbSessionRequest.serializer(), req)
            .let { AirdropJson.parseToJsonElement(it).jsonObject }

        assertEquals(
            listOf("101", "102"),
            obj["package_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("JMD", obj["currency"]!!.jsonPrimitive.content)
        assertEquals("false", obj["is_auction"]!!.jsonPrimitive.content)
        assertEquals("Tam", obj["first_name"]!!.jsonPrimitive.content)
        assertEquals("Zid", obj["last_name"]!!.jsonPrimitive.content)
        assertEquals("Kingston", obj["city"]!!.jsonPrimitive.content)
        assertEquals("JM", obj["country"]!!.jsonPrimitive.content)
        assertEquals("TAM ZID", obj["card_name"]!!.jsonPrimitive.content)
        assertEquals("4111111111111111", obj["card_number"]!!.jsonPrimitive.content)
        assertEquals("12", obj["card_month"]!!.jsonPrimitive.content)
        assertEquals("2030", obj["card_year"]!!.jsonPrimitive.content)
        assertEquals("123", obj["card_cvv"]!!.jsonPrimitive.content)
        assertEquals("pickup", obj["delivery_mode"]!!.jsonPrimitive.content)
        assertEquals("Kingston", obj["pickup_location"]!!.jsonPrimitive.content)
        assertEquals("JMD", obj["delivery_charge_currency"]!!.jsonPrimitive.content)
        // camelCase keys must NOT leak onto the wire.
        assertNull(obj["packageIds"])
        assertNull(obj["cardNumber"])
        assertNull(obj["firstName"])
    }

    @Test
    fun `create-ncb-session response parses spi_token, redirect_data, checkout_id`() {
        val json = """{"spi_token":"spi_abc","redirect_data":"<html>3ds</html>","checkout_id":"co_9"}"""
        val res = AirdropJson.decodeFromString(NcbSessionResponse.serializer(), json)
        assertEquals("spi_abc", res.spiToken)
        assertEquals("<html>3ds</html>", res.redirectData)
        assertEquals("co_9", res.checkoutId)
    }

    @Test
    fun `ncb-complete-payment round-trips spi_token and reads invoice_id`() {
        val body = AirdropJson.encodeToString(
            NcbCompletePaymentRequest.serializer(),
            NcbCompletePaymentRequest(spiToken = "spi_abc"),
        )
        assertEquals("spi_abc", AirdropJson.parseToJsonElement(body).jsonObject["spi_token"]!!.jsonPrimitive.content)

        val res = AirdropJson.decodeFromString(
            NcbCompletePaymentResponse.serializer(),
            """{"invoice_id":"5512"}""", // FlexibleInt tolerates string
        )
        assertEquals(5512, res.invoiceId)
    }
}
