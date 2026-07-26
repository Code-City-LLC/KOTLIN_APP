package com.ga.airdrop.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GET /exchange-rates decoding.
 *
 * The model used to read ONLY `usd_to_jmd`, a key the server does not send.
 * It therefore always decoded to null, the repo raised "Missing exchange rate",
 * and every screen silently fell back to the hardcoded ExchangeRateStore
 * default — the global rate had never once loaded. These pin the real payload.
 */
class ExchangeRateDecodeTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Verbatim `data` object from pre-staging and production (2026-07-25). */
    @Test
    fun `decodes the live body where the rate is a STRING`() {
        val rate = json.decodeFromString(
            ExchangeRate.serializer(),
            """{"id":1,"exchange_rate":"162.00","formatted_rate":162.0}""",
        )
        assertEquals(162.0, rate.usdToJmd!!, 0.001)
    }

    @Test
    fun `falls back to formatted_rate when exchange_rate is absent`() {
        val rate = json.decodeFromString(
            ExchangeRate.serializer(),
            """{"id":1,"formatted_rate":158.5}""",
        )
        assertEquals(158.5, rate.usdToJmd!!, 0.001)
    }

    /** An older payload spelling must still decode rather than regress. */
    @Test
    fun `still accepts the legacy usd_to_jmd spelling`() {
        val rate = json.decodeFromString(
            ExchangeRate.serializer(),
            """{"usd_to_jmd":161.0}""",
        )
        assertEquals(161.0, rate.usdToJmd!!, 0.001)
    }

    /** A zero or missing rate must stay null so callers keep their fallback. */
    @Test
    fun `zero and empty are treated as no rate`() {
        assertNull(json.decodeFromString(ExchangeRate.serializer(), """{"exchange_rate":"0"}""").usdToJmd)
        assertNull(json.decodeFromString(ExchangeRate.serializer(), """{"id":1}""").usdToJmd)
    }
}
