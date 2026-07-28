package com.ga.airdrop.feature.contacts

import com.ga.airdrop.data.model.AirdropUser
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveAgentChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `send adopts canonical uuid for polling handoff and later messages`() =
        runTest(dispatcher) {
            val externalId = "airdrop-user-42"
            val canonicalId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1"
            val sentConversationIds = mutableListOf<String>()
            val polledConversationIds = mutableListOf<String>()
            val source = FakeDataSource(
                sessionConversationId = externalId,
                onSend = { conversationId, _, _ ->
                    sentConversationIds += conversationId
                    AutoPilotAppChatSendResult(
                        conversationId = canonicalId,
                        reply = null,
                        message = null,
                    )
                },
                onMessages = { conversationId ->
                    polledConversationIds += conversationId
                    listOf(
                        assistantMessage(
                            id = "reply-${polledConversationIds.size}",
                            body = "Reply ${polledConversationIds.size}",
                        ),
                    )
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = {},
            )

            viewModel.start()
            advanceUntilIdle()
            assertEquals(externalId, viewModel.state.value.conversationId)

            viewModel.onInputChange("Where is my package?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(listOf(externalId), sentConversationIds)
            assertEquals(listOf(canonicalId), polledConversationIds)
            assertEquals(canonicalId, viewModel.state.value.conversationId)
            assertEquals("Reply 1", viewModel.state.value.messages.last().body)

            viewModel.onInputChange("Can I speak to a human?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(listOf(externalId, canonicalId), sentConversationIds)
            assertEquals(listOf(canonicalId, canonicalId), polledConversationIds)
            assertEquals(canonicalId, viewModel.state.value.conversationId)
        }

    @Test
    fun `poll continues after two transient failures and appends the recovered reply`() =
        runTest(dispatcher) {
            val canonicalId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1"
            val delays = mutableListOf<Long>()
            var pollCount = 0
            val source = FakeDataSource(
                sessionConversationId = "airdrop-user-42",
                onSend = { _, _, _ ->
                    AutoPilotAppChatSendResult(canonicalId, null, null)
                },
                onMessages = {
                    pollCount += 1
                    if (pollCount <= 2) {
                        throw IllegalStateException("transient")
                    }
                    listOf(assistantMessage(id = "reply-3", body = "Recovered reply"))
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = { delays += it },
            )

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Where is my package?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(3, pollCount)
            assertEquals(listOf(3_000L, 3_000L, 3_000L), delays)
            assertEquals(canonicalId, viewModel.state.value.conversationId)
            assertEquals("Recovered reply", viewModel.state.value.messages.last().body)
            assertFalse(viewModel.state.value.sending)
        }

    @Test
    fun `poll backoff covers relay timeout and surfaces delayed status`() =
        runTest(dispatcher) {
            val delays = mutableListOf<Long>()
            var pollCount = 0
            val source = FakeDataSource(
                sessionConversationId = "airdrop-user-42",
                onSend = { _, _, _ ->
                    AutoPilotAppChatSendResult(
                        conversationId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
                        reply = null,
                        message = null,
                    )
                },
                onMessages = {
                    pollCount += 1
                    emptyList()
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = { delays += it },
            )

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Hello?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(19, pollCount)
            assertEquals(
                List(10) { 3_000L } + List(9) { 10_000L },
                delays,
            )
            assertEquals(120_000L, delays.sum())
            assertFalse(viewModel.state.value.sending)
            assertEquals(LIVE_CHAT_DELAYED_STATUS, viewModel.state.value.status)
        }

    @Test
    fun `composer remains editable while lifecycle poll is waiting`() =
        runTest(dispatcher) {
            val delayStarted = CompletableDeferred<Unit>()
            val releaseDelay = CompletableDeferred<Unit>()
            val source = FakeDataSource(
                sessionConversationId = "airdrop-user-42",
                onSend = { _, _, _ ->
                    AutoPilotAppChatSendResult(
                        conversationId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
                        reply = null,
                        message = null,
                    )
                },
                onMessages = { emptyList() },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = {
                    delayStarted.complete(Unit)
                    releaseDelay.await()
                },
                pollScheduleMillis = listOf(3_000L),
            )

            viewModel.start()
            runCurrent()
            viewModel.onInputChange("First message")
            viewModel.send()
            runCurrent()
            delayStarted.await()

            assertFalse(viewModel.state.value.sending)
            assertEquals(LIVE_CHAT_WAITING_STATUS, viewModel.state.value.status)
            viewModel.onInputChange("Composer is still live")
            assertEquals("Composer is still live", viewModel.state.value.input)

            releaseDelay.complete(Unit)
            advanceUntilIdle()
            assertEquals(LIVE_CHAT_DELAYED_STATUS, viewModel.state.value.status)
        }

    @Test
    fun `poll confirmation of reply-only result stops without duplicate`() =
        runTest(dispatcher) {
            var pollCount = 0
            val source = FakeDataSource(
                sessionConversationId = "airdrop-user-42",
                onSend = { _, _, _ ->
                    AutoPilotAppChatSendResult(
                        conversationId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
                        reply = "Your package is ready.",
                        message = null,
                    )
                },
                onMessages = {
                    pollCount += 1
                    listOf(
                        assistantMessage(
                            id = "reply-confirmation",
                            body = "  YOUR   PACKAGE is ready. ",
                        ),
                    )
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = {},
            )

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Is it ready?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(1, pollCount)
            assertEquals(
                listOf("Is it ready?", "Your package is ready."),
                viewModel.state.value.messages.map { it.body },
            )
        }

    @Test
    fun `poll confirms structured inline reply by body when persisted id differs`() =
        runTest(dispatcher) {
            var pollCount = 0
            val inlineMessage = assistantMessage(
                id = "customer-message-42:reply",
                body = "I found your package.",
            )
            val source = FakeDataSource(
                sessionConversationId = "airdrop-user-42",
                onSend = { _, _, _ ->
                    AutoPilotAppChatSendResult(
                        conversationId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
                        reply = null,
                        message = inlineMessage,
                    )
                },
                onMessages = {
                    pollCount += 1
                    listOf(
                        assistantMessage(
                            id = "persisted-outbound-9001",
                            body = "  I FOUND   your package. ",
                        ),
                    )
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = {},
            )

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Find my package")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(1, pollCount)
            assertEquals(
                listOf("Find my package", "I found your package."),
                viewModel.state.value.messages.map { it.body },
            )
        }

    @Test
    fun `inbound send echo is not rendered as an assistant reply`() =
        runTest(dispatcher) {
            var pollCount = 0
            val source = FakeDataSource(
                sessionConversationId = "airdrop-user-42",
                onSend = { _, body, _ ->
                    AutoPilotAppChatSendResult(
                        conversationId = "1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
                        reply = null,
                        message = AutoPilotAppChatMessage(
                            id = "inbound-message-42",
                            body = body,
                            direction = "inbound",
                            senderName = null,
                            senderType = "customer",
                            createdAt = null,
                            deliveryStatus = "delivered",
                        ),
                    )
                },
                onMessages = {
                    pollCount += 1
                    listOf(assistantMessage(id = "reply-42", body = "I found your package."))
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = {},
            )

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Find my package")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(1, pollCount)
            assertEquals(
                listOf("Find my package", "I found your package."),
                viewModel.state.value.messages.map { it.body },
            )
        }

    /**
     * ⚠️ `messages()` is now called ONE extra time, before any send: the view
     * model loads the persisted thread when the chat opens, so leaving the app
     * and returning no longer shows an empty conversation.
     *
     * These scenarios all model a BRAND NEW conversation, so that first call
     * returns empty — which is what the server returns for one. Letting the
     * shared `onMessages` lambda answer it instead would hand the view model an
     * assistant reply to a message the test has not sent yet, which cannot
     * happen in production and would make these tests assert fiction.
     */
    /**
     * THE BUG Kemar reported: "if I get off the app, the chat disappears, and it
     * disappears when I get back on the app."
     *
     * The conversation is persistent server-side — the same conversation id is
     * returned every time — so the turns were never lost, they were simply never
     * FETCHED. `repository.messages()` was only ever called from the poll loop,
     * and polling only starts after you SEND. Reopening the chat therefore
     * rendered whatever `startSession` happened to carry, which is nothing.
     */
    @Test
    fun `reopening the chat restores the persisted conversation instead of showing it empty`() =
        runTest(dispatcher) {
            val source = FakeDataSource(
                sessionConversationId = "conv-persistent",
                onSend = { _, _, _ ->
                    AutoPilotAppChatSendResult(conversationId = "conv-persistent", reply = null, message = null)
                },
                onMessages = { emptyList() },
                // What the server holds from the customer's earlier visit.
                onHistory = {
                    listOf(
                        assistantMessage(id = "past-1", body = "Your package is at the warehouse."),
                    )
                },
            )
            val viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = {},
            )

            viewModel.start()
            advanceUntilIdle()

            val bodies = viewModel.state.value.messages.map { it.body }
            assertEquals(
                "the earlier conversation must come back when the chat is reopened",
                listOf("Your package is at the warehouse."),
                bodies,
            )
        }

    private class FakeDataSource(
        private val sessionConversationId: String,
        private val onSend: suspend (String, String, AirdropUser) -> AutoPilotAppChatSendResult,
        private val onMessages: suspend (String) -> List<AutoPilotAppChatMessage>,
        private val onHistory: suspend (String) -> List<AutoPilotAppChatMessage> = { emptyList() },
    ) : LiveAgentChatDataSource {

        private var historyLoaded = false

        override suspend fun currentUser() = AirdropUser(
            id = 42,
            accountNumber = "GA-42",
            firstName = "Chase",
            lastName = "Camp",
        )

        override suspend fun startSession(user: AirdropUser) =
            AutoPilotAppChatSession(
                conversationId = sessionConversationId,
                channelId = "channel-1",
                status = "open",
                assignedAgentName = "Nirvana",
                messages = emptyList(),
            )

        override suspend fun sendMessage(
            conversationId: String,
            body: String,
            user: AirdropUser,
        ) = onSend(conversationId, body, user)

        override suspend fun messages(conversationId: String): List<AutoPilotAppChatMessage> {
            if (!historyLoaded) {
                historyLoaded = true
                return onHistory(conversationId)
            }
            return onMessages(conversationId)
        }
    }

    companion object {
        private fun assistantMessage(id: String, body: String) =
            AutoPilotAppChatMessage(
                id = id,
                body = body,
                direction = "outbound",
                senderName = "Nirvana",
                senderType = "agent",
                createdAt = null,
                deliveryStatus = "sent",
            )
    }
}
