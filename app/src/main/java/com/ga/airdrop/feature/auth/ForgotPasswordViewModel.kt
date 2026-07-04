package com.ga.airdrop.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Server message from the forgot-password response, shown in the popup. */
    val sentMessage: String? = null,
    val sent: Boolean = false,
)

/**
 * Forgot-password flow — Swift ForgotViewController / RN useForgotPassword:
 * validate the email, POST /auth/forgot-password, then surface the success
 * pop-up and return to Log In.
 */
class ForgotPasswordViewModel(
    private val repository: AuthRepository =
        AuthRepository(com.ga.airdrop.core.network.ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val email = current.email.trim()
        if (email.isEmpty()) {
            _state.update { it.copy(error = "You must enter email") }
            return
        }
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            _state.update { it.copy(error = "Please enter a valid email address.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.forgotPassword(email)
                .onSuccess { response ->
                    _state.update {
                        it.copy(loading = false, sent = true, sentMessage = response.message)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Unable to send reset link. Please try again.",
                        )
                    }
                }
        }
    }
}
