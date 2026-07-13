package com.ga.airdrop.feature.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.prefs.SessionPreferences
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.captureOwnedRequest
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    // Verbatim RN option lists — order matters (JMD before USD).
    val pickupLocations = listOf("Montego Bay", "Kingston", "Savanna-La-Mar")
    val paymentCurrencies = listOf("JMD", "USD")

    private val _state = MutableStateFlow(PreferencesUiState())
    val state: StateFlow<PreferencesUiState> = _state

    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var appContext: Context? = null
    private var userId: Int? = null

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                appContext?.let { context -> SessionPreferences.clearValues(prefs(context)) }
                sessionJobs.replaceSession()
                loadJob = null
                saveJob = null
                sessionOwner = changed
                userId = null
                _state.value = PreferencesUiState()
                appContext?.let { context ->
                    changed?.let { owner -> seedFromPrefs(context, owner) }
                    if (changed != null) load(context)
                }
            }
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        seedFromPrefs(context, owner)
        load(context)
    }

    private fun load(context: Context) {
        if (loadJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        loadJob = sessionJobs.launch {
            repository.currentUser().onSuccess { user ->
                val loadedUserId = user.id
                if (loadedUserId != null && !sessionBoundary.bindAccountId(owner, loadedUserId)) return@onSuccess
                val pickup = user.pickupLocation?.takeIf { it.isNotEmpty() && it in pickupLocations }
                val currency = user.paymentCurrency?.takeIf { it.isNotEmpty() && it in paymentCurrencies }
                val applied = sessionBoundary.apply(owner) {
                    userId = user.id
                    _state.update {
                        it.copy(
                            email = user.email.orEmpty(),
                            pickupLocation = pickup ?: it.pickupLocation,
                            paymentCurrency = currency ?: it.paymentCurrency,
                        )
                    }
                }
                if (applied) persist(context, owner, pickup, currency)
            }
            // Silent on failure — the prefs seed keeps the UI usable offline.
        }
    }

    fun applyPickup(context: Context, value: String) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        val applied = sessionBoundary.apply(owner) {
            _state.update { it.copy(pickupLocation = value) }
        }
        if (applied) persist(context, owner, pickup = value, currency = null)
    }

    fun applyCurrency(context: Context, value: String) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        val applied = sessionBoundary.apply(owner) {
            _state.update { it.copy(paymentCurrency = value) }
        }
        if (applied) persist(context, owner, pickup = null, currency = value)
    }

    fun save() {
        if (saveJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        val s = _state.value
        if (s.saving) return
        if (!sessionBoundary.apply(owner) { _state.update { it.copy(saving = true) } }) return
        val requestUserId = userId
        saveJob = sessionJobs.launch {
            val fields = mapOf(
                "user_id" to requestUserId?.toString(),
                "email" to s.email.trim().takeIf { it.isNotEmpty() },
                "pickup_location" to s.pickupLocation.trim().takeIf { it.isNotEmpty() },
                "payment_currency" to s.paymentCurrency.trim().takeIf { it.isNotEmpty() },
            )
            repository.updateProfile(fields, requestOwner.provenance)
                .onSuccess {
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(saving = false, alert = "Success" to "Preferences updated successfully")
                        }
                    }
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(saving = false, alert = "Error" to (e.message ?: "Please try again."))
                        }
                    }
                }
        }
    }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    private fun seedFromPrefs(context: Context, owner: AuthenticatedSessionOwner) {
        var pickup: String? = null
        var currency: String? = null
        val read = sessionBoundary.runWhileCurrent(owner) {
            val stored = prefs(context)
            pickup = stored.getString(KEY_PICKUP, null)?.takeIf { it in pickupLocations }
            currency = stored.getString(KEY_CURRENCY, null)?.takeIf { it in paymentCurrencies }
            true
        }
        if (read) sessionBoundary.apply(owner) {
            _state.update {
                it.copy(
                    pickupLocation = pickup ?: it.pickupLocation,
                    paymentCurrency = currency ?: it.paymentCurrency,
                )
            }
        }
    }

    private fun persist(
        context: Context,
        owner: AuthenticatedSessionOwner,
        pickup: String?,
        currency: String?,
    ): Boolean = sessionBoundary.runWhileCurrent(owner) {
        prefs(context).edit()
            .apply {
                if (pickup != null) putString(KEY_PICKUP, pickup)
                if (currency != null) putString(KEY_CURRENCY, currency)
            }
            .commit()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PREFS = SessionPreferences.PREFS
        const val KEY_PICKUP = SessionPreferences.KEY_PICKUP
        const val KEY_CURRENCY = SessionPreferences.KEY_CURRENCY
    }
}
