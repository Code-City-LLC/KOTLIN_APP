package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeOption
import com.ga.airdrop.data.model.TierChangeResult
import com.ga.airdrop.data.repo.CustomerTierReader
import com.ga.airdrop.data.repo.TierChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    private var patchHook: () -> Unit = {}
    private var capturedProvenance: AuthTokenStore.RequestProvenance? = null
    private lateinit var sessionBoundary: TestSessionBoundary

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

    private val changer = TierChanger { code, expectedSession ->
        callLog += "patch:$code"
        patchCalls++
        capturedProvenance = expectedSession
        patchHook()
        patchResult
    }

    private fun newViewModel() = GoldPriorityViewModel(
        tierReader = reader,
        fallbackTierNameReader = { null },
        sessionBoundary = sessionBoundary,
        tierChanger = changer,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        callLog.clear()
        patchCalls = 0
        patchResult = Result.success(TierChangeResult(status = "applied"))
        patchHook = {}
        capturedProvenance = null
        tierResponses = ArrayDeque()
        sessionBoundary = TestSessionBoundary()
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
    fun replacementBeforeEventCaptureMakesZeroPatchCalls() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        sessionBoundary.replaceCurrent("session-b")

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(0, patchCalls)
        assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
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
        assertEquals(42L, capturedProvenance?.revision)
        assertEquals("session-a", capturedProvenance?.sessionId)
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
    fun contradictingConfirmationIsErrorEvenWithEffectiveAt() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        // REAL backend shape (gate #22867-1): Laravel "applied" responses
        // ALWAYS carry effective_at — it must never convert a contradictory
        // GET into a success.
        patchResult = Result.success(
            TierChangeResult(status = "applied", effectiveAt = "2026-07-12T18:00:00Z"),
        )
        tierResponses.addLast(Result.success(rubyOffersGold)) // GET still RUBY

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
        assertNull(vm.state.value.changeSuccessName)
    }

    @Test
    fun successFoldsConfirmedStateBeforeAnnouncing() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        val gold = rubyOffersGold.copy(currentTier = "GOLD")
        tierResponses.addLast(Result.success(gold)) // confirmation GET

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        // Success is announced only alongside the already-folded pager state
        // (#22867-2): resolved index moved to gold before/with Success.
        assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
        assertEquals(
            tierPages.indexOfFirst { it.id == "gold" },
            vm.state.value.resolvedTierIndex,
        )
    }

    @Test
    fun replacementBeforeCoroutineDispatchMakesZeroPatchCalls() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.changeTier("GOLD", "Gold Standard")
        // Do not emit the owner change: this isolates the explicit
        // pre-dispatch provenance revalidation from collector cancellation.
        sessionBoundary.replaceCurrent("session-b")
        advanceUntilIdle()

        assertEquals(0, patchCalls)
        assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
    }

    @Test
    fun replacementDuringPatchCannotPublishSuccessOrRunConfirmationGet() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        callLog.clear()
        patchHook = { sessionBoundary.replaceCurrent("session-b") }

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(1, patchCalls)
        assertEquals(listOf("patch:GOLD"), callLog)
        assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
        assertNull(vm.state.value.changeSuccessName)
    }

    private class TestSessionBoundary : AuthenticatedSessionBoundary {
        private var owner: AuthenticatedSessionOwner? =
            AuthenticatedSessionOwner(sessionId = "session-a", accountId = 1)
        private var revision: Long = 42L
        private val ownerFlow = MutableStateFlow(owner)

        override val changes: Flow<AuthenticatedSessionOwner?> = ownerFlow

        override fun capture(): AuthenticatedSessionOwner? = owner

        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = this.owner == owner

        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (this.owner != owner) return false
            action()
            return true
        }

        override fun runWhileCurrent(
            owner: AuthenticatedSessionOwner,
            action: () -> Boolean,
        ): Boolean = this.owner == owner && action() && this.owner == owner

        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? {
            if (this.owner != owner) return null
            return AuthenticatedRequestOwner(
                session = owner,
                provenance = AuthTokenStore.RequestProvenance(
                    revision = revision,
                    sessionId = owner.sessionId,
                    accountId = owner.accountId,
                ),
            )
        }

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean {
            if (this.owner != owner) return false
            this.owner = owner.copy(accountId = accountId)
            ownerFlow.value = this.owner
            return true
        }

        fun replaceCurrent(sessionId: String) {
            owner = AuthenticatedSessionOwner(sessionId = sessionId, accountId = 2)
            revision += 1
        }
    }
}
