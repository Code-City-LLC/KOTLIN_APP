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

    /**
     * ⚠️ THE KEY THAT LOOKS LIKE A TOTAL AND IS NOT ONE.
     *
     * Measured live on pre-staging, 2 packages / 5.5 lb / $150 invoice:
     *
     *   airdrop_standard  total_with_duty 90.90 == customs_duty 90.90   grand_total 145.90
     *   airdrop_express   total_charges   87.30 == customs_duty 87.30   grand_total 132.80
     *   seadrop_standard  total_charges  202.50 (folds in the invoice)  grand_total  52.50
     *
     * Kotlin rendered `total_with_duty ?: total_charges` as the headline, so it
     * showed the DUTY as the total. `grand_total` is composed server-side as
     * round(airdrop_charges + customs_duty, 2), identically for all three
     * methods. Three-of-three consensus, BronzeMountain #80146; Kemar ruled the
     * headline excludes the merchant invoice, so seadrop's 52.50 is intended.
     */
    @Test
    fun `the headline is grand_total, never the duty-only legacy keys`() = runTest(dispatcher) {
        val viewModel = CalculatorViewModel(
            RecordingRepository(
                answer = CALCULATION.copy(
                    airdropCharges = 55.0,
                    customsDuty = 90.9,
                    // What the server sends for airdrop_standard: grand_total.
                    totalWithDuty = 145.9,
                ),
            ),
        )
        viewModel.onPackagesChange("2")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        val charges = resolveCharges(viewModel.result.value!!)
        assertEquals("the headline must be grand_total", 145.9, charges.totalWithDuty, 0.001)
        assertEquals(55.0, charges.airdropCharges, 0.001)
        // The duty is a line item, never the headline.
        assertEquals(90.9, charges.customsDuty, 0.001)
    }

    /**
     * bad_address_fee is conditional — it applies only when the address is
     * flagged bad — so it is deliberately outside grand_total. Folding it in
     * would OVER-quote every normal customer, the mirror of the under-quote
     * fixed earlier today.
     */
    @Test
    fun `a bad address fee is carried as its own line and not folded into the headline`() =
        runTest(dispatcher) {
            val viewModel = CalculatorViewModel(
                RecordingRepository(
                    answer = CALCULATION.copy(
                        airdropCharges = 55.0,
                        customsDuty = 90.9,
                        totalWithDuty = 145.9,
                        badAddressFee = 12.0,
                    ),
                ),
            )
            viewModel.onPackagesChange("2")
            viewModel.onInvoiceChange("150")
            viewModel.onActualWeightChange("5.5")
            viewModel.calculate()
            advanceUntilIdle()

            val charges = resolveCharges(viewModel.result.value!!)
            assertEquals(12.0, charges.badAddressFee!!, 0.001)
            assertEquals("the fee must not inflate the headline", 145.9, charges.totalWithDuty, 0.001)
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
