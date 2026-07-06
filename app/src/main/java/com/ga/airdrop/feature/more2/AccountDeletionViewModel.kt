package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Verified credentials handed from the Account Deletion screen to the Reason
 * screen — the Compose counterpart of the Swift init(email:password:) push
 * (route args must not carry a password).
 */
internal object AccountDeletionFlow {
    var email: String = ""
    var password: String = ""

    fun hasVerifiedCredentials(): Boolean =
        email.isNotBlank() && password.isNotBlank()

    fun clear() {
        email = ""
        password = ""
    }
}

data class AccountDeletionUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val verified: Boolean = false,
    val error: String? = null,
)

/**
 * FigmaAccountDeletionViewController: verify credentials (re-run /auth/login
 * without persisting the token), then continue to the reason screen.
 */
class AccountDeletionViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AccountDeletionUiState())
    val state: StateFlow<AccountDeletionUiState> = _state

    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }
    fun togglePasswordVisibility() =
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun consumeVerified() = _state.update { it.copy(verified = false) }

    fun confirm() {
        val s = _state.value
        if (s.loading) return
        val email = s.email.trim()
        val password = s.password

        if (email.isEmpty()) {
            _state.update { it.copy(error = "Please enter your email address") }
            return
        }
        if (password.isEmpty()) {
            _state.update { it.copy(error = "Please enter your password") }
            return
        }

        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            repository.verifyCredentials(email, password)
                .onSuccess { valid ->
                    if (valid) {
                        AccountDeletionFlow.email = email
                        AccountDeletionFlow.password = password
                        _state.update { it.copy(loading = false, verified = true) }
                    } else {
                        _state.update {
                            it.copy(loading = false, error = "Invalid email or password.")
                        }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
        }
    }
}
