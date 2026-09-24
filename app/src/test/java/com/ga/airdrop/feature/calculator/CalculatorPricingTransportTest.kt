package com.ga.airdrop.feature.calculator

import com.ga.airdrop.data.api.AirdropJson
import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The calculator form, through the PRODUCTION repository, to the bytes Laravel
 * receives and the alert the customer sees.
 *
 * View-model fakes cannot see a field that never reaches the wire, and
 * [RemoteCalculatorRepositoryTransportTest] cannot see what the form puts into
 * a request. This runs [CalculatorViewModel] over [RemoteCalculatorRepository]
 * and a scripted OkHttp interceptor, so no network request is made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorPricingTransportTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /**
     * Release audit 2026-09-24 (HIGH): "Number of Packages" is a required field,
     * but the Airdrop quote never sent it, so three 5.5 lb packages were priced
     * as ONE 5.5 lb parcel. Laravel 9515a2997 takes `number_of_packages` on
     * POST /shipments/quote; the weight stays per package, as on
     * /shipping/calculate.
     */
    @Test
    fun `an Airdrop quote tells Laravel how many packages the weight covers`() = runTest(dispatcher) {
        val transport = ScriptedTransport().apply { respond(200, TIER_QUOTE_JSON) }
        val vm = viewModel(transport)
        vm.onMethodSelected(ShippingMethod.STANDARD)
        vm.onPackagesChange("3")
        vm.onInvoiceChange("150")
        vm.onActualWeightChange("5.5")

        vm.calculate()
        vm.awaitCalculated()

        val request = transport.requests.single()
        assertEquals("/api/v1/shipments/quote", request.path)
        assertEquals("the weight stays PER PACKAGE", "5.5", request.field("weight"))
        assertEquals("the quote must cover every package", "3", request.field("number_of_packages"))

        // A re-quote (SAVR insurance choice, expiry refresh) re-posts the same
        // request, so it must not fall back to one package either.
        transport.respond(200, TIER_QUOTE_JSON)
        vm.selectTierInsurance(false)
        vm.state.first { !it.tierQuoteActionLoading }
        val requote = transport.requests.last()
        assertEquals("true", requote.field("insurance_declined"))
        assertEquals("3", requote.field("number_of_packages"))
    }

    // ─── harness ────────────────────────────────────────────────────────────

    private fun viewModel(transport: ScriptedTransport) = CalculatorViewModel(
        RemoteCalculatorRepository(
            client = OkHttpClient.Builder().addInterceptor(transport).build(),
            json = AirdropJson,
            baseUrl = "https://transport-proof.invalid/api/v1",
        ),
    )

    /**
     * The repository hops to the real IO dispatcher. Suspending on the state
     * lets runTest wait for that hop to land back on the test Main dispatcher,
     * where advanceUntilIdle alone would return before the response arrived.
     */
    private suspend fun CalculatorViewModel.awaitCalculated() {
        state.first { !it.calculating }
    }

    private class CapturedRequest(val path: String, val body: JsonObject?) {
        fun field(name: String): String? = body?.get(name)?.jsonPrimitive?.content
    }

    private class ScriptedTransport : Interceptor {
        private val script = ArrayDeque<(okhttp3.Request) -> Response>()
        val requests = mutableListOf<CapturedRequest>()

        fun respond(code: Int, body: String) {
            script.addLast { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        /** No response at all: the device is offline. */
        fun failConnection() {
            script.addLast { throw IOException("Unable to resolve host \"transport-proof.invalid\"") }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val text = request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }
            requests += CapturedRequest(
                path = request.url.encodedPath,
                body = text?.let { AirdropJson.parseToJsonElement(it) as? JsonObject },
            )
            val next = checkNotNull(script.pollFirst()) { "No response scripted for ${request.url}" }
            return next(request)
        }
    }

    private companion object {
        /** A real /shipments/quote envelope (pre-staging shape). */
        const val TIER_QUOTE_JSON = """
            {"success": true, "message": "Quote created", "data": {
              "quote_reference": "Q-7H2K9M4P6R8T", "customer_tier": "SAVR", "method": "AIR",
              "destination": "JM", "currency": "USD",
              "line_items": [
                {"code": "base_shipping", "label": "Base shipping", "amount": 60},
                {"code": "fuel_surcharge", "label": "Fuel surcharge", "amount": 4.5},
                {"code": "insurance", "label": "Insurance", "amount": 1.5}
              ],
              "subtotal": 66, "total_due": 66, "status": "active", "is_expired": false,
              "expires_at": "2099-01-02T03:04:05Z", "insurance_choice_required": false,
              "aircoins_earned": 0}}
        """
    }
}
