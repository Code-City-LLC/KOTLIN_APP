package com.ga.airdrop.feature.calculator

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorDutyRateSearchTest {
    private val dispatcher = StandardTestDispatcher()
    private val book = CalcDutyRate(60, "BOOK", 37.0)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `failure is not an empty catalogue and a later query retries`() = runTest(dispatcher) {
        val repository = SearchRepository { throw IOException("offline") }
        val vm = model(repository)
        vm.onProductChange("book")
        advanceUntilIdle()
        assertEquals(DutyRateSearchState.Failed, vm.state.value.searchState)
        assertNull(vm.state.value.selectedDutyRate)

        repository.search = { listOf(book) }
        vm.onProductChange("book")
        advanceUntilIdle()
        assertEquals(DutyRateSearchState.Results(listOf(book)), vm.state.value.searchState)
        assertEquals(2, repository.calls)
    }

    @Test
    fun `empty success remains distinct and does not alter the description`() = runTest(dispatcher) {
        val vm = model(SearchRepository { emptyList() })
        vm.onProductChange("Something custom")
        advanceUntilIdle()
        assertEquals(DutyRateSearchState.Results(emptyList()), vm.state.value.searchState)
        assertEquals("Something custom", vm.state.value.product)
    }

    @Test
    fun `all returned matches remain selectable instead of being truncated to eight`() = runTest(dispatcher) {
        val rows = (1..30).map { CalcDutyRate(it, "Book $it", 37.0) }
        val vm = model(SearchRepository { rows })
        vm.onProductChange("book")
        advanceUntilIdle()
        assertEquals(DutyRateSearchState.Results(rows), vm.state.value.searchState)
        vm.onProductSelected(rows.last())
        assertEquals(30, vm.state.value.selectedDutyRate?.id)
        assertEquals(DutyRateSearchState.Hidden, vm.state.value.searchState)
    }

    @Test
    fun `typing is debounced and short queries hide old results`() = runTest(dispatcher) {
        val repository = SearchRepository { listOf(book) }
        val vm = model(repository)
        vm.onProductChange("boo")
        advanceTimeBy(250)
        vm.onProductChange("book")
        advanceTimeBy(499)
        runCurrent()
        assertEquals(0, repository.calls)
        advanceUntilIdle()
        assertEquals(1, repository.calls)
        vm.onProductChange("bo")
        assertEquals(DutyRateSearchState.Hidden, vm.state.value.searchState)
    }

    @Test
    fun `cancelled same-text search cannot repopulate Standard or a changed legacy method`() = runTest(dispatcher) {
        for (method in listOf(ShippingMethod.STANDARD, ShippingMethod.SEADROP)) {
            val vm = model(SearchRepository {
                withContext(NonCancellable) { delay(1000) }
                listOf(book)
            })
            vm.onProductChange("book")
            advanceTimeBy(500)
            runCurrent()
            vm.onMethodSelected(method)
            advanceUntilIdle()
            assertEquals(DutyRateSearchState.Hidden, vm.state.value.searchState)
        }
    }

    @Test
    fun `late failure for an old query cannot overwrite the new results`() = runTest(dispatcher) {
        val vm = model(SearchRepository { query ->
            if (query == "old") {
                withContext(NonCancellable) { delay(1000) }
                throw IOException("old failed request")
            }
            listOf(book)
        })
        vm.onProductChange("old")
        advanceTimeBy(500)
        runCurrent()
        vm.onProductChange("book")
        advanceUntilIdle()
        assertEquals(DutyRateSearchState.Results(listOf(book)), vm.state.value.searchState)
    }

    @Test
    fun `Standard keeps the description free text without querying customs`() = runTest(dispatcher) {
        val repository = SearchRepository { listOf(book) }
        val vm = model(repository)
        vm.onMethodSelected(ShippingMethod.STANDARD)
        vm.onProductChange("book")
        advanceUntilIdle()
        assertEquals(0, repository.calls)
        assertEquals(DutyRateSearchState.Hidden, vm.state.value.searchState)
        assertEquals("book", vm.state.value.product)
    }

    private fun model(repository: SearchRepository) = CalculatorViewModel(repository).also {
        it.onMethodSelected(ShippingMethod.EXPRESS)
    }

    private class SearchRepository(var search: suspend (String) -> List<CalcDutyRate>) : CalculatorRepository {
        var calls = 0
        override suspend fun searchDutyRates(query: String, limit: Int): List<CalcDutyRate> {
            calls += 1
            return search(query)
        }
        override suspend fun usdToJmdRate() = 162.0
        override suspend fun calculateShipment(
            shippingMethod: String, invoiceAmount: Double, weightLbs: Double?, numberOfPackages: Int,
            lengthInches: Double?, widthInches: Double?, heightInches: Double?, customDutyRateId: Int?,
        ): ShipmentCalculation = error("Search tests must not request a quote")
    }
}
