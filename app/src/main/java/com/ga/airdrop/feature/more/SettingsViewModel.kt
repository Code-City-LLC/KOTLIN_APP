package com.ga.airdrop.feature.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loggingOut: Boolean = false,
    val logoutError: String? = null,
    val loggedOut: Boolean = false,
    val cacheCleared: Boolean = false,
    /** Download My Data (Swift §D.2): request in flight / result note. */
    val exportingData: Boolean = false,
    val exportResult: String? = null,
)

/**
 * FigmaSettingsViewController behavior: clear-cache sweep of the cached
 * package/cart blobs + full logout hygiene (POST /auth/logout, then clear
 * token, session header and local cart caches even when the server call
 * comes back unauthenticated).
 */
class SettingsViewModel(
    private val repository: MoreSettingsRepository = MoreRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    /** RN storage.cleanAll parity — clears the persisted cart (the real
     *  client-side cache) plus the legacy cache prefs sweep. */
    fun clearCache(context: Context) {
        com.ga.airdrop.feature.cart.CartStore.init(context)
        com.ga.airdrop.feature.cart.CartStore.clear()
        com.ga.airdrop.feature.cart.SavedForLaterStore.init(context)
        com.ga.airdrop.feature.cart.SavedForLaterStore.clearAll()
        // Swift FigmaCalculatorHistory.removeAll() is wired to Settings → Clear Cache.
        com.ga.airdrop.feature.calculator.CalculatorHistory.clear()
        com.ga.airdrop.feature.shipments.clearShipmentsSessionCaches()
        com.ga.airdrop.feature.shop.clearShopSessionCaches()
        com.ga.airdrop.core.session.clearLegacySessionCachePrefs(context)
        _state.update { it.copy(cacheCleared = true) }
    }

    fun dismissCacheCleared() = _state.update { it.copy(cacheCleared = false) }

    /** POST /user/export-data — link now, or an email-when-ready note. */
    fun requestDataExport() {
        if (_state.value.exportingData) return
        _state.update { it.copy(exportingData = true) }
        viewModelScope.launch {
            com.ga.airdrop.data.repo.UserRepository(com.ga.airdrop.core.network.ApiClient.service)
                .requestPersonalDataExport()
                .onSuccess { response ->
                    val note = response.downloadUrl?.takeIf(String::isNotBlank)
                        ?.let { "Your data export is ready: $it" }
                        ?: response.message?.takeIf(String::isNotBlank)
                        ?: "Your data export was requested. We'll email you when it's ready."
                    _state.update { it.copy(exportingData = false, exportResult = note) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            exportingData = false,
                            exportResult = e.message
                                ?: "The export request couldn't be sent. Try again later.",
                        )
                    }
                }
        }
    }

    fun dismissExportResult() = _state.update { it.copy(exportResult = null) }

    fun logout(context: Context) {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true, logoutError = null) }
        viewModelScope.launch {
            val result = repository.logout()
            val unauthenticated = AuthTokenStore.token == null // 401 already swept the token
            if (result.isSuccess || unauthenticated) {
                finishLocalLogout(context)
            } else {
                _state.update {
                    it.copy(
                        loggingOut = false,
                        logoutError = result.exceptionOrNull()?.message
                            ?: "Logout failed, please try again.",
                    )
                }
            }
        }
    }

    fun dismissLogoutError() = _state.update { it.copy(logoutError = null) }

    private fun finishLocalLogout(context: Context) {
        // Full hygiene: bearer token, shared header cache, cart store, cached
        // cart/package blobs. FCM token deregistration joins once push lands.
        com.ga.airdrop.core.session.clearLocalUserSessionAfterCustomerLogout(context)
        _state.update { it.copy(loggingOut = false, loggedOut = true) }
    }

    companion object {
        const val CACHE_PREFS = com.ga.airdrop.core.session.SESSION_CACHE_PREFS
        val CACHE_KEYS = com.ga.airdrop.core.session.SESSION_CACHE_KEYS
    }
}
