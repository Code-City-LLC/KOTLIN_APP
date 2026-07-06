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
        sweepCachePrefs(context)
        _state.update { it.copy(cacheCleared = true) }
    }

    fun dismissCacheCleared() = _state.update { it.copy(cacheCleared = false) }

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
        AuthTokenStore.clear()
        SessionStore.clear()
        com.ga.airdrop.feature.cart.CartStore.clear()
        com.ga.airdrop.core.prefs.DeliveryDefaultsStore.clearAll()
        sweepCachePrefs(context)
        _state.update { it.copy(loggingOut = false, loggedOut = true) }
    }

    private fun sweepCachePrefs(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            CACHE_KEYS.forEach(::remove)
        }.apply()
    }

    companion object {
        const val CACHE_PREFS = "airdrop_cache"
        val CACHE_KEYS = listOf(
            "PACKAGE", "CART_PACKAGES", "PACKAGE_SHORTLIST",
            "figma.cart.items", "figma.packages.cache", "figma.packages.shortlist",
        )
    }
}
