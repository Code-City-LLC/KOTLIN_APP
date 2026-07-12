package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeOption
import com.ga.airdrop.data.model.TierChangeResult
import com.ga.airdrop.data.repo.CustomerTierReader
import com.ga.airdrop.data.repo.TierChanger
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
import org.junit.Before
import org.junit.Test

/**
 * ViewModel-level proofs the gate demanded (#22836-1/-4): the PATCH is never
 * fired for un-offered codes (ZERO network calls), and Success is only
 * claimed AFTER the authoritative GET confirms — request order PATCH→GET is
 * asserted on a shared call log, and a failing/contradicting confirmation
 * yields Error, never a false Success.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoldPriorityViewModelChangeTest {

    private val dispatcher = StandardTestDispatcher()

    /** Shared, ordered log across the reader and changer fakes. */
    private val callLog = mutableListOf<String>()

    private val rubyOffersGold = CustomerTier(
        currentTier = "RUBY",
        canChange = true,
        availableChanges = listOf(
            TierChangeOption(code = "GOLD", name = "Gold Standard", laneRank = 3, direction = "upgrade"),
        ),
    )

    private var tierResponses: ArrayDeque<Result<CustomerTier>> = ArrayDeque()
    private var patchResult: Result<TierChangeResult> = Result.success(TierChangeResult(status = "applied"))
    private var patchCalls = 0

    private val reader = object : CustomerTierReader {
        override suspend fun serviceTiers(): Result<List<ServiceTier>> {
            callLog += "serviceTiers"
            return Result.success(
                listOf(ServiceTier(code = "GOLD", benefitsSummary = listOf("Gold row"))),
            )
        }

        override suspend fun customerTier(): Result<CustomerTier> {
            callLog += "customerTier"
            return tierResponses.removeFirstOrNull() ?: Result.success(rubyOffersGold)
        }
    }

    private val changer = TierChanger { code ->
        callLog += "patch:$code"
        patchCalls++
        patchResult
    }

    private fun newViewModel() = GoldPriorityViewModel(
        tierReader = reader,
        fallbackTierNameReader = { null },
        tierChanger = changer,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        callLog.clear()
        patchCalls = 0
        tierResponses = ArrayDeque()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unofferedCodeMakesZeroPatchCalls() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle() // initial load: RUBY, offers = [GOLD]

        vm.changeTier("DIAM", "Diamond Elite") // never offered
        advanceUntilIdle()

        assertEquals(0, patchCalls)
        assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
        assertNull(vm.state.value.changeSuccessName)
    }

    @Test
    fun successOnlyAfterGetConfirmsRequestedTier_patchThenGetOrder() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        callLog.clear()
        // Confirmation GET returns the NEW tier; subsequent refresh GETs too.
        tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        // Order proof: PATCH strictly before the confirmation GET.
        assertEquals("patch:GOLD", callLog.getOrNull(0))
        assertEquals("customerTier", callLog.getOrNull(1))
        assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
        assertEquals("Gold Standard", vm.state.value.changeSuccessName)
    }

    @Test
    fun failedConfirmationGetIsErrorNotSuccess() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        tierResponses.addLast(Result.failure(RuntimeException("network down")))

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(1, patchCalls) // PATCH fired…
        assertEquals(TierChangePhase.Error, vm.state.value.changePhase) // …but no false success.
        assertNull(vm.state.value.changeSuccessName)
    }

    @Test
    fun contradictingConfirmationWithoutEffectiveAtIsError() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        // Backend said applied (no effective_at), but GET still shows RUBY.
        patchResult = Result.success(TierChangeResult(status = "applied", effectiveAt = null))
        tierResponses.addLast(Result.success(rubyOffersGold)) // still RUBY

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
    }

    @Test
    fun scheduledChangeWithEffectiveAtIsSuccessDespiteUnchangedTier() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        patchResult = Result.success(
            TierChangeResult(status = "scheduled", effectiveAt = "2026-08-01T00:00:00Z"),
        )
        tierResponses.addLast(Result.success(rubyOffersGold)) // unchanged until effective

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
    }
}
