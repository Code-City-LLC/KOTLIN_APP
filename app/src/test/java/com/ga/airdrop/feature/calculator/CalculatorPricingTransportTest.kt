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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ─── A refused quote says why (release audit 2026-09-24, MEDIUM) ────────
    //
    // Every refusal used to read "We couldn't reach our pricing service ...
    // check your connection", and the server's answer was dropped. A customs
    // item retired after the catalogue loaded failed EVERY retry, because the
    // screen kept sending it: Laravel rejects an inactive custom_duty_rate_id up
    // front (422 VALIDATION_ERROR, Rule::exists ... where is_active), or with
    // 422 DUTY_RATE_UNAVAILABLE if it is retired between validation and pricing.

    @Test
    fun `a retired customs item is named, dropped and never resent by the Airdrop quote`() = runTest(dispatcher) {
        val transport = ScriptedTransport().apply {
            respond(200, CATALOGUE_JSON)
            respond(422, RETIRED_ITEM_JSON)
            respond(200, TIER_QUOTE_JSON)
        }
        val vm = viewModel(transport)
        vm.onMethodSelected(ShippingMethod.STANDARD)
        vm.onProductChange("Lapt")
        vm.awaitSearch()
        vm.onProductSelected(LAPTOP)
        vm.fillForm()

        vm.calculate()
        vm.awaitCalculated()

        assertEquals("42", transport.requests[1].field("custom_duty_rate_id"))
        val alert = vm.state.value.alert
        assertEquals("Customs item unavailable", alert?.title)
        assertEquals(CUSTOMS_ITEM_UNAVAILABLE, alert?.message)
        assertNull("the retired pick must be cleared", vm.state.value.selectedDutyRate)

        // Calculating again must not send the retired item again.
        vm.dismissAlert()
        vm.calculate()
        vm.awaitCalculated()
        assertFalse(transport.requests[2].has("custom_duty_rate_id"))
        assertNull(vm.state.value.alert)
        assertNotNull(vm.result.value?.tierQuote)

        // The screen's cached catalogue no longer offers it either.
        vm.onProductChange("Laptop")
        assertEquals(listOf(43), vm.awaitSearch().map { it.id })
        assertEquals("the cached catalogue is filtered, not refetched", 3, transport.requests.size)
    }

    @Test
    fun `a customs item retired while pricing shows Laravel's own sentence`() = runTest(dispatcher) {
        val sentence = "Laptop computer was retired a moment ago. Pick another item."
        val transport = ScriptedTransport().apply {
            respond(200, CATALOGUE_JSON)
            respond(422, dutyRateUnavailable(sentence))
        }
        val vm = viewModel(transport)
        vm.onMethodSelected(ShippingMethod.STANDARD)
        vm.onProductChange("Lapt")
        vm.awaitSearch()
        vm.onProductSelected(LAPTOP)
        vm.fillForm()

        vm.calculate()
        vm.awaitCalculated()

        assertEquals(CalcAlert("Customs item unavailable", sentence), vm.state.value.alert)
        assertNull(vm.state.value.selectedDutyRate)
    }

    @Test
    fun `Express names a retired customs item too and does not resend it`() = runTest(dispatcher) {
        val transport = ScriptedTransport().apply {
            respond(200, CATALOGUE_JSON)
            respond(422, RETIRED_ITEM_JSON)
            respond(200, EXPRESS_JSON)
        }
        val vm = viewModel(transport)
        vm.onMethodSelected(ShippingMethod.EXPRESS)
        vm.onProductChange("Lapt")
        vm.awaitSearch()
        vm.onProductSelected(LAPTOP)
        vm.fillForm()

        vm.calculate()
        vm.awaitCalculated()

        assertEquals("/api/v1/shipping/calculate", transport.requests[1].path)
        assertEquals("42", transport.requests[1].field("custom_duty_rate_id"))
        assertEquals(CalcAlert("Customs item unavailable", CUSTOMS_ITEM_UNAVAILABLE), vm.state.value.alert)
        assertNull(vm.state.value.selectedDutyRate)

        vm.dismissAlert()
        vm.calculate()
        vm.awaitCalculated()
        assertFalse(transport.requests[2].has("custom_duty_rate_id"))
        assertNotNull(vm.result.value?.live)
    }

    /**
     * /shipping/calculate's twin of DUTY_RATE_UNAVAILABLE: an item retired
     * between validation and pricing makes ShippingCalculatorService throw
     * InvalidArgumentException, which the controller answers 400.
     */
    @Test
    fun `Express names a customs item retired while pricing`() = runTest(dispatcher) {
        val transport = ScriptedTransport().apply {
            respond(200, CATALOGUE_JSON)
            respond(400, RETIRED_WHILE_CALCULATING_JSON)
        }
        val vm = viewModel(transport)
        vm.onMethodSelected(ShippingMethod.EXPRESS)
        vm.onProductChange("Lapt")
        vm.awaitSearch()
        vm.onProductSelected(LAPTOP)
        vm.fillForm()

        vm.calculate()
        vm.awaitCalculated()

        assertEquals(CalcAlert("Customs item unavailable", CUSTOMS_ITEM_UNAVAILABLE), vm.state.value.alert)
        assertNull(vm.state.value.selectedDutyRate)
    }

    /** A re-quote (SAVR insurance choice) re-posts the item, so it can be refused too. */
    @Test
    fun `a re-quote refused for a retired customs item names it and unpicks it`() = runTest(dispatcher) {
        val transport = ScriptedTransport().apply {
            respond(200, CATALOGUE_JSON)
            respond(200, TIER_QUOTE_JSON)
            respond(422, dutyRateUnavailable(CUSTOMS_ITEM_UNAVAILABLE))
        }
        val vm = viewModel(transport)
        vm.onMethodSelected(ShippingMethod.STANDARD)
        vm.onProductChange("Lapt")
        vm.awaitSearch()
        vm.onProductSelected(LAPTOP)
        vm.fillForm()
        vm.calculate()
        vm.awaitCalculated()
        assertNotNull(vm.result.value?.tierQuote)

        vm.selectTierInsurance(false)
        vm.state.first { !it.tierQuoteActionLoading }

        assertEquals("42", transport.requests[2].field("custom_duty_rate_id"))
        assertEquals(CalcAlert("Customs item unavailable", CUSTOMS_ITEM_UNAVAILABLE), vm.state.value.alert)
        assertNull(vm.state.value.selectedDutyRate)
    }

    @Test
    fun `a refused quote shows Laravel's validation message, not a connection problem`() = runTest(dispatcher) {
        for ((method, body, expected) in listOf(
            Triple(ShippingMethod.STANDARD, TOO_MANY_PACKAGES_JSON, "The number of packages field must not be greater than 1000."),
            Triple(ShippingMethod.EXPRESS, TOO_LONG_JSON, "The package length must not exceed 1000."),
        )) {
            val transport = ScriptedTransport().apply { respond(422, body) }
            val vm = viewModel(transport)
            vm.onMethodSelected(method)
            vm.fillForm()
            if (method == ShippingMethod.STANDARD) vm.onPackagesChange("1500") else vm.onLengthChange("1200")

            vm.calculate()
            vm.awaitCalculated()

            val alert = vm.state.value.alert
            assertEquals("$method", CalcAlert("Couldn't get current rates", expected), alert)
            assertNull(vm.result.value)
        }
    }

    @Test
    fun `only a network failure or a server fault says check your connection`() = runTest(dispatcher) {
        for (method in listOf(ShippingMethod.STANDARD, ShippingMethod.EXPRESS)) {
            for (script in listOf<ScriptedTransport.() -> Unit>(
                { failConnection() },
                { respond(500, SERVER_FAULT_JSON) },
                { respond(503, "<html>Service Unavailable</html>") },
            )) {
                val transport = ScriptedTransport().apply(script)
                val vm = viewModel(transport)
                vm.onMethodSelected(method)
                vm.fillForm()

                vm.calculate()
                vm.awaitCalculated()

                assertEquals("$method", CalcAlert("Couldn't get current rates", CONNECTIVITY), vm.state.value.alert)
            }
        }
    }

    // ─── Customs suggestions (release audit 2026-09-24, LOW) ─────────────────

    /** The whole catalogue is searched and ranked; only the best 20 render. */
    @Test
    fun `customs suggestions are the best of the whole catalogue, capped at twenty`() = runTest(dispatcher) {
        val books = (1..30).joinToString(",") {
            """{"id": $it, "item_name": "Book ${it.toString().padStart(2, '0')}", "duty_percentage": "37.00"}"""
        }
        val transport = ScriptedTransport().apply {
            // The exact match is the catalogue's LAST row.
            respond(200, """{"success": true, "data": {"items": [$books,
                {"id": 99, "item_name": "BOOK", "duty_percentage": "37.00"}]}}""")
        }
        val vm = viewModel(transport)
        vm.onProductChange("book")
        val shown = vm.awaitSearch()

        assertEquals(20, shown.size)
        assertEquals("the exact match ranks first", 99, shown.first().id)
        assertEquals((1..19).toList(), shown.drop(1).map { it.id })
        assertEquals(31, (vm.state.value.searchState as DutyRateSearchState.Results).totalMatches)
    }

    // ─── harness ────────────────────────────────────────────────────────────

    private fun CalculatorViewModel.fillForm() {
        onPackagesChange("1")
        onInvoiceChange("150")
        onActualWeightChange("5.5")
        onLengthChange("10")
        onWidthChange("8")
        onHeightChange("6")
    }

    /** The search also hops to the IO dispatcher; wait for its answer. */
    private suspend fun CalculatorViewModel.awaitSearch(): List<CalcDutyRate> {
        val settled = state.first {
            it.searchState is DutyRateSearchState.Results || it.searchState is DutyRateSearchState.Failed
        }
        return (settled.searchState as DutyRateSearchState.Results).products
    }

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
        fun has(name: String): Boolean = body?.containsKey(name) == true
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
        val LAPTOP = CalcDutyRate(id = 42, itemName = "Laptop computer", dutyPercentage = 20.0)

        const val CUSTOMS_ITEM_UNAVAILABLE = "The selected customs item is no longer available. Pick another item."
        const val CONNECTIVITY = "We couldn't reach our pricing service, so we can't quote this shipment " +
            "right now. Please check your connection and try again."

        /** GET /custom-duty-rates?active_only=1, as loaded before the item was retired. */
        const val CATALOGUE_JSON = """
            {"success": true, "data": {"items": [
              {"id": 42, "item_name": "Laptop computer", "duty_percentage": "20.00"},
              {"id": 43, "item_name": "Laptop bag", "duty_percentage": "20.00"}]}}
        """

        /** bootstrap/app.php's ValidationException renderer for Rule::exists(...)->where(is_active). */
        const val RETIRED_ITEM_JSON = """
            {"success": false, "message": "Validation failed",
             "errors": {"custom_duty_rate_id": ["The selected custom duty rate id is invalid."]},
             "error_code": "VALIDATION_ERROR", "meta": {"timestamp": "2026-09-24T12:00:00+00:00"}}
        """

        /** QuoteController's DUTY_RATE_UNAVAILABLE (ApiResponse::errorResponse shape). */
        fun dutyRateUnavailable(message: String) = """
            {"success": false, "message": "$message", "error_code": "DUTY_RATE_UNAVAILABLE",
             "meta": {"timestamp": "2026-09-24T12:00:00+00:00"}}
        """

        /** ShippingCalculatorController's INVALID_ARGUMENT for withResolvedDutyRate's refusal. */
        const val RETIRED_WHILE_CALCULATING_JSON = """
            {"success": false, "message": "The selected package duty rate is unavailable.",
             "error_code": "INVALID_ARGUMENT", "meta": {"timestamp": "2026-09-24T12:00:00+00:00"}}
        """

        /** QuoteController: number_of_packages max:1000 (Laravel's default message). */
        const val TOO_MANY_PACKAGES_JSON = """
            {"success": false, "message": "Validation failed",
             "errors": {"number_of_packages": ["The number of packages field must not be greater than 1000."]},
             "error_code": "VALIDATION_ERROR", "meta": {"timestamp": "2026-09-24T12:00:00+00:00"}}
        """

        /** CalculateShippingRequest: package_length max:1000 (its own message). */
        const val TOO_LONG_JSON = """
            {"success": false, "message": "Validation failed",
             "errors": {"package_length": ["The package length must not exceed 1000."]},
             "error_code": "VALIDATION_ERROR", "meta": {"timestamp": "2026-09-24T12:00:00+00:00"}}
        """

        const val SERVER_FAULT_JSON = """
            {"success": false, "message": "Failed to build quote", "meta": {"timestamp": "2026-09-24T12:00:00+00:00"}}
        """

        /** POST /shipping/calculate for airdrop_express (ShippingCalculatorService shape). */
        const val EXPRESS_JSON = """
            {"success": true, "message": "Shipping cost calculated successfully", "data": {
              "shipping_method": "airdrop_express",
              "breakdown": {"freight": 16.5, "insurance": 15, "fuel_surcharge": 1.5, "customs_duty": 0,
                "bad_address_fee": 0, "subtotal": 33, "total_charges": 0, "airdrop_charges": 33,
                "grand_total": 33},
              "calculations": {"total_chargeable_weight_lbs": 5.5, "number_of_packages": 1,
                "cif_value": 181.5, "invoice_amount": 150}}}
        """

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
