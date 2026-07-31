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
internal val LIVE_CHAT_POLL_SCHEDULE_MILLIS =
    List(10) { 3_000L } + List(9) { 10_000L }
private const val LIVE_CHAT_INITIAL_POLL_ATTEMPTS = 10

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
        pollJob?.cancel()
        pollJob = null
        // An in-flight send must die too: deliver() writes the canonical
        // conversationId back into state when it returns, which would resurrect
        // the conversation the customer just retired.
        sendJob?.cancel()
        sendJob = null
        sessionStarting = false
        displayedRemoteMessageIds.clear()
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

    private fun applySession(user: AirdropUser, session: AutoPilotAppChatSession) {
        val agent = session.assignedAgentName?.takeIf { it.isNotBlank() } ?: "Nirvana"
        session.messages.forEach(::markDisplayed)
        val turns = session.messages
            .filter { it.body.isNotBlank() }
            .map { it.toTurn(agent) }
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
        pollScheduleMillis.forEachIndexed { index, intervalMillis ->
            if (surfaceWaitingStatus) {
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
            val messages = try {
                repository.messages(conversationId)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Throwable) {
                if (surfaceWaitingStatus) {
                    _state.update { it.copy(status = LIVE_CHAT_RECONNECTING_STATUS) }
                }
                return@forEachIndexed
            }
            if (
                appendRemoteAssistantMessages(
                    messages = messages,
                    inlineAssistantMessageId = inlineAssistantMessageId,
                    inlineAssistantFingerprint = inlineAssistantFingerprint,
                )
            ) {
                return true
            }
        }
        return false
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
