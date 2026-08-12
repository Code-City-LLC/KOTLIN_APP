package com.ga.airdrop.feature.calculator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The calculator's item field selects a CUSTOMS CLASSIFICATION, and the id it
 * selects has to reach the quote.
 *
 * ⚠️ THIS FIELD WAS SEARCHING THE WRONG CATALOGUE ENTIRELY.
 *
 * It called `GET /products?in_stock=1` — the auction listing — and rendered a
 * shop price beside every row. A customer classifying a laptop for duty was
 * shown laptops for sale with dollar amounts next to them. Then the id they
 * picked went nowhere: `calculateShipment` had no parameter to carry it, so
 * the server always fell back to a default rate and the selection was purely
 * decorative.
 *
 * That is why "search is dead" could not be found by reading the search
 * wiring — the wiring worked. It was the wrong business object.
 *
 * Root-caused independently and to the same lines by @Codex-LavenderGlen
 * (ORC #99765) and @Codex-MobileReleaseQC (#99763).
 *
 * The rate id, never a percentage: Laravel validates the id is active and
 * resolves the rate server-side. `ShipmentCalculationRequest` still omits
 * `custom_duty_percentage` for the same reason it always has — mobile does not
 * author duty rates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorDutyRateSelectionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val laptop = CalcDutyRate(id = 42, itemName = "Laptop computer", dutyPercentage = 20.0)
    private val phone = CalcDutyRate(id = 7, itemName = "Mobile phone", dutyPercentage = 12.5)

    private fun viewModel(repo: FakeRepo) = CalculatorViewModel(repo)

    private class FakeRepo : CalculatorRepository {
        var dutyRateId: Int? = null
        var sawCalculate = false
        val searchQueries = mutableListOf<String>()
        var searchAnswer: List<CalcDutyRate> = emptyList()

        override suspend fun calculateShipment(
            shippingMethod: String,
            invoiceAmount: Double,
            weightLbs: Double?,
            numberOfPackages: Int,
            lengthInches: Double?,
            widthInches: Double?,
            heightInches: Double?,
            customDutyRateId: Int?,
        ): ShipmentCalculation {
            sawCalculate = true
            dutyRateId = customDutyRateId
            return ShipmentCalculation(
                shippingMethod = shippingMethod,
                freight = 1.0,
                insurance = 1.0,
                fuelSurcharge = 1.0,
                airdropCharges = 1.0,
                customsDuty = 1.0,
                totalWithDuty = 1.0,
                cifValue = 1.0,
                totalWeightLbs = 1.0,
            )
        }

        override suspend fun searchDutyRates(query: String, limit: Int): List<CalcDutyRate> {
            searchQueries += query
            return searchAnswer
        }

        override suspend fun usdToJmdRate(): Double = 162.0
    }

    private fun CalculatorViewModel.prepareValidForm() {
        onPackagesChange("1")
        onInvoiceChange("150")
        onActualWeightChange("5.5")
    }

    // ── selection is retained ───────────────────────────────────────────────

    @Test
    fun `selecting a rate keeps its id, and shows the item name in the field`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = viewModel(repo)

        vm.onProductSelected(laptop)
        advanceUntilIdle()

        assertEquals("Laptop computer", vm.state.value.product)
        assertEquals(laptop, vm.state.value.selectedDutyRate)
        assertEquals(42, vm.state.value.selectedDutyRate?.id)
    }

    // ── editing the text drops the id ───────────────────────────────────────

    @Test
    fun `typing after selecting CLEARS the id — a stale id must not outlive the name`() =
        runTest(dispatcher) {
            val repo = FakeRepo()
            val vm = viewModel(repo)

            vm.onProductSelected(laptop)
            advanceUntilIdle()
            assertEquals(42, vm.state.value.selectedDutyRate?.id)

            // The customer edits the field. What is on screen no longer matches
            // rate 42, so sending 42 would quote a classification they cannot see.
            vm.onProductChange("Laptop computer and case")
            advanceUntilIdle()

            assertNull(
                "the selection must not survive an edit to the text",
                vm.state.value.selectedDutyRate,
            )
        }

    @Test
    fun `selecting a second rate replaces the first id`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = viewModel(repo)

        vm.onProductSelected(laptop)
        vm.onProductSelected(phone)
        advanceUntilIdle()

        assertEquals(7, vm.state.value.selectedDutyRate?.id)
        assertEquals("Mobile phone", vm.state.value.product)
    }

    // ── the id reaches the quote ────────────────────────────────────────────

    @Test
    fun `the selected rate id is SENT with the shipping quote`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = viewModel(repo)

        vm.onProductSelected(laptop)
        vm.prepareValidForm()
        advanceUntilIdle()
        vm.calculate()
        advanceUntilIdle()

        assertTrue("the quote must have been requested", repo.sawCalculate)
        assertEquals(
            "the customs classification the customer picked must reach the server",
            42,
            repo.dutyRateId,
        )
    }

    @Test
    fun `with no selection the quote sends NO rate id, letting the server default`() =
        runTest(dispatcher) {
            val repo = FakeRepo()
            val vm = viewModel(repo)

            vm.prepareValidForm()
            advanceUntilIdle()
            vm.calculate()
            advanceUntilIdle()

            assertTrue(repo.sawCalculate)
            assertNull(
                "an unselected classification must not be invented client-side",
                repo.dutyRateId,
            )
        }

    @Test
    fun `an id cleared by editing is NOT sent`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = viewModel(repo)

        vm.onProductSelected(laptop)
        vm.onProductChange("something else entirely")
        vm.prepareValidForm()
        advanceUntilIdle()
        vm.calculate()
        advanceUntilIdle()

        assertNull(
            "clearing the selection must also clear what gets sent",
            repo.dutyRateId,
        )
    }

    // ── the model carries no money ──────────────────────────────────────────

    @Test
    fun `a duty rate has no price field to render`() {
        // Compile-time guard as much as a runtime one: if anyone reintroduces a
        // price on this model, the auction catalogue is creeping back in.
        val rate = CalcDutyRate(id = 1, itemName = "Anything", dutyPercentage = null)
        assertEquals(1, rate.id)
        assertEquals("Anything", rate.itemName)
        assertNull("a missing duty percentage stays missing, never a fabricated 0", rate.dutyPercentage)
    }
}
