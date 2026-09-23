package com.ga.airdrop.feature.calculator

import com.ga.airdrop.data.api.AirdropJson
import java.util.ArrayDeque
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transport-level proof for the production calculator repository.
 *
 * ViewModel fakes prove selection behavior, but cannot detect a wrong URL,
 * query parameter, response envelope, or serialized request body. These tests
 * execute [RemoteCalculatorRepository] itself through its injected OkHttp
 * client, without making a network request.
 */
class RemoteCalculatorRepositoryTransportTest {

    @Test
    fun `duty search uses the customs endpoint and maps only selectable rows`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue(
                """
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"id":"42","item_name":"Laptop computer","duty_percentage":"20.0"},
                      {"item_name":"Missing id","duty_percentage":10},
                      {"id":7,"duty_percentage":12.5},
                      {"id":8,"item_name":"Unrated laptop","duty_percentage":null},
                      {"id":0,"item_name":"Laptop ghost"},
                      {"id":-2,"item_name":"Laptop invalid"},
                      {"id":9,"item_name":"   "}
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
        val repository = repository(transport)

        val rates = repository.searchDutyRates(query = "  laptop  ", limit = 8)

        assertEquals(2, rates.size)
        assertEquals(CalcDutyRate(42, "Laptop computer", 20.0), rates[0])
        assertEquals(CalcDutyRate(8, "Unrated laptop", null), rates[1])

        val request = transport.singleRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/custom-duty-rates", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("1000", request.url.queryParameter("per_page"))
        assertEquals("1", request.url.queryParameter("active_only"))
        assertNull("the full catalogue is filtered locally", request.url.queryParameter("search"))
        assertNull("the auction catalogue must not leak into this request", request.url.queryParameter("in_stock"))
    }

    @Test
    fun `catalogue ranks exact then prefix then substring matches and reuses the load`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue("""[{"id":404,"item_name":"NOTEBOOK"},{"id":60,"item_name":"BOOK"},{"id":61,"item_name":"BOOK SHELF"},{"id":126,"item_name":"CHECK BOOK"},{"id":8,"item_name":"BOOK"},{"id":12,"item_name":"LAPTOP"}]""")
        }
        val repository = repository(transport)
        assertEquals(listOf(8, 60, 61, 126, 404), repository.searchDutyRates("  Book  ").map { it.id })
        assertEquals(listOf(12), repository.searchDutyRates("LAP").map { it.id })
        assertEquals(listOf(8, 60), repository.searchDutyRates("book", limit = 2).map { it.id })
        assertTrue(repository.searchDutyRates("unmatched").isEmpty())
        assertEquals(1, transport.requestCount)
    }

    @Test
    fun `default search retains matches beyond the old page and row caps`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue((1..30).joinToString(prefix = "[", postfix = "]") {
                """{"id":$it,"item_name":"Book $it"}"""
            })
        }
        val rates = repository(transport).searchDutyRates("book")
        assertEquals(30, rates.size)
        assertEquals((1..30).toSet(), rates.map { it.id }.toSet())
    }

    @Test
    fun `concurrent queries share one successful catalogue request`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue("""[{"id":60,"item_name":"BOOK"},{"id":12,"item_name":"LAPTOP"}]""")
        }
        val repository = repository(transport)
        val results = listOf("book", "laptop").map { query ->
            async { repository.searchDutyRates(query) }
        }.awaitAll()
        assertEquals(listOf(listOf(60), listOf(12)), results.map { rows -> rows.map { it.id } })
        assertEquals(1, transport.requestCount)
    }

    @Test
    fun `failed and malformed catalogue loads are errors and can be retried`() = runBlocking {
        for ((body, code) in listOf("{}" to 503, "not json" to 200, "{}" to 200)) {
            val transport = RecordingTransport().apply {
                enqueue(body, code)
                enqueue("""{"data":{"items":[{"id":60,"item_name":"BOOK"}]}}""")
            }
            val repository = repository(transport)
            val failure = runCatching { repository.searchDutyRates("book") }.exceptionOrNull()
            assertTrue("failed catalogue must not become empty success", failure is IOException)
            assertEquals(listOf(60), repository.searchDutyRates("book").map { it.id })
            assertEquals(2, transport.requestCount)
        }
    }

    @Test
    fun `short queries do not load and separate screen repositories do not share cache`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue("[]")
            enqueue("""[{"id":60,"item_name":"BOOK"}]""")
        }
        val first = repository(transport)
        assertTrue(first.searchDutyRates("bo").isEmpty())
        assertEquals(0, transport.requestCount)
        assertTrue(first.searchDutyRates("book").isEmpty())
        assertTrue(first.searchDutyRates("book").isEmpty())
        assertEquals(listOf(60), repository(transport).searchDutyRates("book").map { it.id })
        assertEquals(2, transport.requestCount)
    }

    @Test
    fun `shipping quote serializes the selected rate id and never a raw percentage`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue(
                """
                {
                  "success": true,
                  "data": {
                    "shipping_method": "airdrop_standard",
                    "breakdown": {
                      "freight": 10,
                      "insurance": 2,
                      "fuel_surcharge": 1,
                      "airdrop_charges": 13,
                      "customs_duty": 20,
                      "grand_total": 33
                    },
                    "calculations": {"cif_value": 120, "total_weight_lbs": 5}
                  }
                }
                """.trimIndent(),
            )
        }
        val repository = repository(transport)

        val result = repository.calculateShipment(
            shippingMethod = "airdrop_standard",
            invoiceAmount = 100.0,
            weightLbs = 5.0,
            numberOfPackages = 1,
            customDutyRateId = 42,
        )

        assertEquals(33.0, result.totalWithDuty, 0.0)
        val request = transport.singleRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/shipping/calculate", request.url.encodedPath)

        val body = AirdropJson.parseToJsonElement(request.bodyText.orEmpty()) as JsonObject
        assertEquals("42", body["custom_duty_rate_id"]?.jsonPrimitive?.content)
        assertFalse(
            "mobile identifies the server-owned rate; it must not author a percentage",
            body.containsKey("custom_duty_percentage"),
        )
    }

    @Test
    fun `tier quote uses the published Airdrop contract and preserves server line items`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue(
                """
                {
                  "success": true,
                  "data": {
                    "quote_reference": "Q-7H2K9M4P6R8T",
                    "customer_tier": "SAVR",
                    "method": "AIR",
                    "destination": "JM",
                    "currency": "USD",
                    "line_items": [
                      {"code":"base_shipping","label":"Base shipping","amount":"20.00"},
                      {"code":"fuel_surcharge","label":"Fuel surcharge","amount":2},
                      {"code":"insurance","label":"Insurance","amount":1.5}
                    ],
                    "subtotal": "23.50",
                    "total_due": 23.5,
                    "status": "active",
                    "is_expired": false,
                    "expires_at": "2099-01-02T03:04:05Z",
                    "insurance_options": {
                      "insured_value": 150,
                      "rate_per_100": 1,
                      "block_size": 100,
                      "blocks": 2,
                      "premium": 1.5,
                      "covered_value": 150,
                      "can_decline": true,
                      "mandatory": false,
                      "explicit_required": true
                    },
                    "insurance_choice_required": true,
                    "aircoins_earned": 4
                  }
                }
                """.trimIndent(),
            )
        }
        val repository = repository(transport)

        val quote = repository.quoteShipment(
            TierQuoteRequest(
                weightLbs = 5.5,
                method = "AIR",
                declaredValue = 150.0,
                insuredValue = 150.0,
                itemName = "Laptop",
            ),
        )

        assertEquals("Q-7H2K9M4P6R8T", quote.quoteReference)
        assertEquals(23.5, quote.totalDue, 0.0)
        assertEquals(listOf("base_shipping", "fuel_surcharge", "insurance"), quote.lineItems.map { it.code })
        assertTrue(quote.insuranceChoiceRequired)
        assertTrue(quote.insuranceOptions!!.canDecline)

        val request = transport.singleRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/shipments/quote", request.url.encodedPath)
        val body = AirdropJson.parseToJsonElement(request.bodyText.orEmpty()) as JsonObject
        assertEquals("5.5", body["weight"]?.jsonPrimitive?.content)
        assertEquals("AIR", body["method"]?.jsonPrimitive?.content)
        assertEquals("150.0", body["declared_value"]?.jsonPrimitive?.content)
        assertEquals("150.0", body["insured_value"]?.jsonPrimitive?.content)
        assertEquals("Laptop", body["item_name"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("custom_duty_rate_id"))
        assertFalse(body.containsKey("custom_duty_percentage"))
        assertFalse(body.containsKey("number_of_packages"))
    }

    @Test
    fun `tier quote preserves Laravel's machine-readable failure without legacy fallback`() = runBlocking {
        val transport = RecordingTransport().apply {
            enqueue(
                """{"success":false,"message":"No active rate card for this method/destination","error_code":"NO_RATE_CARD"}""",
                code = 422,
            )
        }
        val repository = repository(transport)

        val error = runCatching {
            repository.quoteShipment(TierQuoteRequest(weightLbs = 5.5, method = "AIR"))
        }.exceptionOrNull()

        assertTrue(error is TierQuoteException)
        assertEquals("NO_RATE_CARD", (error as TierQuoteException).errorCode)
        assertEquals("/api/v1/shipments/quote", transport.singleRequest().url.encodedPath)
    }

    private fun repository(transport: RecordingTransport) = RemoteCalculatorRepository(
        client = OkHttpClient.Builder().addInterceptor(transport).build(),
        json = AirdropJson,
        baseUrl = "https://transport-proof.invalid/api/v1",
    )

    private data class CapturedRequest(
        val method: String,
        val url: okhttp3.HttpUrl,
        val bodyText: String?,
    )

    private class RecordingTransport : Interceptor {
        private data class Fixture(val body: String, val code: Int)

        private val responses = ArrayDeque<Fixture>()
        private val requests = mutableListOf<CapturedRequest>()
        val requestCount: Int get() = requests.size

        fun enqueue(body: String, code: Int = 200) {
            responses.addLast(Fixture(body, code))
        }

        fun singleRequest(): CapturedRequest = requests.single()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += CapturedRequest(
                method = request.method,
                url = request.url,
                bodyText = request.body?.readUtf8(),
            )
            val fixture = checkNotNull(responses.pollFirst()) { "No response fixture queued" }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(fixture.code)
                .message(if (fixture.code in 200..299) "OK" else "Error")
                .body(fixture.body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}

private fun RequestBody?.readUtf8(): String? {
    if (this == null) return null
    return Buffer().use { buffer ->
        writeTo(buffer)
        buffer.readUtf8()
    }
}
