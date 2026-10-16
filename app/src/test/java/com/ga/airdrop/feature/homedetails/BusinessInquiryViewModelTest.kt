package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.*
import com.ga.airdrop.data.model.*
import com.ga.airdrop.data.repo.BusinessRateInquirySender
import com.ga.airdrop.feature.contacts.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessInquiryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = AirdropUser(id = 1, firstName = "Jordan", lastName = "Customer", accountNumber = "ARD-1")
    private lateinit var boundary: Boundary
    private val models = mutableListOf<BusinessInquiryViewModel>()
    private val requests = mutableListOf<BusinessRateInquiryRequest>()
    private lateinit var chat: Chat
    private var inboxResult = Result.success(BusinessRateInquiryResult(123, "created"))
    private var onInbox: () -> Unit = {}

    @Before fun setup() { Dispatchers.setMain(dispatcher); boundary = Boundary(); chat = Chat() }
    @After fun cleanup() { models.forEach { it.close() }; Dispatchers.resetMain() }

    private fun model(profile: suspend () -> AirdropUser = { user }): BusinessInquiryViewModel =
        BusinessInquiryViewModel(profile, BusinessRateInquirySender { request, owner ->
            assertEquals(1, owner.accountId)
            requests += request; onInbox(); inboxResult
        }, { chat }, boundary, "9717246e-2049-4f67-a740-c9b6f64a851d").also(models::add)

    @Test fun profilePrefillsAndEitherVolumeQualifies() = runTest(dispatcher) {
        val vm = model(); vm.openForm(); advanceUntilIdle()
        assertEquals("Jordan Customer", vm.state.value.businessName)
        vm.edit(cubicFeet = "106.7"); vm.submit(true); advanceUntilIdle()
        assertEquals(BusinessStage.Success, vm.state.value.stage)
        assertEquals(null, requests.single().airDropMonthlyPounds)
        assertEquals(106.7, requests.single().seaDropMonthlyCubicFeet!!, 0.001)
        assertEquals("canonical-conversation", vm.state.value.conversationId)
        assertEquals("123", chat.metadata.getValue("laravel_message_id"))
    }

    @Test fun profileBindsAnUnresolvedAccountBeforeSending() = runTest(dispatcher) {
        boundary.unbind()
        val vm = model(); vm.openForm(); advanceUntilIdle()
        assertEquals(BusinessPhase.Ready, vm.state.value.phase)
        assertEquals(1, boundary.capture()?.accountId)
        vm.edit(pounds = "400"); vm.submit(true); advanceUntilIdle()
        assertEquals(BusinessStage.Success, vm.state.value.stage)
        assertEquals(1, requests.size)
    }

    @Test fun consentDeclineMakesNoInboxOrChatRequest() = runTest(dispatcher) {
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        vm.submit(false); assertTrue(vm.state.value.needsConsent)
        vm.declineConsent(); advanceUntilIdle()
        assertEquals(0, chat.starts); assertTrue(requests.isEmpty()); assertEquals(0, chat.sends)
        assertTrue(vm.state.value.error!!.contains("not sent"))
    }

    @Test fun repeatedSubmitWhileWorkingSendsOnce() = runTest(dispatcher) {
        chat.startGate = CompletableDeferred()
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        vm.submit(true); vm.submit(true); runCurrent(); assertEquals(1, chat.starts)
        chat.startGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, requests.size); assertEquals(1, chat.sends)
    }

    @Test fun partialChatFailurePreservesRetryIdentityAndExplainsSavedInbox() = runTest(dispatcher) {
        chat.failSend = true
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        vm.submit(true); advanceUntilIdle()
        assertEquals(BusinessPhase.Failed, vm.state.value.phase)
        assertTrue(vm.state.value.error!!.contains("reached the support inbox"))
        chat.failSend = false; inboxResult = Result.success(BusinessRateInquiryResult(123, "updated"))
        vm.submit(true); advanceUntilIdle()
        assertEquals(requests[0].clientRequestId, requests[1].clientRequestId)
        assertEquals("update", chat.metadata["request_action"])
        assertEquals(BusinessStage.Success, vm.state.value.stage)
    }

    @Test fun exactCustomerHistorySkipsDuplicateChatButStillConfirmsInbox() = runTest(dispatcher) {
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        val body = inquiryDetails(validatedInquiry(vm.state.value, "unused"))
        chat.history = listOf(message("Business-rate inquiry — follow up now.\n\n$body"))
        vm.submit(true); advanceUntilIdle()
        assertEquals(1, requests.size); assertEquals(0, chat.sends)
        assertTrue(vm.state.value.alreadySubmitted)
    }

    @Test fun agentEchoDoesNotSuppressCustomerSubmission() = runTest(dispatcher) {
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        chat.history = listOf(message(inquiryDetails(validatedInquiry(vm.state.value, "unused")), "agent"))
        vm.submit(true); advanceUntilIdle(); assertEquals(1, chat.sends)
    }

    @Test fun inboxFailureNeverSendsChatOrClaimsSuccess() = runTest(dispatcher) {
        inboxResult = Result.failure(IllegalStateException("422"))
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        vm.submit(true); advanceUntilIdle()
        assertEquals(0, chat.sends); assertEquals(BusinessPhase.Failed, vm.state.value.phase)
        assertFalse(vm.state.value.error!!.contains("reached the support inbox"))
    }

    @Test fun accountReplacementDiscardsProfileAndPendingConsent() = runTest(dispatcher) {
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400"); vm.submit(false)
        boundary.replace(); var remembered = false
        vm.acceptConsent { remembered = true }; advanceUntilIdle()
        assertFalse(remembered); assertTrue(requests.isEmpty())
        assertEquals(BusinessPhase.Expired, vm.state.value.phase)
        assertNull(vm.state.value.account)
    }

    @Test fun accountSwitchAfterInboxCannotSendOrPublishIntoNewAccount() = runTest(dispatcher) {
        onInbox = { boundary.replace() }
        val vm = model(); vm.openForm(); advanceUntilIdle(); vm.edit(pounds = "400")
        vm.submit(true); advanceUntilIdle()
        assertEquals(1, requests.size); assertEquals(0, chat.sends)
        assertEquals(BusinessPhase.Expired, vm.state.value.phase)
    }

    @Test fun profileFailureCanRetryAndDoesNotSubmit() = runTest(dispatcher) {
        var fail = true
        val vm = model { if (fail) error("offline") else user }
        vm.openForm(); advanceUntilIdle(); assertEquals(BusinessPhase.ProfileFailed, vm.state.value.phase)
        assertTrue(requests.isEmpty()); fail = false; vm.loadProfile(); advanceUntilIdle()
        assertEquals(BusinessPhase.Ready, vm.state.value.phase)
    }

    @Test fun invalidAndNonFiniteInputsFailBeforeNetwork() = runTest(dispatcher) {
        val vm = model(); vm.openForm(); advanceUntilIdle()
        for (value in listOf("-1", "NaN", "Infinity", "1e9999", "1000001", "399.9", "words")) {
            vm.edit(pounds = value); vm.submit(true); advanceUntilIdle()
            assertTrue(value, requests.isEmpty()); assertNotNull(vm.state.value.error)
        }
        vm.edit(pounds = "1,000"); vm.submit(true); advanceUntilIdle(); assertEquals(1, requests.size)
    }

    private fun message(body: String, type: String = "customer") =
        AutoPilotAppChatMessage("m1", body, type, null, type, null, null)

    private inner class Chat : BusinessInquiryChat {
        var starts = 0; var sends = 0; var failSend = false
        var startGate: CompletableDeferred<Unit>? = null
        var history = emptyList<AutoPilotAppChatMessage>()
        var metadata = emptyMap<String, String>()
        override suspend fun currentUser() = user
        override suspend fun startSession(user: AirdropUser): AutoPilotAppChatSession {
            starts++; startGate?.await()
            return AutoPilotAppChatSession("initial-conversation", null, null, null, history)
        }
        override suspend fun messages(conversationId: String) = history
        override suspend fun sendMessage(conversationId: String, body: String, user: AirdropUser) =
            error("Inquiry must use the existing chat transport with its metadata")
        override suspend fun sendInquiry(conversationId: String, body: String, user: AirdropUser,
                                         metadata: Map<String, String>): AutoPilotAppChatSendResult {
            sends++; this.metadata = metadata
            if (failSend) error("offline")
            return AutoPilotAppChatSendResult("canonical-conversation", null, null)
        }
    }

    private class Boundary : AuthenticatedSessionBoundary {
        private val flow = MutableStateFlow<AuthenticatedSessionOwner?>(AuthenticatedSessionOwner("session-a", 1))
        override val changes = flow
        override fun capture() = flow.value
        override fun isCurrent(owner: AuthenticatedSessionOwner) = flow.value?.sessionId == owner.sessionId
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false; action(); return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) =
            flow.value == owner && action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) = if (flow.value == owner)
            AuthenticatedRequestOwner(owner, AuthTokenStore.RequestProvenance(42, owner.sessionId, owner.accountId)) else null
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean {
            if (flow.value != owner || (owner.accountId != null && owner.accountId != accountId)) return false
            flow.value = owner.copy(accountId = accountId); return true
        }
        fun unbind() { flow.value = AuthenticatedSessionOwner("session-a") }
        fun replace() { flow.value = AuthenticatedSessionOwner("session-b", 2) }
    }
}
