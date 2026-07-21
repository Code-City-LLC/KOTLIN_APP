package com.ga.airdrop.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.push.PushRegistrar
import com.ga.airdrop.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(
    private val repository: AuthRepository = AuthRepository(com.ga.airdrop.core.network.ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun togglePasswordVisibility() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun login() {
        val current = _state.value
        if (current.loading) return
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Enter your email and password.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.login(current.email.trim(), current.password)
                .onSuccess { response ->
                    val token = response.token
                    if (token.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = response.message ?: "Unable to log in. Please try again.",
                            )
                        }
                    } else {
                        AuthTokenStore.save(token, response.user?.id)
                        // Swift FigmaLoginViewController:504-511 parity: the FCM
                        // token minted before login is replayed to
                        // /device-tokens/register now that a bearer exists —
                        // without this, a fresh install never registers for push.
                        PushRegistrar.registerIfLoggedIn(force = true)
                        _state.update { it.copy(loading = false, loggedIn = true) }
                    }
                }
                .onFailure { e ->
                    val raw = e.message.orEmpty()
                    // A rate-limit lockout is a dead end without a way out — give the
                    // user the alternative (wait + reset) instead of just "too many
                    // requests". The backend sends "Too many requests…"/RATE_LIMIT_EXCEEDED.
                    val rateLimited = raw.contains("too many", ignoreCase = true) ||
                        raw.contains("rate limit", ignoreCase = true) ||
                        raw.contains("rate_limit", ignoreCase = true)
                    val friendly = when {
                        rateLimited ->
                            "Too many attempts. Please wait a minute and try again — " +
                                "or tap “Forgot Password?” below to reset it."
                        raw.isNotBlank() -> raw
                        else -> "Unable to log in. Please try again."
                    }
                    _state.update { it.copy(loading = false, error = friendly) }
                }
        }
    }
}
