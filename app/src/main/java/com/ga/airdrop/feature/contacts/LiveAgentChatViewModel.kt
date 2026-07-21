package com.ga.airdrop.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.AirdropUser
import java.util.UUID
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
    val conversationId: String? = null,
    val agentDisplayName: String = "Nirvana",
    val customerDisplayName: String = "You",
    val historyCount: Int = 0,
)

internal class LiveAgentChatViewModel(
    private val repository: LiveAgentChatRepository = LiveAgentChatRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LiveAgentChatUiState())
    internal val state: StateFlow<LiveAgentChatUiState> = _state

    private var currentUser: AirdropUser? = null
    private val displayedRemoteMessageIds = linkedSetOf<String>()
    private val pendingAssistantFingerprints = linkedSetOf<String>()
    private var sessionStarting = false

    fun start() {
        val snapshot = _state.value
        if (snapshot.conversationId != null || sessionStarting || snapshot.loading) return
        viewModelScope.launch {
            runCatching {
                sessionStarting = true
                _state.update { it.copy(loading = true, error = null) }
                val user = resolveCurrentUser()
                val session = repository.startSession(user)
                applySession(user, session)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = LiveAgentChatRepository.userFacingStatus(err),
                    )
                }
            }
            sessionStarting = false
        }
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
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val user = resolveCurrentUser()
                val conversationId = ensureConversation(user)
                val result = repository.sendMessage(
                    conversationId = conversationId,
                    body = body,
                    user = user,
                )
                val returned = result.message
                when {
                    returned != null && !returned.isCustomerAuthored && returned.body.isNotBlank() -> {
                        markDisplayed(returned)
                        appendAssistant(returned.body, returned.senderName, returned.id)
                    }
                    !result.reply.isNullOrBlank() -> {
                        pendingAssistantFingerprints += fingerprint(result.reply)
                        appendAssistant(result.reply, _state.value.agentDisplayName)
                    }
                }
                pollForReply(conversationId)
            }.onFailure { err ->
                // Mark this specific turn failed so it can be resent, and surface the banner.
                _state.update {
                    it.copy(
                        error = LiveAgentChatRepository.userFacingStatus(err),
                        messages = it.messages.map { m -> if (m.id == turn.id) m.copy(failed = true) else m },
                    )
                }
            }
            _state.update { it.copy(sending = false) }
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
                conversationId = session.conversationId,
                agentDisplayName = agent,
                customerDisplayName = displayName(user),
                messages = turns,
                historyCount = session.messages.count { msg -> msg.body.isNotBlank() },
            )
        }
    }

    private suspend fun pollForReply(conversationId: String) {
        var failures = 0
        repeat(6) {
            delay(1_500)
            runCatching {
                repository.messages(conversationId)
            }.onSuccess { messages ->
                failures = 0
                val appended = appendRemoteAssistantMessages(messages)
                if (appended) return
            }.onFailure {
                failures += 1
                if (failures >= 2) return
            }
        }
    }

    private fun appendRemoteAssistantMessages(messages: List<AutoPilotAppChatMessage>): Boolean {
        val agent = _state.value.agentDisplayName
        val newTurns = messages
            .filter { !it.isCustomerAuthored && it.body.isNotBlank() }
            .filterNot { displayedRemoteMessageIds.contains(it.id) }
            .mapNotNull { message ->
                markDisplayed(message)
                val fp = fingerprint(message.body)
                if (pendingAssistantFingerprints.remove(fp)) {
                    null
                } else {
                    message.toTurn(agent)
                }
            }
        _state.update {
            it.copy(historyCount = messages.count { msg -> msg.body.isNotBlank() })
        }
        if (newTurns.isEmpty()) return false
        _state.update { it.copy(messages = it.messages + newTurns) }
        return true
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
