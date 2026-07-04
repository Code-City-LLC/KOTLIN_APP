package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.AuthorizedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthorizedUsersUiState(
    val activeUsers: List<AuthorizedUser> = emptyList(),
    val inactiveUsers: List<AuthorizedUser> = emptyList(),
    val loading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val error: String? = null,
)

/** FigmaAuthorizedUsersViewController: GET /authorized-users, refetch on focus. */
class AuthorizedUsersViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AuthorizedUsersUiState())
    val state: StateFlow<AuthorizedUsersUiState> = _state

    private var isLoading = false

    init {
        load(showLoadingState = true)
    }

    fun refresh() = load(showLoadingState = !_state.value.hasLoadedOnce)

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun load(showLoadingState: Boolean) {
        if (isLoading) return
        isLoading = true
        if (showLoadingState) _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            repository.authorizedUsers()
                .onSuccess { users ->
                    _state.update {
                        it.copy(
                            activeUsers = users.active,
                            inactiveUsers = users.inactive,
                            loading = false,
                            hasLoadedOnce = true,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.toUserMessage())
                    }
                }
            isLoading = false
        }
    }
}
