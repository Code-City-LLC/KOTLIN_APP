package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.api.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// RN canonical list — keep verbatim.
internal val DELETION_REASONS = listOf(
    "I have some issue in using the app",
    "Just making another account",
    "I don't need this account anymore",
    "Privacy concerns",
    "Too many notifications",
    "Other",
)

data class AccountDeletionReasonUiState(
    val selectedReason: String? = null,
    val deleting: Boolean = false,
    val showConfirmModal: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

/**
 * FigmaAccountDeletionReasonViewController: reason radios + confirmation
 * modal + POST /user/deactivate-account, then full local logout.
 */
class AccountDeletionReasonViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AccountDeletionReasonUiState())
    val state: StateFlow<AccountDeletionReasonUiState> = _state

    fun selectReason(reason: String) = _state.update { it.copy(selectedReason = reason) }

    fun requestDelete() {
        if (_state.value.selectedReason == null) {
            _state.update {
                it.copy(error = "Please select a reason for deleting your account")
            }
            return
        }
        _state.update { it.copy(showConfirmModal = true) }
    }

    fun dismissModal() = _state.update { it.copy(showConfirmModal = false) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun confirmDelete() {
        if (_state.value.deleting) return
        _state.update { it.copy(showConfirmModal = false, deleting = true) }
        viewModelScope.launch {
            // The API ignores the reason (not part of the Laravel payload) —
            // kept client-side, matching RN/Swift.
            repository.deactivateAccount(password = AccountDeletionFlow.password)
                .onSuccess {
                    // Full logout hygiene: token + session caches.
                    // RECONCILE: also clear FCM token + any local stores
                    // (background image pref etc.) once those land on Android.
                    AuthTokenStore.clear()
                    SessionStore.clear()
                    AccountDeletionFlow.clear()
                    _state.update { it.copy(deleting = false, deleted = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(deleting = false, error = e.toUserMessage()) }
                }
        }
    }
}
