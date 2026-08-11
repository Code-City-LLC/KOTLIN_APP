package com.ga.airdrop.feature.calculator

import com.ga.airdrop.data.api.AirdropJson
import java.util.ArrayDeque
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
                      {"id":8,"item_name":"Unrated item","duty_percentage":null}
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
        assertEquals(CalcDutyRate(8, "Unrated item", null), rates[1])

        val request = transport.singleRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/custom-duty-rates", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("8", request.url.queryParameter("per_page"))
        assertEquals("1", request.url.queryParameter("active_only"))
        assertEquals("laptop", request.url.queryParameter("search"))
        assertNull("the auction catalogue must not leak into this request", request.url.queryParameter("in_stock"))
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
        private val responses = ArrayDeque<String>()
        private val requests = mutableListOf<CapturedRequest>()

        fun enqueue(body: String) {
            responses.addLast(body)
        }

        fun singleRequest(): CapturedRequest = requests.single()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += CapturedRequest(
                method = request.method,
                url = request.url,
                bodyText = request.body?.readUtf8(),
            )
            val body = checkNotNull(responses.pollFirst()) { "No response fixture queued" }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
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
