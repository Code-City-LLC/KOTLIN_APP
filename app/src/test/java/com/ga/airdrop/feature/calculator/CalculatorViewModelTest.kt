package com.ga.airdrop.feature.calculator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * Legacy Express/SeaDrop still send package count to their server calculator;
 * Airdrop now uses TierQuote's separate documented contract. An absent weight
 * is always asked for rather than invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Records exactly what the view model asks the server for. */
    private class RecordingRepository(
        private val legacyAnswer: ShipmentCalculation? = CALCULATION,
        private val tierAnswer: TierQuote? = TIER_QUOTE,
    ) : CalculatorRepository {
        var method: String? = null
        var invoice: Double? = null
        var weight: Double? = null
        var packages: Int? = null
        var dutyRateId: Int? = null
        var searchQueries = mutableListOf<String>()
        var searchAnswer: List<CalcDutyRate> = emptyList()
        var legacyCalls = 0
        var tierCalls = 0
        val tierRequests = mutableListOf<TierQuoteRequest>()
        var nextTierResponse: CompletableDeferred<TierQuote>? = null

        override suspend fun quoteShipment(request: TierQuoteRequest): TierQuote {
            tierCalls++
            tierRequests += request
            val response = nextTierResponse
            nextTierResponse = null
            if (response != null) return response.await()
            return tierAnswer ?: error("tier pricing service unavailable")
        }

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
            legacyCalls++
            dutyRateId = customDutyRateId
            method = shippingMethod
            invoice = invoiceAmount
            weight = weightLbs
            packages = numberOfPackages
            return legacyAnswer ?: error("pricing service unavailable")
        }

        override suspend fun searchDutyRates(query: String, limit: Int): List<CalcDutyRate> {
            searchQueries += query
            return searchAnswer
        }

        override suspend fun usdToJmdRate(): Double = 162.0
    }

    /** AirDrop Standard is a TierQuote and must never fall through to legacy calculate. */
    @Test
    fun airdropUsesTierQuoteWithTheAuthoritativeRequestContract() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.onProductChange("Laptop")
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals("Airdrop must use the tier endpoint", 1, repo.tierCalls)
        assertEquals("Airdrop must not silently fall back to legacy calculate", 0, repo.legacyCalls)
        val request = repo.tierRequests.single()
        assertEquals("AIR", request.method)
        // The weight is PER PACKAGE and the count travels beside it (Laravel
        // 9515a2997 multiplies freight and fuel by it). It used to be dropped,
        // so 3 packages were quoted as one.
        assertEquals(5.5, request.weightLbs, 0.001)
        assertEquals("the quote must cover every package", 3, request.numberOfPackages)
        assertEquals(150.0, request.declaredValue!!, 0.001)
        assertEquals(150.0, request.insuredValue!!, 0.001)
        assertEquals("Laptop", request.itemName)
        assertNull("the app must not infer an insurance decision", request.insuranceDeclined)
    }

    /**
     * Kemar 2026-09-23 on the customs item picker: "fix it, make sure it works".
     * Airdrop searches the customs catalogue and its tier quote carries the
     * picked item, which Laravel now prices into the quote.
     */
    @Test
    fun airdropSearchesCustomsAndSendsThePickedItemToTheTierQuote() = runTest(dispatcher) {
        val laptop = CalcDutyRate(id = 42, itemName = "Laptop computer", dutyPercentage = 20.0)
        val repo = RecordingRepository().apply { searchAnswer = listOf(laptop) }
        val viewModel = CalculatorViewModel(repo)

        viewModel.onProductChange("Lapt")
        advanceUntilIdle()
        assertEquals("Airdrop must search the customs catalogue", listOf("Lapt"), repo.searchQueries)

        viewModel.onProductSelected(laptop)
        viewModel.onPackagesChange("1")
        viewModel.onInvoiceChange("300")
        viewModel.onActualWeightChange("3")
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals(0, repo.legacyCalls)
        assertEquals(42, repo.tierRequests.single().customDutyRateId)
    }

    @Test
    fun switchingToAirdropKeepsTheCustomsPick() = runTest(dispatcher) {
        val laptop = CalcDutyRate(id = 42, itemName = "Laptop computer", dutyPercentage = 20.0)
        val viewModel = CalculatorViewModel(RecordingRepository())

        viewModel.onMethodSelected(ShippingMethod.EXPRESS)
        viewModel.onProductSelected(laptop)
        viewModel.onMethodSelected(ShippingMethod.STANDARD)

        assertEquals(laptop, viewModel.state.value.selectedDutyRate)
    }

    /** The TierQuote total and lines are server-owned, never reconstructed by Android. */
    @Test
    fun theRenderedTierQuoteUsesTheServersOwnLinesAndTotal() = runTest(dispatcher) {
        val viewModel = CalculatorViewModel(RecordingRepository())

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        val result = viewModel.result.value
        assertNotNull(result)
        assertEquals(TIER_QUOTE, result!!.tierQuote)
        val charges = resolveCharges(result!!)
        assertEquals(TIER_QUOTE.totalDue, charges.totalWithDuty, 0.001)
        assertEquals(0.0, charges.customsDuty, 0.001)
    }

    /**
     * The Airdrop results card says "Total Weight" but showed ONE package's
     * weight: 3 x 5.5 lb read "5.50 lbs" beside a price for all three. The
     * customer web calculator's "Total Weight LBS" is packages x actual weight,
     * and /shipping/calculate's total_weight_lbs is weight per package x count.
     */
    @Test
    fun theAirdropTotalWeightCoversEveryPackage() = runTest(dispatcher) {
        val viewModel = CalculatorViewModel(RecordingRepository())

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        val result = viewModel.result.value!!
        assertEquals("the request weight stays per package", 5.5, result.weightLbs, 0.001)
        assertEquals("Total Weight is every package", 16.5, resolveCharges(result).totalWeightLbs, 0.001)
    }

    /**
     * Kemar: never quote a number no system authored. A failed pricing call
     * must raise an error, not fall through to a client-side estimate.
     */
    @Test
    fun aPricingFailureRaisesAnErrorInsteadOfQuoting() = runTest(dispatcher) {
        val repo = RecordingRepository(tierAnswer = null)
        val viewModel = CalculatorViewModel(repo)

        viewModel.onPackagesChange("1")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        assertNull("no quote may be published when pricing failed", viewModel.result.value)
        assertEquals(1, repo.tierCalls)
        assertEquals("Airdrop failures must not retry through legacy calculate", 0, repo.legacyCalls)
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

        assertEquals("nothing may be priced without a weight", 0, repo.tierCalls)
        assertEquals("nothing may be priced without a weight", 0, repo.legacyCalls)
        assertNull(viewModel.result.value)
        assertEquals("Missing weight", viewModel.state.value.alert?.title)
    }

    /**
     * Was `airdropKeepsTheProductAsDescriptionWithoutQueryingTheCustomsPicker`:
     * Airdrop used to skip the customs catalogue because the tier quote ignored
     * the pick. Kemar 2026-09-23 reversed that ("fix it, make sure it works") and
     * Laravel's tier quote now prices the picked item. The typed text is still
     * kept and sent as item_name.
     */
    @Test
    fun airdropKeepsTheProductTextAndQueriesTheCustomsPicker() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)

        viewModel.onProductChange("Laptop")
        advanceUntilIdle()

        assertEquals("Laptop", viewModel.state.value.product)
        assertEquals(listOf("Laptop"), repo.searchQueries)
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
                legacyAnswer = CALCULATION.copy(
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
        viewModel.onMethodSelected(ShippingMethod.EXPRESS)
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
                    legacyAnswer = CALCULATION.copy(
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
            viewModel.onMethodSelected(ShippingMethod.EXPRESS)
            viewModel.calculate()
            advanceUntilIdle()

            val charges = resolveCharges(viewModel.result.value!!)
            assertEquals(12.0, charges.badAddressFee!!, 0.001)
            assertEquals("the fee must not inflate the headline", 145.9, charges.totalWithDuty, 0.001)
        }

    @Test
    fun tierQuoteRequiresAnInsuranceChoiceBeforePaymentAndRequotesOnDecline() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)

        viewModel.onPackagesChange("1")
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        advanceUntilIdle()

        assertFalse(viewModel.canProceedToPayment())
        assertEquals("Insurance choice required", viewModel.state.value.alert?.title)

        viewModel.dismissAlert()
        viewModel.selectTierInsurance(false)
        advanceUntilIdle()

        assertEquals(2, repo.tierCalls)
        assertTrue(repo.tierRequests.last().insuranceDeclined == true)
        assertEquals(false, viewModel.result.value?.insuranceChoice)
        assertTrue(viewModel.canProceedToPayment())
    }

    @Test
    fun supersededTierRefreshSuccessDoesNotReplaceNewerCalculation() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        runCurrent()

        val oldRefresh = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = oldRefresh
        viewModel.refreshTierQuote()
        runCurrent()
        assertTrue(viewModel.state.value.tierQuoteActionLoading)

        val newerQuote = TIER_QUOTE.copy(quoteReference = "Q-NEWER", totalDue = 42.0)
        repo.nextTierResponse = CompletableDeferred(newerQuote)
        viewModel.onInvoiceChange("250")
        viewModel.calculate()
        runCurrent()
        val newerResult = viewModel.result.value!!
        val newerState = viewModel.state.value
        assertEquals(newerQuote, newerResult.tierQuote)
        assertEquals(250.0, newerResult.invoiceUsd, 0.001)
        assertFalse(newerState.tierQuoteActionLoading)

        oldRefresh.complete(TIER_QUOTE.copy(quoteReference = "Q-STALE"))
        runCurrent()

        assertEquals(newerResult, viewModel.result.value)
        assertEquals(newerState, viewModel.state.value)
        assertEquals(3, repo.tierCalls)
    }

    @Test
    fun supersededTierRefreshFailureDoesNotAlterNewerCalculation() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        runCurrent()

        val oldRefresh = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = oldRefresh
        viewModel.refreshTierQuote()
        runCurrent()
        assertTrue(viewModel.state.value.tierQuoteActionLoading)

        val newerQuote = TIER_QUOTE.copy(quoteReference = "Q-NEWER", totalDue = 42.0)
        repo.nextTierResponse = CompletableDeferred(newerQuote)
        viewModel.onInvoiceChange("250")
        viewModel.calculate()
        runCurrent()
        val newerResult = viewModel.result.value!!
        val newerState = viewModel.state.value
        assertEquals(newerQuote, newerResult.tierQuote)
        assertEquals(250.0, newerResult.invoiceUsd, 0.001)
        assertFalse(newerState.tierQuoteActionLoading)
        assertNull(newerState.alert)

        oldRefresh.completeExceptionally(IllegalStateException("old refresh failed"))
        runCurrent()

        assertEquals(newerResult, viewModel.result.value)
        assertEquals(newerState, viewModel.state.value)
        assertEquals(3, repo.tierCalls)
    }

    @Test
    fun supersededTierRefreshSuccessPreservesNewerInsuranceChoice() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        runCurrent()

        val oldRefresh = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = oldRefresh
        viewModel.refreshTierQuote()
        runCurrent()

        viewModel.onInvoiceChange("250")
        viewModel.calculate()
        runCurrent()
        viewModel.selectTierInsurance(false)
        runCurrent()
        val newerResult = viewModel.result.value!!
        val newerState = viewModel.state.value
        assertEquals(false, newerResult.insuranceChoice)
        assertEquals(true, newerResult.tierQuoteRequest?.insuranceDeclined)
        assertEquals(250.0, newerResult.invoiceUsd, 0.001)

        oldRefresh.complete(TIER_QUOTE.copy(quoteReference = "Q-STALE"))
        runCurrent()

        assertEquals(newerResult, viewModel.result.value)
        assertEquals(newerState, viewModel.state.value)
        assertEquals(4, repo.tierCalls)
    }

    @Test
    fun supersededTierRefreshFailurePreservesPendingNewerInsuranceChoice() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        runCurrent()

        val oldRefresh = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = oldRefresh
        viewModel.refreshTierQuote()
        runCurrent()

        viewModel.onInvoiceChange("250")
        viewModel.calculate()
        runCurrent()
        val newerChoice = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = newerChoice
        viewModel.selectTierInsurance(true)
        runCurrent()
        val newerResult = viewModel.result.value!!
        val pendingState = viewModel.state.value
        assertTrue(pendingState.tierQuoteActionLoading)

        oldRefresh.completeExceptionally(IllegalStateException("old refresh failed"))
        runCurrent()

        assertEquals(newerResult, viewModel.result.value)
        assertEquals(pendingState, viewModel.state.value)
        val newerQuote = TIER_QUOTE.copy(quoteReference = "Q-NEWER-CHOICE")
        newerChoice.complete(newerQuote)
        runCurrent()

        assertEquals(newerQuote, viewModel.result.value?.tierQuote)
        assertEquals(true, viewModel.result.value?.insuranceChoice)
        assertNull(viewModel.result.value?.tierQuoteRequest?.insuranceDeclined)
        assertFalse(viewModel.state.value.tierQuoteActionLoading)
        assertNull(viewModel.state.value.alert)
        assertEquals(4, repo.tierCalls)
    }

    @Test
    fun tierRefreshDoesNotStartWhileNewerCalculationIsPending() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        runCurrent()

        val calculation = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = calculation
        viewModel.onInvoiceChange("250")
        viewModel.calculate()
        runCurrent()
        val pendingState = viewModel.state.value
        assertTrue(pendingState.calculating)

        viewModel.refreshTierQuote()
        viewModel.selectTierInsurance(false)
        runCurrent()

        assertEquals(2, repo.tierCalls)
        assertEquals(pendingState, viewModel.state.value)
        val newerQuote = TIER_QUOTE.copy(quoteReference = "Q-NEWER")
        calculation.complete(newerQuote)
        runCurrent()

        assertEquals(newerQuote, viewModel.result.value?.tierQuote)
        assertEquals(250.0, viewModel.result.value!!.invoiceUsd, 0.001)
        assertNull(viewModel.result.value?.insuranceChoice)
        assertFalse(viewModel.state.value.calculating)
        assertFalse(viewModel.state.value.tierQuoteActionLoading)
    }

    @Test
    fun currentTierRefreshFailurePreservesValidQuoteAndInsuranceChoice() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        viewModel.calculate()
        runCurrent()
        viewModel.selectTierInsurance(false)
        runCurrent()
        val validResult = viewModel.result.value!!

        val refresh = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = refresh
        viewModel.refreshTierQuote()
        runCurrent()
        assertTrue(viewModel.state.value.tierQuoteActionLoading)
        assertEquals(true, repo.tierRequests.last().insuranceDeclined)
        refresh.completeExceptionally(IllegalStateException("current refresh failed"))
        runCurrent()

        assertEquals(validResult, viewModel.result.value)
        assertEquals(false, viewModel.result.value?.insuranceChoice)
        assertFalse(viewModel.state.value.tierQuoteActionLoading)
        assertEquals("Quote refresh failed", viewModel.state.value.alert?.title)
    }

    /**
     * Release audit 2026-09-24: only Express shows the lbs/kg picker, but its
     * unit converted every method's weight. Kg picked on Express, then 10 typed
     * into Airdrop's "Actual Weight (lbs)", was priced as 22.05 lb. Neither
     * switching method nor setting the unit on Airdrop may convert it.
     */
    @Test
    fun aKgUnitNeverConvertsAirdropsPoundField() = runTest(dispatcher) {
        for (pickKg in listOf<CalculatorViewModel.() -> Unit>(
            {
                onMethodSelected(ShippingMethod.EXPRESS)
                onWeightUnitSelected(WeightUnit.KG)
                onMethodSelected(ShippingMethod.STANDARD)
            },
            {
                onMethodSelected(ShippingMethod.STANDARD)
                onWeightUnitSelected(WeightUnit.KG)
            },
        )) {
            val repo = RecordingRepository()
            val viewModel = CalculatorViewModel(repo)
            viewModel.pickKg()
            viewModel.onInvoiceChange("150")
            viewModel.onActualWeightChange("10")
            viewModel.calculate()
            advanceUntilIdle()

            assertEquals("Airdrop's field is pounds", 10.0, repo.tierRequests.single().weightLbs, 0.001)
            assertEquals(WeightUnit.LBS, viewModel.result.value!!.weightUnit)
        }
    }

    /** Express shows the picker, so its kg weight is still converted. */
    @Test
    fun expressStillConvertsAKgWeight() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        viewModel.onMethodSelected(ShippingMethod.EXPRESS)
        viewModel.onWeightUnitSelected(WeightUnit.KG)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("10")
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals(10.0 / 0.453592, repo.weight!!, 0.001)
        assertEquals(WeightUnit.KG, viewModel.result.value!!.weightUnit)
    }

    /** A retired customs item also leaves the suggestions on screen, and their count. */
    @Test
    fun aRetiredCustomsItemLeavesTheSuggestionsOnScreen() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        val laptop = CalcDutyRate(42, "Laptop computer", 20.0)
        val bag = CalcDutyRate(43, "Laptop bag", 20.0)
        viewModel.onProductSelected(laptop)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")
        val pending = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = pending
        viewModel.calculate()
        runCurrent()

        // The customer searches again while the quote is in flight.
        repo.searchAnswer = listOf(laptop, bag)
        viewModel.onProductChange("Laptop")
        advanceUntilIdle()
        assertEquals(DutyRateSearchState.Results(listOf(laptop, bag)), viewModel.state.value.searchState)

        pending.completeExceptionally(
            TierQuoteException(
                errorCode = "DUTY_RATE_UNAVAILABLE",
                message = "The selected customs item is no longer available. Pick another item.",
            ),
        )
        runCurrent()

        assertEquals(DutyRateSearchState.Results(listOf(bag), totalMatches = 1), viewModel.state.value.searchState)
    }

    /**
     * A refused customs item is unpicked — but only if it is still the pick.
     * One chosen while the refused quote was in flight is the customer's new
     * choice and must survive.
     */
    @Test
    fun aCustomsItemRefusedMidFlightDoesNotUnpickTheNewChoice() = runTest(dispatcher) {
        val repo = RecordingRepository()
        val viewModel = CalculatorViewModel(repo)
        val laptop = CalcDutyRate(42, "Laptop computer", 20.0)
        val bag = CalcDutyRate(43, "Laptop bag", 20.0)
        viewModel.onProductSelected(laptop)
        viewModel.onInvoiceChange("150")
        viewModel.onActualWeightChange("5.5")

        val pending = CompletableDeferred<TierQuote>()
        repo.nextTierResponse = pending
        viewModel.calculate()
        runCurrent()
        assertEquals(42, repo.tierRequests.last().customDutyRateId)
        viewModel.onProductSelected(bag)
        pending.completeExceptionally(
            TierQuoteException(
                errorCode = "DUTY_RATE_UNAVAILABLE",
                message = "The selected customs item is no longer available. Pick another item.",
            ),
        )
        runCurrent()

        assertEquals("Customs item unavailable", viewModel.state.value.alert?.title)
        assertEquals(bag, viewModel.state.value.selectedDutyRate)
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

        val TIER_QUOTE = TierQuote(
            quoteReference = "Q-7H2K9M4P6R8T",
            customerTier = "SAVR",
            method = "AIR",
            destination = "JM",
            currency = "USD",
            lineItems = listOf(
                TierLineItem("base_shipping", "Base shipping", 20.0),
                TierLineItem("fuel_surcharge", "Fuel surcharge", 2.0),
                TierLineItem("insurance", "Insurance", 1.5),
                TierLineItem("aircoins_credit", "AirCoins credit", 0.0),
            ),
            subtotal = 23.5,
            totalDue = 23.5,
            status = "active",
            isExpired = false,
            expiresAt = "2099-01-02T03:04:05Z",
            insuranceOptions = TierInsuranceOptions(
                insuredValue = 150.0,
                ratePer100 = 1.0,
                blockSize = 100,
                blocks = 2,
                premium = 1.5,
                maxCoverage = null,
                coveredValue = 150.0,
                canDecline = true,
                mandatory = false,
                explicitRequired = true,
            ),
            insuranceChoiceRequired = true,
            aircoinsEarned = 4.0,
        )
    }
}
