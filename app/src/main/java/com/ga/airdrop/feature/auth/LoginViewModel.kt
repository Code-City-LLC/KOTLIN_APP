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
        doLogin(current.email, current.password)
    }

    /**
     * Biometric sign-in path: the Login screen unlocks the vault
     * (fingerprint/face) and replays the stored credentials through the
     * exact same request; nothing else differs from a typed login.
     */
    fun loginWithVaultCredentials(email: String, password: String) {
        _state.update { it.copy(email = email, password = password) }
        doLogin(email, password)
    }

    private fun doLogin(email: String, password: String) {
        if (_state.value.loading) return
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Enter your email and password.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.login(email.trim(), password)
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
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Unable to log in. Please try again.",
                        )
                    }
                }
        }
    }
}
