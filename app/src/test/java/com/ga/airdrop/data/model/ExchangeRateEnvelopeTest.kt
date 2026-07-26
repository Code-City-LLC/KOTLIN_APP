package com.ga.airdrop.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exchange-rate endpoint returns an ENVELOPE. `exchangeRates()` used to be
 * declared as returning a bare `ExchangeRate`, so the parser looked for
 * `exchange_rate` at the top level of `{success, data, meta}`, found nothing,
 * and every caller fell back to the hardcoded DEFAULT_USD_TO_JMD — the app
 * quoted 160.625 while the server said 162.00, on the cart and everywhere else.
 */
class ExchangeRateEnvelopeTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `the live envelope yields the server rate, not the fallback`() {
        val body = """{"success":true,"message":"Exchange rate retrieved successfully",""" +
            """"data":{"id":1,"exchange_rate":"162.00","formatted_rate":162.0},""" +
            """"meta":{"timestamp":"2026-07-25T20:41:06-04:00","version":"1.0"}}"""
        val envelope = json.decodeFromString(
            DataEnvelope.serializer(ExchangeRate.serializer()),
            body,
        )
        assertEquals(162.0, envelope.data!!.usdToJmd!!, 0.001)
    }
}
