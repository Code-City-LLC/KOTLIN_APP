package com.ga.airdrop.feature.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val master: Boolean = true,
    val packageMaster: Boolean = false,
    val packageEmail: Boolean = false,
    val packageSms: Boolean = false,
    val packagePush: Boolean = false,
    val promosMaster: Boolean = false,
    val promosEmail: Boolean = false,
    val promosSms: Boolean = false,
    val promosPush: Boolean = false,
)

/**
 * FigmaNotificationSettingsViewController behavior: master toggle cascades
 * off both sections, section toggles reset their sub-rows, everything
 * persists locally and syncs email/sms/offers notification flags with a
 * sparse PUT /user/profile ("1"/"0" strings, best-effort).
 * RECONCILE: FCM device-token registration on push-enable once Firebase
 * messaging lands (no FCM stack in the Android app yet).
 */
class NotificationSettingsViewModel(
    private val repository: MoreRepository = MoreRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state

    fun start(context: Context) {
        val prefs = prefs(context)
        _state.value = NotificationSettingsUiState(
            master = if (prefs.contains(KEY_MASTER)) prefs.getBoolean(KEY_MASTER, true) else true,
            packageMaster = prefs.getBoolean(KEY_PACKAGE_MASTER, false),
            packageEmail = prefs.getBoolean(KEY_PACKAGE_EMAIL, false),
            packageSms = prefs.getBoolean(KEY_PACKAGE_SMS, false),
            packagePush = prefs.getBoolean(KEY_PACKAGE_PUSH, false),
            promosMaster = prefs.getBoolean(KEY_PROMOS_MASTER, false),
            promosEmail = prefs.getBoolean(KEY_PROMOS_EMAIL, false),
            promosSms = prefs.getBoolean(KEY_PROMOS_SMS, false),
            promosPush = prefs.getBoolean(KEY_PROMOS_PUSH, false),
        )
    }

    fun setMaster(context: Context, on: Boolean) {
        _state.update {
            if (on) it.copy(master = true)
            else NotificationSettingsUiState(master = false) // cascade everything off
        }
        persist(context)
    }

    fun setPackageMaster(context: Context, on: Boolean) {
        _state.update {
            if (on) it.copy(packageMaster = true)
            else it.copy(packageMaster = false, packageEmail = false, packageSms = false, packagePush = false)
        }
        persist(context)
    }

    fun setPromosMaster(context: Context, on: Boolean) {
        _state.update {
            if (on) it.copy(promosMaster = true)
            else it.copy(promosMaster = false, promosEmail = false, promosSms = false, promosPush = false)
        }
        persist(context)
    }

    fun setChannel(context: Context, transform: (NotificationSettingsUiState, Boolean) -> NotificationSettingsUiState, on: Boolean) {
        _state.update { transform(it, on) }
        persist(context)
    }

    private fun persist(context: Context) {
        val s = _state.value
        prefs(context).edit()
            .putBoolean(KEY_MASTER, s.master)
            .putBoolean(KEY_PACKAGE_MASTER, s.packageMaster)
            .putBoolean(KEY_PACKAGE_EMAIL, s.packageEmail)
            .putBoolean(KEY_PACKAGE_SMS, s.packageSms)
            .putBoolean(KEY_PACKAGE_PUSH, s.packagePush)
            .putBoolean(KEY_PROMOS_MASTER, s.promosMaster)
            .putBoolean(KEY_PROMOS_EMAIL, s.promosEmail)
            .putBoolean(KEY_PROMOS_SMS, s.promosSms)
            .putBoolean(KEY_PROMOS_PUSH, s.promosPush)
            .apply()
        syncToBackend()
    }

    /** Aggregate channel flags across sections — Swift syncToBackend parity. */
    private fun syncToBackend() {
        val s = _state.value
        val email = s.master && (s.packageEmail || s.promosEmail)
        val sms = s.master && (s.packageSms || s.promosSms)
        val offers = s.master && (s.promosEmail || s.promosSms || s.promosPush)
        viewModelScope.launch {
            repository.updateProfile(
                mapOf(
                    "email_notification" to if (email) "1" else "0",
                    "sms_notification" to if (sms) "1" else "0",
                    "offers_notification" to if (offers) "1" else "0",
                ),
            ) // Non-fatal on failure — local state is already updated.
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PREFS = "airdrop_notification_settings"
        private const val KEY_MASTER = "isNotifications"
        private const val KEY_PACKAGE_MASTER = "packageMaster"
        private const val KEY_PACKAGE_EMAIL = "packageEmail"
        private const val KEY_PACKAGE_SMS = "packageSMS"
        private const val KEY_PACKAGE_PUSH = "packagePush"
        private const val KEY_PROMOS_MASTER = "promosMaster"
        private const val KEY_PROMOS_EMAIL = "promosEmail"
        private const val KEY_PROMOS_SMS = "promosSMS"
        private const val KEY_PROMOS_PUSH = "promosPush"
    }
}
