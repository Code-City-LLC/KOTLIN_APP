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
import java.util.IdentityHashMap
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
    private var customerTierHook: () -> Unit = {}
    private var serviceTiersHook: () -> Unit = {}
    private var capturedProvenance: AuthTokenStore.RequestProvenance? = null
    private lateinit var sessionBoundary: TestSessionBoundary

    private val reader = object : CustomerTierReader {
        override suspend fun serviceTiers(): Result<List<ServiceTier>> {
            callLog += "serviceTiers"
            val result = Result.success(
                listOf(ServiceTier(code = "GOLD", benefitsSummary = listOf("Gold row"))),
            )
            serviceTiersHook()
            return result
        }

        override suspend fun customerTier(): Result<CustomerTier> {
            callLog += "customerTier"
            val result = tierResponses.removeFirstOrNull() ?: Result.success(rubyOffersGold)
            customerTierHook()
            return result
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
        customerTierHook = {}
        serviceTiersHook = {}
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
        sessionBoundary.replaceCurrent("session-b", emit = true)
        advanceUntilIdle()

        assertEquals(0, patchCalls)
        assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
        assertEquals(TierChangePhase.Idle, vm.state.value.changePhase)
        assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
    }

    @Test
    fun sameSessionRevisionRotationBeforeDispatchMakesZeroPatchCallsAndEndsError() =
        runTest(dispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.changeTier("GOLD", "Gold Standard")
            sessionBoundary.rotateRevision()
            advanceUntilIdle()

            assertEquals(0, patchCalls)
            assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
            assertNull(vm.state.value.changeSuccessName)
        }

    @Test
    fun replacementDuringPatchCannotPublishSuccessOrRunConfirmationGet() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        callLog.clear()
        patchHook = { sessionBoundary.replaceCurrent("session-b", emit = true) }

        vm.changeTier("GOLD", "Gold Standard")
        advanceUntilIdle()

        assertEquals(1, patchCalls)
        assertEquals("patch:GOLD", callLog.firstOrNull())
        // Emitting a production-faithful replacement starts one fresh-session
        // load. Exactly one customer/catalog read belongs to that reload; an
        // extra read would mean the replaced mutation also ran confirmation.
        assertEquals(1, callLog.count { it == "customerTier" })
        assertEquals(1, callLog.count { it == "serviceTiers" })
        assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
        assertEquals(TierChangePhase.Idle, vm.state.value.changePhase)
        assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
        assertNull(vm.state.value.changeSuccessName)
    }

    @Test
    fun sameSessionRevisionRotationDuringPatchStillConfirmsAndEndsSuccess() =
        runTest(dispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            callLog.clear()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            patchHook = sessionBoundary::rotateRevision

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(listOf("patch:GOLD", "customerTier", "serviceTiers"), callLog)
            assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals("Gold Standard", vm.state.value.changeSuccessName)
            assertEquals(42L, capturedProvenance?.revision)
            assertEquals(43L, sessionBoundary.currentRevision())
        }

    @Test
    fun sameSessionRevisionRotationDuringConfirmationCannotStrandWorking() =
        runTest(dispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            callLog.clear()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            customerTierHook = sessionBoundary::rotateRevision

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(listOf("patch:GOLD", "customerTier", "serviceTiers"), callLog)
            assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertEquals(43L, sessionBoundary.currentRevision())
        }

    @Test
    fun nullAccountBindingBeforeDispatchMakesZeroPatchCallsAndEndsError() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()

            vm.changeTier("GOLD", "Gold Standard")
            sessionBoundary.bindCurrentAccount(1)
            advanceUntilIdle()

            assertEquals(0, patchCalls)
            assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
            assertNull(vm.state.value.changeSuccessName)
        }

    @Test
    fun nullAccountBindingBetweenOwnerCaptureAndRequestResolutionRetriesSafely() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()

            vm.changeTier("GOLD", "Gold Standard")
            sessionBoundary.beforeNextRequestOwner { bindCurrentAccount(1) }
            advanceUntilIdle()

            assertEquals(0, patchCalls)
            assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
            assertNull(vm.state.value.changeSuccessName)
        }

    @Test
    fun nullAccountBindingBeforeInitialLoadingPublicationRetriesExactOwner() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            sessionBoundary.beforeNextApplicationAfterValidation { bindCurrentAccount(1) }

            val vm = newViewModel()
            advanceUntilIdle()

            assertEquals(TierCatalogStatus.Ready, vm.state.value.catalogStatus)
            assertEquals(TierResolutionStatus.Resolved, vm.state.value.resolutionStatus)
            assertEquals(1, sessionBoundary.currentAccountId())
        }

    @Test
    fun nullAccountBindingDuringPatchContinuesConfirmationAndSucceeds() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            callLog.clear()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            patchHook = { sessionBoundary.bindCurrentAccount(1) }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(1, patchCalls)
            assertEquals(listOf("patch:GOLD", "customerTier", "serviceTiers"), callLog)
            assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals("Gold Standard", vm.state.value.changeSuccessName)
        }

    @Test
    fun nullAccountBindingDuringConfirmationStillFoldsAndSucceeds() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            callLog.clear()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            customerTierHook = { sessionBoundary.bindCurrentAccount(1) }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(listOf("patch:GOLD", "customerTier", "serviceTiers"), callLog)
            assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals("Gold Standard", vm.state.value.changeSuccessName)
        }

    @Test
    fun replacementSessionDuringConfirmationCannotPublishSuccess() =
        runTest(dispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            callLog.clear()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            customerTierHook = { sessionBoundary.replaceCurrent("session-b", emit = true) }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(1, patchCalls)
            assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals(TierChangePhase.Idle, vm.state.value.changePhase)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertNull(vm.state.value.changeSuccessName)
        }

    @Test
    fun nullAccountBindingImmediatelyBeforeFinalFoldRetriesAndPublishesSuccess() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            callLog.clear()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            serviceTiersHook = {
                sessionBoundary.beforeNextApplicationAfterValidation {
                    bindCurrentAccount(1)
                }
            }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(listOf("patch:GOLD", "customerTier", "serviceTiers"), callLog)
            assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals("Gold Standard", vm.state.value.changeSuccessName)
            assertEquals(1, sessionBoundary.currentAccountId())
            assertEquals(1, sessionBoundary.maxRunWhileActionInvocations())
        }

    @Test
    fun replacementImmediatelyBeforeFinalFoldCannotPublishOldSuccess() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            serviceTiersHook = {
                sessionBoundary.beforeNextApplicationAfterValidation {
                    replaceCurrent("session-b", emit = true)
                }
            }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertNotEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals(TierChangePhase.Idle, vm.state.value.changePhase)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertNull(vm.state.value.changeSuccessName)
        }

    @Test
    fun provisionalBindRollbackBeforeDispatchCannotStrandWorking() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            patchResult = Result.failure(RuntimeException("patch failed"))

            vm.changeTier("GOLD", "Gold Standard")
            sessionBoundary.beginProvisionalBind(accountId = 1, persist = false)
            advanceUntilIdle()

            assertEquals(1, patchCalls)
            assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertNull(sessionBoundary.currentAccountId())
        }

    @Test
    fun consecutiveProvisionalBindRollbacksCannotConsumeValidationOrStrandWorking() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            patchResult = Result.failure(RuntimeException("patch failed"))

            vm.changeTier("GOLD", "Gold Standard")
            sessionBoundary.beginConsecutiveFailedBinds(1, 2)
            advanceUntilIdle()

            assertEquals(1, patchCalls)
            assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertNull(vm.state.value.changeSuccessName)
            assertNull(sessionBoundary.currentAccountId())
            assertEquals(1, sessionBoundary.maxRunWhileActionInvocations())
        }

    @Test
    fun provisionalBindRollbackDuringPatchFailurePublishesExactError() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            patchResult = Result.failure(RuntimeException("patch failed"))
            patchHook = {
                sessionBoundary.beginProvisionalBind(accountId = 1, persist = false)
            }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(1, patchCalls)
            assertEquals(TierChangePhase.Error, vm.state.value.changePhase)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertNull(sessionBoundary.currentAccountId())
        }

    @Test
    fun provisionalBindRollbackBeforeConfirmedFoldStillPublishesOnce() =
        runTest(dispatcher) {
            sessionBoundary = TestSessionBoundary(initialAccountId = null)
            val vm = newViewModel()
            advanceUntilIdle()
            tierResponses.addLast(Result.success(rubyOffersGold.copy(currentTier = "GOLD")))
            serviceTiersHook = {
                sessionBoundary.beginProvisionalBind(accountId = 1, persist = false)
            }

            vm.changeTier("GOLD", "Gold Standard")
            advanceUntilIdle()

            assertEquals(TierChangePhase.Success, vm.state.value.changePhase)
            assertEquals("Gold Standard", vm.state.value.changeSuccessName)
            assertNotEquals(TierChangePhase.Working, vm.state.value.changePhase)
            assertNull(sessionBoundary.currentAccountId())
            assertEquals(1, sessionBoundary.maxRunWhileActionInvocations())
        }

    private class TestSessionBoundary(initialAccountId: Int? = 1) : AuthenticatedSessionBoundary {
        private var owner: AuthenticatedSessionOwner? =
            AuthenticatedSessionOwner(sessionId = "session-a", accountId = initialAccountId)
        private var revision: Long = 42L
        private val ownerFlow = MutableStateFlow(owner)
        private var requestOwnerHook: TestSessionBoundary.() -> Unit = {}
        private var afterValidationHook: TestSessionBoundary.() -> Unit = NoOpHook
        private var beforeRunWhileHook: TestSessionBoundary.() -> Unit = NoOpHook
        private var insideRunWhileAction = false
        private var provisionalBindPersists: Boolean? = null
        private val failedBindsAfterRunWhile = ArrayDeque<Int>()
        private val runWhileActionInvocations = IdentityHashMap<() -> Boolean, Int>()

        override val changes: Flow<AuthenticatedSessionOwner?> = ownerFlow

        override fun capture(): AuthenticatedSessionOwner? = owner

        // Production isCurrent/apply guard only the session id. Tests must
        // not accidentally grant them stronger full-owner semantics.
        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean =
            this.owner?.sessionId == owner.sessionId

        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (this.owner?.sessionId != owner.sessionId) return false
            action()
            return true
        }

        override fun runWhileCurrent(
            owner: AuthenticatedSessionOwner,
            action: () -> Boolean,
        ): Boolean {
            settleProvisionalBind()
            if (beforeRunWhileHook !== NoOpHook) {
                val hook = beforeRunWhileHook
                beforeRunWhileHook = NoOpHook
                hook()
            }
            val result = if (this.owner != owner) {
                false
            } else {
                val invocationCount = (runWhileActionInvocations[action] ?: 0) + 1
                runWhileActionInvocations[action] = invocationCount
                check(invocationCount == 1) { "runWhileCurrent action executed more than once" }
                insideRunWhileAction = true
                val actionResult = try {
                    action()
                } finally {
                    insideRunWhileAction = false
                }
                actionResult && this.owner == owner
            }
            // Model another caller acquiring transitionLock immediately after
            // this exact validation releases it. The completed result remains
            // linearized; the next call must independently survive its bind.
            failedBindsAfterRunWhile.removeFirstOrNull()?.let { accountId ->
                beginProvisionalBind(accountId = accountId, persist = false)
            }
            return result
        }

        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? {
            val hook = requestOwnerHook
            requestOwnerHook = {}
            hook()
            if (this.owner != owner) return null
            val result = AuthenticatedRequestOwner(
                session = owner,
                provenance = AuthTokenStore.RequestProvenance(
                    revision = revision,
                    sessionId = owner.sessionId,
                    accountId = owner.accountId,
                ),
            )
            if (insideRunWhileAction && afterValidationHook !== NoOpHook) {
                beforeRunWhileHook = afterValidationHook
                afterValidationHook = NoOpHook
            }
            return result
        }

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean {
            if (this.owner != owner) return false
            this.owner = owner.copy(accountId = accountId)
            ownerFlow.value = this.owner
            return true
        }

        fun replaceCurrent(sessionId: String, emit: Boolean = false) {
            owner = AuthenticatedSessionOwner(sessionId = sessionId, accountId = 2)
            revision += 1
            if (emit) ownerFlow.value = owner
        }

        fun rotateRevision() {
            revision += 1
        }

        fun bindCurrentAccount(accountId: Int) {
            check(owner?.accountId == null)
            owner = owner?.copy(accountId = accountId)
            ownerFlow.value = owner
        }

        fun beforeNextRequestOwner(action: TestSessionBoundary.() -> Unit) {
            requestOwnerHook = action
        }

        fun beforeNextApplicationAfterValidation(action: TestSessionBoundary.() -> Unit) {
            afterValidationHook = action
        }

        fun beginProvisionalBind(accountId: Int, persist: Boolean) {
            check(owner?.accountId == null)
            owner = owner?.copy(accountId = accountId)
            provisionalBindPersists = persist
        }

        fun beginConsecutiveFailedBinds(firstAccountId: Int, vararg followingAccountIds: Int) {
            check(failedBindsAfterRunWhile.isEmpty())
            failedBindsAfterRunWhile.addAll(followingAccountIds.toList())
            beginProvisionalBind(accountId = firstAccountId, persist = false)
        }

        fun currentRevision(): Long = revision

        fun currentAccountId(): Int? = owner?.accountId

        fun maxRunWhileActionInvocations(): Int =
            runWhileActionInvocations.values.maxOrNull() ?: 0

        private fun settleProvisionalBind() {
            val persists = provisionalBindPersists ?: return
            provisionalBindPersists = null
            if (persists) {
                ownerFlow.value = owner
            } else {
                owner = owner?.copy(accountId = null)
            }
        }

        private companion object {
            val NoOpHook: TestSessionBoundary.() -> Unit = {}
        }
    }
}
