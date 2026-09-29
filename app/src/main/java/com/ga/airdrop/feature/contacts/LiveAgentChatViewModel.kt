package com.ga.airdrop.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.AirdropUser
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class LiveChatRole {
    Customer,
    Assistant,
    /** A centred line about the chat itself: ended, expired, a team member joined. */
    Notice,
}

internal data class LiveAgentChatTurn(
    val id: String = UUID.randomUUID().toString(),
    val role: LiveChatRole,
    val body: String,
    val senderName: String? = null,
    /** A customer turn whose send failed — the UI offers a tap-to-retry. */
    val failed: Boolean = false,
)

internal data class LiveAgentChatUiState(
    val input: String = "",
    val messages: List<LiveAgentChatTurn> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val status: String? = null,
    val conversationId: String? = null,
    val agentDisplayName: String = "Nirvana",
    val customerDisplayName: String = "You",
    val historyCount: Int = 0,
)

internal const val LIVE_CHAT_WAITING_STATUS = "Waiting for Nirvana…"
internal const val LIVE_CHAT_STILL_WAITING_STATUS = "Still waiting for Nirvana…"
internal const val LIVE_CHAT_RECONNECTING_STATUS = "Reconnecting to Nirvana…"
internal const val LIVE_CHAT_DELAYED_STATUS = "Message sent — reply delayed"
internal const val LIVE_CHAT_ENDING_STATUS = "Ending chat…"
internal const val LIVE_CHAT_END_FAILED_ERROR = "Couldn't end the chat. Please try again."
internal val LIVE_CHAT_POLL_SCHEDULE_MILLIS =
    List(10) { 3_000L } + List(9) { 10_000L }
private const val LIVE_CHAT_INITIAL_POLL_ATTEMPTS = 10

/**
 * "Waiting for Nirvana…" never shows longer than this without a reply (CRM
 * Enhancements item 14). Polling carries on, so a late reply — including
 * AutoPilot's "can't answer right now" fallback — still shows up.
 */
internal const val LIVE_CHAT_WAITING_TIMEOUT_MILLIS = 60_000L

private val CANONICAL_CONVERSATION_ID =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * What Live Chat tells the customer as the chat's state changes: closed by the
 * team, expired after they went quiet, ended by them, or a team member joining
 * (AutoPilot CRM Enhancements items 7 and 8). Mirrors iOS
 * `LiveChatConversationTracker` (FigmaRouteViewController.swift).
 */
internal class LiveChatConversationTracker {
    sealed interface Notice {
        data class AgentJoined(val name: String) : Notice
        data class Ended(val endedBy: String?) : Notice
    }

    var conversationId: String? = null
        private set
    var agentName: String? = null
        private set
    var isClosed: Boolean = false
        private set

    /**
     * Folds the state returned for the active conversation into the tracker
     * and returns what to tell the customer. State for any other conversation
     * (a poll still in flight for a chat since replaced by a new one) is
     * ignored.
     */
    fun apply(state: AutoPilotAppChatConversationState?, activeConversationId: String?): List<Notice> {
        if (state == null) return emptyList()
        val stateId = state.id.cleaned()
        val activeId = activeConversationId.cleaned()
        if (stateId != null && activeId != null && stateId != activeId) return emptyList()
        val id = stateId ?: activeId ?: return emptyList()
        val name = state.agentName.cleaned()
        if (id != conversationId) {
            // First state for this conversation (opened, or the new chat after
            // a close): nothing to announce unless it has ended.
            conversationId = id
            agentName = name
            isClosed = state.isClosed
            return if (state.isClosed) listOf(Notice.Ended(state.endedBy.cleaned())) else emptyList()
        }
        val notices = mutableListOf<Notice>()
        if (!state.isClosed && agentName == null && name != null) {
            notices += Notice.AgentJoined(name)
        }
        if (name != null) agentName = name
        if (state.isClosed && !isClosed) {
            notices += Notice.Ended(state.endedBy.cleaned())
        }
        isClosed = state.isClosed
        return notices
    }

    fun isClosed(conversationId: String?): Boolean {
        val id = conversationId.cleaned() ?: return false
        return isClosed && id == this.conversationId
    }

    companion object {
        fun text(notice: Notice): String =
            when (notice) {
                is Notice.AgentJoined -> "${notice.name} joined the chat"
                is Notice.Ended -> {
                    val reason = when (notice.endedBy?.lowercase()) {
                        "customer" -> "You ended this chat."
                        "agent" -> "This chat was closed by our team."
                        "expired" -> "This chat closed after a period of inactivity."
                        else -> "This chat has ended."
                    }
                    "$reason Send a message to start a new chat."
                }
            }

        private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

internal class LiveAgentChatViewModel(
    private val repository: LiveAgentChatDataSource = LiveAgentChatRepository(),
    private val pollDelay: suspend (Long) -> Unit = { delay(it) },
    private val pollScheduleMillis: List<Long> = LIVE_CHAT_POLL_SCHEDULE_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveAgentChatUiState())
    internal val state: StateFlow<LiveAgentChatUiState> = _state

    private var currentUser: AirdropUser? = null
    private val displayedRemoteMessageIds = linkedSetOf<String>()
    private var sessionStarting = false
    private var pollJob: Job? = null
    // Tracked so endChatAndStartFresh() can cancel an in-flight send; otherwise
    // the retired conversationId is written back over the fresh conversation.
    private var sendJob: Job? = null
    private var endJob: Job? = null
    /** Open / closed state of the active conversation, and who is on it. */
    private var tracker = LiveChatConversationTracker()

    fun start() {
        val snapshot = _state.value
        if (snapshot.conversationId != null || sessionStarting || snapshot.loading) return
        viewModelScope.launch {
            runCatching {
                sessionStarting = true
                _state.update { it.copy(loading = true, error = null, status = null) }
                val user = resolveCurrentUser()
                val session = repository.startSession(user)
                applySession(user, session)
                // The conversation is PERSISTENT server-side — the same
                // conversation id comes back on every session call — so the
                // customer's earlier turns still exist. They were simply never
                // fetched: repository.messages() was only ever called from the
                // poll loop, and polling only starts after you SEND. So leaving
                // the app and coming back showed an empty thread even though the
                // history was sitting on the server. Load it explicitly here.
                loadHistory(session.conversationId)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = LiveAgentChatRepository.userFacingStatus(err),
                        status = null,
                    )
                }
            }
            sessionStarting = false
        }
    }

    /**
     * "End Chat & Start Fresh" (Kemar) — wipe the conversation on this device
     * and open a new one.
     *
     * Everything that identifies the old conversation has to go together: the
     * poll job (it would keep appending replies from the retired
     * conversationId), the de-dupe ledger (stale ids would suppress the new
     * conversation's turns), and the whole UI state including any half-typed
     * input and sticky error/status banner. Clearing the state alone would
     * leave a live poller writing into a "fresh" screen.
     */
    fun endChatAndStartFresh() {
        if (endJob?.isActive == true) return
        // The conversation is closed in AutoPilot first, so the team sees it
        // end and the next session really is new (CRM Enhancements item 8) —
        // otherwise the server hands the same open conversation straight back.
        // A chat that never reached AutoPilot, or has already closed, just
        // starts fresh. If the end call fails nothing is wiped: the customer
        // is never told the chat ended when it did not.
        val conversationId = _state.value.conversationId
            ?.trim()
            ?.takeIf { CANONICAL_CONVERSATION_ID.matches(it) && !tracker.isClosed(it) }
        if (conversationId == null) {
            startFresh()
            return
        }
        _state.update { it.copy(status = LIVE_CHAT_ENDING_STATUS, error = null) }
        endJob = viewModelScope.launch {
            try {
                repository.endChat(conversationId)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Throwable) {
                _state.update { it.copy(status = null, error = LIVE_CHAT_END_FAILED_ERROR) }
                return@launch
            }
            startFresh()
        }
    }

    private fun startFresh() {
        pollJob?.cancel()
        pollJob = null
        // An in-flight send must die too: deliver() writes the canonical
        // conversationId back into state when it returns, which would resurrect
        // the conversation the customer just retired.
        sendJob?.cancel()
        sendJob = null
        sessionStarting = false
        displayedRemoteMessageIds.clear()
        tracker = LiveChatConversationTracker()
        _state.value = LiveAgentChatUiState()
        start()
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun send() {
        val body = _state.value.input.trim()
        if (body.isEmpty() || _state.value.sending) return

        val turn = LiveAgentChatTurn(
            role = LiveChatRole.Customer,
            body = body,
            senderName = _state.value.customerDisplayName,
        )
        _state.update { it.copy(input = "", messages = it.messages + turn) }
        deliver(turn)
    }

    /** Re-attempt a customer message whose earlier send failed. */
    fun resend(turnId: String) {
        if (_state.value.sending) return
        val turn = _state.value.messages.firstOrNull { it.id == turnId && it.failed } ?: return
        // Clear the failed marker while the retry is in flight.
        _state.update {
            it.copy(messages = it.messages.map { m -> if (m.id == turnId) m.copy(failed = false) else m })
        }
        deliver(turn.copy(failed = false))
    }

    private fun deliver(turn: LiveAgentChatTurn) {
        val body = turn.body
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(sending = true, error = null, status = null) }
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            var inlineAssistantMessageId: String? = null
            var inlineAssistantFingerprint: String? = null
            val canonicalConversationId = try {
                val user = resolveCurrentUser()
                val conversationId = ensureConversation(user)
                val result = repository.sendMessage(
                    conversationId = conversationId,
                    body = body,
                    user = user,
                )
                // A brand-new session may expose the customer's external id.
                // The send response is authoritative and returns the canonical
                // conversation UUID required by polling and human handoff.
                val canonicalConversationId = result.conversationId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: conversationId
                _state.update { it.copy(conversationId = canonicalConversationId) }
                // After a close this is a NEW conversation (AutoPilot starts one
                // for the customer's next message), so its state starts clean.
                applyConversationState(result.conversation)
                val returned = result.message
                when {
                    returned != null && !returned.isCustomerAuthored && returned.body.isNotBlank() -> {
                        inlineAssistantMessageId = returned.id
                        // The inline response may carry a synthetic
                        // "<inbound-id>:reply" id while polling returns the
                        // canonical persisted outbound id. Track both forms.
                        inlineAssistantFingerprint = fingerprint(returned.body)
                        markDisplayed(returned)
                        appendAssistant(returned.body, returned.senderName, returned.id)
                    }
                    !result.reply.isNullOrBlank() -> {
                        inlineAssistantFingerprint = fingerprint(result.reply)
                        appendAssistant(result.reply, _state.value.agentDisplayName)
                    }
                }
                canonicalConversationId
            } catch (err: CancellationException) {
                _state.update { it.copy(sending = false) }
                throw err
            } catch (err: Throwable) {
                // Mark this specific turn failed so it can be resent, and surface the banner.
                _state.update {
                    it.copy(
                        sending = false,
                        error = LiveAgentChatRepository.userFacingStatus(err),
                        status = null,
                        messages = it.messages.map { m -> if (m.id == turn.id) m.copy(failed = true) else m },
                    )
                }
                return@launch
            }
            // Poll in a separate lifecycle-owned job so the composer remains
            // usable throughout the bounded 30s initial + 90s extended window.
            _state.update { it.copy(sending = false) }
            startPolling(
                conversationId = canonicalConversationId,
                inlineAssistantMessageId = inlineAssistantMessageId,
                inlineAssistantFingerprint = inlineAssistantFingerprint,
            )
        }
    }

    private suspend fun resolveCurrentUser(): AirdropUser {
        currentUser?.let { return it }
        return repository.currentUser().also { user ->
            currentUser = user
            _state.update {
                it.copy(customerDisplayName = displayName(user))
            }
        }
    }

    private suspend fun ensureConversation(user: AirdropUser): String {
        _state.value.conversationId?.takeIf { it.isNotBlank() }?.let { return it }
        val session = repository.startSession(user)
        applySession(user, session)
        return session.conversationId
    }

    /**
     * ⚠️ MUST NOT WIPE A MESSAGE THE CUSTOMER HAS ALREADY TYPED AND SENT.
     *
     * `messages = turns` replaced the whole list with the server's thread. On
     * the FIRST message of a conversation the order is:
     *
     *   send()  -> optimistically appends the customer's turn, CLEARS the input
     *   deliver -> ensureConversation() -> startSession() -> applySession()
     *
     * and the session the server returns does not contain that message yet, so
     * the customer watched their own sentence vanish the instant they hit send.
     * The draft was already cleared, so it was unrecoverable — retype it, with
     * no error and nothing explaining what happened.
     *
     * Any local customer turn the server has not echoed back is carried over.
     * Matched on id AND on trimmed body, so a server echo under a different id
     * still collapses instead of doubling the message.
     */
    private fun applySession(user: AirdropUser, session: AutoPilotAppChatSession) {
        val agent = session.assignedAgentName?.takeIf { it.isNotBlank() } ?: "Nirvana"
        session.messages.forEach(::markDisplayed)
        val serverTurns = session.messages
            .filter { it.body.isNotBlank() }
            .map { it.toTurn(agent) }
        val serverIds = serverTurns.mapTo(mutableSetOf()) { it.id }
        val serverBodies = serverTurns.mapTo(mutableSetOf()) { it.body.trim() }
        val pendingLocal = _state.value.messages.filter { local ->
            local.role == LiveChatRole.Customer &&
                local.id !in serverIds &&
                local.body.trim() !in serverBodies
        }
        val turns = serverTurns + pendingLocal
        _state.update {
            it.copy(
                loading = false,
                error = null,
                status = null,
                conversationId = session.conversationId,
                agentDisplayName = agent,
                customerDisplayName = displayName(user),
                messages = turns,
                historyCount = session.messages.count { msg -> msg.body.isNotBlank() },
            )
        }
        applyConversationState(session.conversation)
    }

    /**
     * Pull the persisted thread for a conversation the customer already has.
     *
     * Best-effort on purpose: a history fetch that fails must NOT surface an
     * error or wipe what [applySession] already rendered. Chat still opens; the
     * customer just sees whatever the session returned, which is the behaviour
     * before this existed.
     */
    private suspend fun loadHistory(conversationId: String) {
        // Only fetch when the session gave us nothing. If startSession already
        // returned the thread, re-fetching would be a wasted round trip AND
        // would consume a reply the poll loop is about to look for.
        if (_state.value.messages.isNotEmpty()) return
        val thread = runCatching { repository.thread(conversationId) }.getOrNull() ?: return
        val remote = thread.messages
        val turns = remote.filter { it.body.isNotBlank() }
            .map { it.toTurn(_state.value.agentDisplayName) }
        if (turns.isNotEmpty()) {
            remote.forEach(::markDisplayed)
            // Nothing local can exist yet — this runs during start(), before
            // any send — so a straight assignment is correct and cannot drop a
            // pending turn.
            _state.update { it.copy(messages = turns, historyCount = turns.size) }
        }
        applyConversationState(thread.conversation)
    }

    private fun startPolling(
        conversationId: String,
        inlineAssistantMessageId: String?,
        inlineAssistantFingerprint: String?,
    ) {
        pollJob?.cancel()
        val awaitingReply =
            inlineAssistantMessageId == null && inlineAssistantFingerprint == null
        if (awaitingReply) {
            _state.update { it.copy(status = LIVE_CHAT_WAITING_STATUS) }
        }
        pollJob = viewModelScope.launch {
            val received = pollForReply(
                conversationId = conversationId,
                inlineAssistantMessageId = inlineAssistantMessageId,
                inlineAssistantFingerprint = inlineAssistantFingerprint,
                surfaceWaitingStatus = awaitingReply,
            )
            _state.update {
                it.copy(
                    status = when {
                        received -> null
                        awaitingReply -> LIVE_CHAT_DELAYED_STATUS
                        else -> it.status
                    },
                )
            }
        }
    }

    private suspend fun pollForReply(
        conversationId: String,
        inlineAssistantMessageId: String?,
        inlineAssistantFingerprint: String?,
        surfaceWaitingStatus: Boolean,
    ): Boolean {
        var waiting = surfaceWaitingStatus
        var elapsedMillis = 0L
        pollScheduleMillis.forEachIndexed { index, intervalMillis ->
            if (waiting && elapsedMillis >= LIVE_CHAT_WAITING_TIMEOUT_MILLIS) {
                // Never "waiting" for more than a minute (item 14). Keep
                // polling quietly: a late reply still shows up.
                waiting = false
                _state.update { it.copy(status = LIVE_CHAT_DELAYED_STATUS) }
            }
            if (waiting) {
                _state.update {
                    it.copy(
                        status = if (index < LIVE_CHAT_INITIAL_POLL_ATTEMPTS) {
                            LIVE_CHAT_WAITING_STATUS
                        } else {
                            LIVE_CHAT_STILL_WAITING_STATUS
                        },
                    )
                }
            }
            pollDelay(intervalMillis)
            elapsedMillis += intervalMillis
            val thread = try {
                repository.thread(conversationId)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Throwable) {
                if (waiting) {
                    _state.update { it.copy(status = LIVE_CHAT_RECONNECTING_STATUS) }
                }
                return@forEachIndexed
            }
            // A team member who just took the chat is announced before their
            // reply; an end is announced after the last messages.
            if (thread.conversation?.isClosed == false) {
                applyConversationState(thread.conversation)
            }
            val received = appendRemoteAssistantMessages(
                messages = thread.messages,
                inlineAssistantMessageId = inlineAssistantMessageId,
                inlineAssistantFingerprint = inlineAssistantFingerprint,
            )
            // The chat can end while the customer waits (closed by the team,
            // or expired): say so and stop polling.
            if (applyConversationState(thread.conversation) || received) {
                return true
            }
        }
        return false
    }

    /**
     * Folds AutoPilot's state for the active conversation into the transcript:
     * a centred notice when a team member joins or the chat ends. State for
     * any other conversation is ignored. Returns true when the active chat is
     * closed.
     */
    private fun applyConversationState(conversation: AutoPilotAppChatConversationState?): Boolean {
        val activeId = _state.value.conversationId
        val notices = tracker.apply(conversation, activeConversationId = activeId)
        if (notices.isNotEmpty()) {
            _state.update {
                it.copy(
                    messages = it.messages + notices.map { notice ->
                        LiveAgentChatTurn(
                            role = LiveChatRole.Notice,
                            body = LiveChatConversationTracker.text(notice),
                        )
                    },
                )
            }
        }
        return tracker.isClosed(activeId)
    }

    private fun appendRemoteAssistantMessages(
        messages: List<AutoPilotAppChatMessage>,
        inlineAssistantMessageId: String?,
        inlineAssistantFingerprint: String?,
    ): Boolean {
        val agent = _state.value.agentDisplayName
        var inlineReplyConfirmed = false
        val newTurns = messages
            .filter { !it.isCustomerAuthored && it.body.isNotBlank() }
            .mapNotNull { message ->
                val fingerprint = fingerprint(message.body)
                val confirmsInlineReply =
                    message.id == inlineAssistantMessageId ||
                        fingerprint == inlineAssistantFingerprint
                if (confirmsInlineReply) {
                    inlineReplyConfirmed = true
                    markDisplayed(message)
                    return@mapNotNull null
                }
                if (displayedRemoteMessageIds.contains(message.id)) {
                    return@mapNotNull null
                }
                markDisplayed(message)
                message.toTurn(agent)
            }
        _state.update {
            it.copy(historyCount = messages.count { msg -> msg.body.isNotBlank() })
        }
        if (newTurns.isNotEmpty()) {
            _state.update { it.copy(messages = it.messages + newTurns) }
        }
        return inlineReplyConfirmed || newTurns.isNotEmpty()
    }

    private fun appendAssistant(body: String, senderName: String?, id: String = UUID.randomUUID().toString()) {
        _state.update {
            it.copy(
                messages = it.messages + LiveAgentChatTurn(
                    id = id,
                    role = LiveChatRole.Assistant,
                    body = body,
                    senderName = senderName,
                ),
            )
        }
    }

    private fun markDisplayed(message: AutoPilotAppChatMessage) {
        displayedRemoteMessageIds += message.id
    }

    private fun AutoPilotAppChatMessage.toTurn(agent: String): LiveAgentChatTurn =
        LiveAgentChatTurn(
            id = id,
            role = if (isCustomerAuthored) LiveChatRole.Customer else LiveChatRole.Assistant,
            body = body,
            senderName = senderName?.takeIf { it.isNotBlank() }
                ?: if (isCustomerAuthored) _state.value.customerDisplayName else agent,
        )

    private fun displayName(user: AirdropUser): String {
        val fullName = listOfNotNull(user.firstName?.trim(), user.lastName?.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return fullName.ifBlank { "You" }
    }

    private fun fingerprint(value: String): String =
        value.splitToSequence(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .lowercase()
}
