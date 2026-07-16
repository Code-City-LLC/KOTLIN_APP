package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.repo.normalizeCheckoutUserNote
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CreateCheckoutRequestTest {

    @Test
    fun `hosted checkout serializes the canonical mobile return signal`() {
        val request = CreateCheckoutRequest(
            packageIds = listOf(7, 9),
            currency = "USD",
            isAuction = false,
            returnUrl = MOBILE_CHECKOUT_RETURN_URL,
            userNote = normalizeCheckoutUserNote("  Leave at reception\n"),
        )

        val body = AirdropJson.parseToJsonElement(AirdropJson.encodeToString(request)).jsonObject
        assertEquals("airdrop://payment-success", body.getValue("return_url").jsonPrimitive.content)
        assertEquals("USD", body.getValue("currency").jsonPrimitive.content)
        assertEquals("false", body.getValue("is_auction").jsonPrimitive.content)
        assertEquals("Leave at reception", body.getValue("user_note").jsonPrimitive.content)
    }

    @Test
    fun `blank checkout note and optional return signal are omitted`() {
        val request = CreateCheckoutRequest(
            packageIds = listOf(7),
            currency = "USD",
            isAuction = false,
            userNote = normalizeCheckoutUserNote(" \n\t "),
        )

        val body = AirdropJson.parseToJsonElement(AirdropJson.encodeToString(request)).jsonObject
        assertFalse(body.containsKey("return_url"))
        assertFalse(body.containsKey("user_note"))
    }
}
