package com.ga.airdrop.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CreateCheckoutRequestTest {

    private val json = Json { explicitNulls = false }

    @Test
    fun `hosted checkout serializes the canonical mobile return signal`() {
        val request = CreateCheckoutRequest(
            packageIds = listOf(7, 9),
            currency = "USD",
            isAuction = false,
            returnUrl = MOBILE_CHECKOUT_RETURN_URL,
        )

        val body = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals("airdrop://payment-success", body.getValue("return_url").jsonPrimitive.content)
        assertEquals("USD", body.getValue("currency").jsonPrimitive.content)
        assertEquals("false", body.getValue("is_auction").jsonPrimitive.content)
    }

    @Test
    fun `non-hosted payment requests omit the optional return signal`() {
        val request = CreateCheckoutRequest(
            packageIds = listOf(7),
            currency = "USD",
            isAuction = false,
        )

        val body = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertFalse(body.containsKey("return_url"))
    }
}
