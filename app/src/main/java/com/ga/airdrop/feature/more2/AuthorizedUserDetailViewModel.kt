package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.AuthorizedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthorizedUserDetailUiState(
    val user: AuthorizedUser? = null,
    val loading: Boolean = false,
    val processing: Boolean = false,
    val emptyMessage: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
)

/**
 * FigmaAuthorizedUserDetailViewController: GET /authorized-users/{id}, then
 * PATCH {id}/activate | {id}/deactivate and DELETE {id}.
 */
class AuthorizedUserDetailViewModel(
    private val userId: Int,
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AuthorizedUserDetailUiState())
    val state: StateFlow<AuthorizedUserDetailUiState> = _state

    init {
        load()
    }

    fun load() {
        if (userId <= 0) {
            _state.update { it.copy(emptyMessage = "User ID is missing.") }
            return
        }
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, emptyMessage = null) }
        viewModelScope.launch {
            repository.authorizedUser(userId)
                .onSuccess { user ->
                    _state.update { it.copy(loading = false, user = user) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, user = null, emptyMessage = e.toUserMessage())
                    }
                }
        }
    }

    fun togglePrimary() {
        val user = _state.value.user ?: return
        val activate = !user.isActive
        runMutation {
            if (activate) repository.activateAuthorizedUser(userId)
            else repository.deactivateAuthorizedUser(userId)
        }
    }

    fun delete() {
        if (_state.value.processing) return
        _state.update { it.copy(processing = true) }
        viewModelScope.launch {
            repository.deleteAuthorizedUser(userId)
                .onSuccess { _state.update { it.copy(processing = false, deleted = true) } }
                .onFailure { e ->
                    _state.update { it.copy(processing = false, error = e.toUserMessage()) }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun runMutation(action: suspend () -> Result<*>) {
        if (_state.value.processing) return
        _state.update { it.copy(processing = true) }
        viewModelScope.launch {
            action()
                .onSuccess {
                    _state.update { it.copy(processing = false) }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(processing = false, error = e.toUserMessage()) }
                }
        }
    }
}
