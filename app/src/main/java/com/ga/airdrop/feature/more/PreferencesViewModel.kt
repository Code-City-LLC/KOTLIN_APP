package com.ga.airdrop.feature.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreferencesUiState(
    val email: String = "",
    val pickupLocation: String = "",
    val paymentCurrency: String = "JMD", // RN default fallback
    val saving: Boolean = false,
    val alert: Pair<String, String>? = null,
)

/**
 * FigmaPreferencesViewController behavior: read-only email + pickup
 * location + payment currency pickers, Save → sparse PUT /user/profile.
 * Choices mirror into SharedPreferences under `airdrop.preferences.*` so
 * Home/calculator can read them synchronously (RECONCILE: consumers land
 * with those feature groups).
 */
class PreferencesViewModel(
    private val repository: MoreProfileRepository = MoreRepository(),
) : ViewModel() {

    // Verbatim RN option lists — order matters (JMD before USD).
    val pickupLocations = listOf("Montego Bay", "Kingston", "Savanna-La-Mar")
    val paymentCurrencies = listOf("JMD", "USD")

    private val _state = MutableStateFlow(PreferencesUiState())
    val state: StateFlow<PreferencesUiState> = _state

    private var userId: Int? = null

    fun start(context: Context) {
        seedFromPrefs(context)
        viewModelScope.launch {
            repository.currentUser().onSuccess { user ->
                userId = user.id
                _state.update { it.copy(email = user.email.orEmpty()) }
                user.pickupLocation?.takeIf { it.isNotEmpty() && it in pickupLocations }
                    ?.let { applyPickup(context, it) }
                user.paymentCurrency?.takeIf { it.isNotEmpty() && it in paymentCurrencies }
                    ?.let { applyCurrency(context, it) }
            }
            // Silent on failure — the prefs seed keeps the UI usable offline.
        }
    }

    fun applyPickup(context: Context, value: String) {
        _state.update { it.copy(pickupLocation = value) }
        prefs(context).edit().putString(KEY_PICKUP, value).apply()
    }

    fun applyCurrency(context: Context, value: String) {
        _state.update { it.copy(paymentCurrency = value) }
        prefs(context).edit().putString(KEY_CURRENCY, value).apply()
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val fields = mapOf(
                "user_id" to userId?.toString(),
                "email" to s.email.trim().takeIf { it.isNotEmpty() },
                "pickup_location" to s.pickupLocation.trim().takeIf { it.isNotEmpty() },
                "payment_currency" to s.paymentCurrency.trim().takeIf { it.isNotEmpty() },
            )
            repository.updateProfile(fields)
                .onSuccess {
                    _state.update {
                        it.copy(saving = false, alert = "Success" to "Preferences updated successfully")
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(saving = false, alert = "Error" to (e.message ?: "Please try again."))
                    }
                }
        }
    }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    private fun seedFromPrefs(context: Context) {
        val stored = prefs(context)
        stored.getString(KEY_PICKUP, null)?.takeIf { it in pickupLocations }?.let { value ->
            _state.update { it.copy(pickupLocation = value) }
        }
        stored.getString(KEY_CURRENCY, null)?.takeIf { it in paymentCurrencies }?.let { value ->
            _state.update { it.copy(paymentCurrency = value) }
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PREFS = "airdrop_preferences"
        const val KEY_PICKUP = "airdrop.preferences.pickup_location"
        const val KEY_CURRENCY = "airdrop.preferences.payment_currency"
    }
}
