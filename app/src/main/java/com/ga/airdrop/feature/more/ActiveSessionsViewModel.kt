package com.ga.airdrop.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.ActiveSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveSessionsUiState(
    val sessions: List<ActiveSession> = emptyList(),
    val loading: Boolean = false,
    val revokingId: String? = null,
    val revokingAll: Boolean = false,
    val alert: Pair<String, String>? = null,
)

/**
 * Active Sessions (signed-in devices) — lists the user's Sanctum tokens, signs
 * one out (DELETE /user/sessions/{id}), or signs out ALL other devices in one
 * call (POST /user/sessions/revoke). The current device is never revoked here
 * (Laravel guards it; clients use Logout). Bearer is carried by ApiClient.
 */
class ActiveSessionsViewModel(
    private val service: AirdropApiService = ApiClient.service,
) : ViewModel() {

    private val _state = MutableStateFlow(ActiveSessionsUiState(loading = true))
    val state: StateFlow<ActiveSessionsUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.sessions.isEmpty()) }
        viewModelScope.launch {
            runCatching { service.activeSessions() }
                .onSuccess { env ->
                    _state.update { it.copy(sessions = env.data.orEmpty(), loading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, alert = "Couldn't load sessions" to e.toUserMessage())
                    }
                }
        }
    }

    fun revoke(id: String) {
        if (_state.value.revokingId != null || _state.value.revokingAll) return
        _state.update { it.copy(revokingId = id) }
        viewModelScope.launch {
            runCatching { service.revokeSession(id) }
                .onSuccess {
                    _state.update { it.copy(revokingId = null) }
                    load()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(revokingId = null, alert = "Couldn't sign out device" to e.toUserMessage())
                    }
                }
        }
    }

    fun revokeAllOthers() {
        if (_state.value.revokingAll || _state.value.revokingId != null) return
        _state.update { it.copy(revokingAll = true) }
        viewModelScope.launch {
            runCatching { service.revokeOtherSessions() }
                .onSuccess { env ->
                    val n = env.data?.revokedCount ?: 0
                    _state.update {
                        it.copy(
                            revokingAll = false,
                            alert = "Signed out" to
                                "$n other device${if (n == 1) "" else "s"} signed out.",
                        )
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            revokingAll = false,
                            alert = "Couldn't sign out other devices" to e.toUserMessage(),
                        )
                    }
                }
        }
    }

    fun dismissAlert() = _state.update { it.copy(alert = null) }
}
