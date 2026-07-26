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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ⚠️ THE TWO TESTS THAT USED TO LIVE HERE ASSERTED THE BUG.
 *
 * One was literally named `standardCalculationUsesEnteredWeightWithoutMultiplyingPackages`
 * and asserted that entering **3 packages** produced the same freight as one —
 * pinning the defect Kemar called "very serious": the form makes Number of
 * Packages required, then the quote discarded it. Measured on pre-staging at
 * 5.5 lb / $150, six packages quoted USD 23.50 against a real USD 165.00.
 *
 * The other asserted that a blank weight silently became the package COUNT —
 * three packages of unknown weight priced as three pounds, a number nobody
 * entered and nothing measured.
 *
 * Both are now the opposite assertions: the count reaches the server, and an
 * absent weight is asked for rather than invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Records exactly what the view model asks the server for. */
    private class RecordingRepository(
        private val answer: ShipmentCalculation? = CALCULATION,
    ) : CalculatorRepository {
        var method: String? = null
        var invoice: Double? = null
        var weight: Double? = null
        var packages: Int? = null
        var calls = 0

        override suspend fun calculateShipment(
            shippingMethod: String,
            invoiceAmount: Double,
            weightLbs: Double?,
            numberOfPackages: Int,
            lengthInches: Double?,
            widthInches: Double?,
            heightInches: Double?,
        ): ShipmentCalculation {
            calls++
            method = shippingMethod
            invoice = invoiceAmount
            weight = weightLbs
            packages = numberOfPackages
            return answer ?: error("pricing service unavailable")
        }

        override suspend fun searchProducts(query: String, limit: Int): List<CalcProduct> = emptyList()

        override suspend fun usdToJmdRate(): Double = 162.0
    }

    /** AirDrop Standard must reach the server, and carry the package count. */
    @Test
    fun theEnteredPackageCountReachesTheServer() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals("the standard quote must be priced by the server", 1, repo.calls)
        assertEquals(3, repo.packages)
        assertEquals(5.5, repo.weight!!, 0.001)
        assertEquals(150.0, repo.invoice!!, 0.001)
    }

    /** The breakdown shown is the server's, not one the client recomputed. */
    @Test
    fun theRenderedChargesAreTheServersOwnBreakdown() = runTest(dispatcher) {
        val viewModel = CalculatorViewModel(RecordingRepository())

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        val result = viewModel.result.value
        assertNotNull(result)
        val charges = resolveCharges(result!!)
        assertEquals(69.0, charges.freight, 0.001)
        assertEquals(4.5, charges.fuelSurcharge, 0.001)
        assertEquals(82.5, charges.airdropCharges, 0.001)
        assertEquals(16.5, charges.totalWeightLbs, 0.001)
    }

    /**
     * Kemar: never quote a number no system authored. A failed pricing call
     * must raise an error, not fall through to a client-side estimate.
     */
    @Test
    fun aPricingFailureRaisesAnErrorInsteadOfQuoting() = runTest(dispatcher) {
        val viewModel = CalculatorViewModel(RecordingRepository(answer = null))

        viewModel.onPackagesChange("1")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        assertNull("no quote may be published when pricing failed", viewModel.result.value)
        val alert = viewModel.state.value.alert
        assertNotNull("the customer must be told", alert)
        assertEquals("Couldn't get current rates", alert!!.title)
    }

    /** A blank weight is asked for, never substituted with the package count. */
    @Test
    fun aBlankWeightIsAskedForRatherThanInvented() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("150")
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals("nothing may be priced without a weight", 0, repo.calls)
        assertNull(viewModel.result.value)
        assertEquals("Missing weight", viewModel.state.value.alert?.title)
    }

    private companion object {
        /** The real pre-staging answer for 3 × 5.5 lb, invoice $150. */
        val CALCULATION = ShipmentCalculation(
            shippingMethod = "airdrop_standard",
            freight = 69.0,
            insurance = 9.0,
            fuelSurcharge = 4.5,
            airdropCharges = 82.5,
            customsDuty = 102.6,
            totalWithDuty = 102.6,
            cifValue = 228.0,
            totalWeightLbs = 16.5,
        )
    }
}
