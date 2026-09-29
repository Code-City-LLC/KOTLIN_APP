package com.ga.airdrop.feature.contacts

import com.ga.airdrop.data.model.AirdropUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Chat state in Live Chat: ended / expired chats, "End Chat & Start Fresh"
 * closing the chat in AutoPilot, and the one-minute cap on "Waiting for
 * Nirvana…" (AutoPilot CRM Enhancements items 7, 8 and 14).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveChatConversationStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Tracker ----------------------------------------------------------

    @Test
    fun `each end reason is announced once`() {
        val expected = mapOf(
            "agent" to "This chat was closed by our team. Send a message to start a new chat.",
            "expired" to "This chat closed after a period of inactivity. Send a message to start a new chat.",
            "customer" to "You ended this chat. Send a message to start a new chat.",
        )
        expected.forEach { (endedBy, text) ->
            val tracker = LiveChatConversationTracker()
            assertEquals(emptyList<LiveChatConversationTracker.Notice>(), tracker.apply(open(CHAT_A), CHAT_A))

            val notices = tracker.apply(closed(CHAT_A, endedBy), CHAT_A)

            assertEquals(listOf(LiveChatConversationTracker.Notice.Ended(endedBy)), notices)
            assertEquals(listOf(text), notices.map(LiveChatConversationTracker::text))
            assertTrue(tracker.isClosed(CHAT_A))
            // Later polls of the same closed chat add nothing.
            assertEquals(emptyList<LiveChatConversationTracker.Notice>(), tracker.apply(closed(CHAT_A, endedBy), CHAT_A))
        }
    }

    @Test
    fun `state for another conversation is ignored`() {
        val tracker = LiveChatConversationTracker()
        tracker.apply(open(CHAT_B), CHAT_B)

        assertEquals(emptyList<LiveChatConversationTracker.Notice>(), tracker.apply(closed(CHAT_A, "expired"), CHAT_B))
        assertFalse(tracker.isClosed(CHAT_B))
    }

    @Test
    fun `a team member joining is announced once, one already on the chat is not`() {
        val tracker = LiveChatConversationTracker()
        tracker.apply(open(CHAT_A), CHAT_A)

        val joined = tracker.apply(open(CHAT_A, agent = " Ramiela "), CHAT_A)

        assertEquals(listOf(LiveChatConversationTracker.Notice.AgentJoined("Ramiela")), joined)
        assertEquals(listOf("Ramiela joined the chat"), joined.map(LiveChatConversationTracker::text))
        assertEquals(emptyList<LiveChatConversationTracker.Notice>(), tracker.apply(open(CHAT_A, agent = "Ramiela"), CHAT_A))

        val restored = LiveChatConversationTracker()
        assertEquals(emptyList<LiveChatConversationTracker.Notice>(), restored.apply(open(CHAT_A, agent = "Ramiela"), CHAT_A))
        assertEquals("Ramiela", restored.agentName)
    }

    // --- View model -------------------------------------------------------

    @Test
    fun `a chat closed by the team while the customer waits says so and stops polling`() =
        runTest(dispatcher) {
            var polls = 0
            val source = StateFakeDataSource(
                onSend = { _, _ -> sendResult(CHAT_A) },
                onThread = {
                    polls += 1
                    if (polls < 2) {
                        AutoPilotAppChatThread(emptyList(), open(CHAT_A))
                    } else {
                        AutoPilotAppChatThread(
                            listOf(assistantMessage("m1", "Thanks, all sorted.")),
                            closed(CHAT_A, "agent"),
                        )
                    }
                },
            )
            val viewModel = LiveAgentChatViewModel(repository = source, pollDelay = {})

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Is it ready?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(2, polls)
            val bodies = viewModel.state.value.messages.map { it.role to it.body }
            assertEquals(
                listOf(
                    LiveChatRole.Customer to "Is it ready?",
                    LiveChatRole.Assistant to "Thanks, all sorted.",
                    LiveChatRole.Notice to "This chat was closed by our team. Send a message to start a new chat.",
                ),
                bodies,
            )
            assertNull(viewModel.state.value.status)
        }

    @Test
    fun `the next message after a close moves to the new conversation`() =
        runTest(dispatcher) {
            var sends = 0
            val source = StateFakeDataSource(
                onSend = { _, _ ->
                    sends += 1
                    if (sends == 1) sendResult(CHAT_A) else sendResult(CHAT_B)
                },
                onThread = { id ->
                    if (id == CHAT_A) {
                        AutoPilotAppChatThread(emptyList(), closed(CHAT_A, "expired"))
                    } else {
                        AutoPilotAppChatThread(listOf(assistantMessage("m2", "Hi again!")), open(CHAT_B))
                    }
                },
            )
            val viewModel = LiveAgentChatViewModel(repository = source, pollDelay = {})

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("First")
            viewModel.send()
            advanceUntilIdle()
            viewModel.onInputChange("Second")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(CHAT_B, viewModel.state.value.conversationId)
            assertEquals(
                listOf(
                    "First",
                    "This chat closed after a period of inactivity. Send a message to start a new chat.",
                    "Second",
                    "Hi again!",
                ),
                viewModel.state.value.messages.map { it.body },
            )
        }

    @Test
    fun `end chat closes it in AutoPilot before starting fresh`() =
        runTest(dispatcher) {
            val source = StateFakeDataSource(
                onSend = { _, _ -> sendResult(CHAT_A, reply = "Hello!") },
                onThread = { AutoPilotAppChatThread(emptyList(), open(CHAT_A)) },
            )
            val viewModel = LiveAgentChatViewModel(repository = source, pollDelay = {})

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Hi")
            viewModel.send()
            advanceUntilIdle()
            viewModel.endChatAndStartFresh()
            advanceUntilIdle()

            assertEquals(listOf(CHAT_A), source.ended)
            assertEquals(2, source.sessions)
            assertEquals(emptyList<LiveAgentChatTurn>(), viewModel.state.value.messages)
            assertEquals(SESSION_ID, viewModel.state.value.conversationId)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a failed end keeps the chat and says so`() =
        runTest(dispatcher) {
            val source = StateFakeDataSource(
                onSend = { _, _ -> sendResult(CHAT_A, reply = "Hello!") },
                onThread = { AutoPilotAppChatThread(emptyList(), open(CHAT_A)) },
                onEnd = { throw LiveAgentChatException("offline", LiveAgentChatErrorKind.Transport) },
            )
            val viewModel = LiveAgentChatViewModel(repository = source, pollDelay = {})

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Hi")
            viewModel.send()
            advanceUntilIdle()
            viewModel.endChatAndStartFresh()
            advanceUntilIdle()

            assertEquals(1, source.sessions)
            assertEquals(CHAT_A, viewModel.state.value.conversationId)
            assertEquals(listOf("Hi", "Hello!"), viewModel.state.value.messages.map { it.body })
            assertEquals(LIVE_CHAT_END_FAILED_ERROR, viewModel.state.value.error)
        }

    @Test
    fun `a chat that never reached AutoPilot just starts fresh`() =
        runTest(dispatcher) {
            val source = StateFakeDataSource(
                onSend = { _, _ -> error("not sent") },
                onThread = { AutoPilotAppChatThread(emptyList()) },
            )
            val viewModel = LiveAgentChatViewModel(repository = source, pollDelay = {})

            viewModel.start()
            advanceUntilIdle()
            viewModel.endChatAndStartFresh()
            advanceUntilIdle()

            assertEquals(emptyList<String>(), source.ended)
            assertEquals(2, source.sessions)
        }

    @Test
    fun `waiting never shows for more than a minute and polling carries on`() =
        runTest(dispatcher) {
            val statusAtPoll = mutableListOf<Pair<Long, String?>>()
            var elapsed = 0L
            lateinit var viewModel: LiveAgentChatViewModel
            val source = StateFakeDataSource(
                onSend = { _, _ -> sendResult(CHAT_A) },
                onThread = {
                    statusAtPoll += elapsed to viewModel.state.value.status
                    AutoPilotAppChatThread(emptyList(), open(CHAT_A))
                },
            )
            viewModel = LiveAgentChatViewModel(
                repository = source,
                pollDelay = { elapsed += it },
            )

            viewModel.start()
            advanceUntilIdle()
            viewModel.onInputChange("Hello?")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(19, statusAtPoll.size)
            assertEquals(120_000L, elapsed)
            val waitingLines = setOf(LIVE_CHAT_WAITING_STATUS, LIVE_CHAT_STILL_WAITING_STATUS)
            statusAtPoll.forEach { (at, status) ->
                // A poll at `at` ran after the status for that slot was set,
                // which happens before its delay: the slot began at `at - delay`.
                if (status in waitingLines) {
                    assertTrue("still waiting at ${at}ms", at <= LIVE_CHAT_WAITING_TIMEOUT_MILLIS)
                }
            }
            assertTrue(statusAtPoll.any { (at, status) -> at > 60_000L && status == LIVE_CHAT_DELAYED_STATUS })
            assertEquals(LIVE_CHAT_DELAYED_STATUS, viewModel.state.value.status)
        }

    private class StateFakeDataSource(
        private val onSend: suspend (String, String) -> AutoPilotAppChatSendResult,
        private val onThread: suspend (String) -> AutoPilotAppChatThread,
        private val onEnd: suspend (String) -> AutoPilotAppChatConversationState? = { closed(it, "customer") },
    ) : LiveAgentChatDataSource {
        var sessions = 0
        val ended = mutableListOf<String>()

        override suspend fun currentUser() = AirdropUser(
            id = 42,
            accountNumber = "GA-42",
            firstName = "Chase",
            lastName = "Camp",
        )

        override suspend fun startSession(user: AirdropUser): AutoPilotAppChatSession {
            sessions += 1
            // No open conversation: AutoPilot hands back the customer's id.
            return AutoPilotAppChatSession(
                conversationId = SESSION_ID,
                channelId = "channel-1",
                status = null,
                assignedAgentName = "Nirvana",
                messages = emptyList(),
            )
        }

        override suspend fun sendMessage(conversationId: String, body: String, user: AirdropUser) =
            onSend(conversationId, body)

        override suspend fun messages(conversationId: String) = thread(conversationId).messages

        override suspend fun thread(conversationId: String): AutoPilotAppChatThread =
            if (conversationId == SESSION_ID) AutoPilotAppChatThread(emptyList()) else onThread(conversationId)

        override suspend fun endChat(conversationId: String): AutoPilotAppChatConversationState? {
            val state = onEnd(conversationId)
            ended += conversationId
            return state
        }
    }

    companion object {
        private const val SESSION_ID = "airdrop-user-42"
        private const val CHAT_A = "5f0c3d1e-8a4b-4c55-9d6e-2b7a1c9e0f11"
        private const val CHAT_B = "9b1c7e44-2f0a-4d3b-8c6e-5a4f3e2d1c0b"

        private fun open(id: String, agent: String? = null) =
            AutoPilotAppChatConversationState(id = id, status = "open", closed = false, agentName = agent)

        private fun closed(id: String, endedBy: String?) =
            AutoPilotAppChatConversationState(id = id, status = "closed", closed = true, endedBy = endedBy)

        private fun sendResult(id: String, reply: String? = null) =
            AutoPilotAppChatSendResult(
                conversationId = id,
                reply = reply,
                message = null,
                conversation = open(id),
            )

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
