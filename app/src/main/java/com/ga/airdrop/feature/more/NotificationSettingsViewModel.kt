package com.ga.airdrop.feature.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.prefs.NotificationAccountPreferences
import com.ga.airdrop.core.prefs.NotificationPreferenceMatrix
import com.ga.airdrop.core.push.PushRegistrar
import com.ga.airdrop.core.push.notificationAuthorizationAllowed
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedRequest
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NotificationDeviceStatus {
    data object Applying : NotificationDeviceStatus
    data object On : NotificationDeviceStatus
    data object Off : NotificationDeviceStatus
    data object MissingAccount : NotificationDeviceStatus
    data object PermissionDenied : NotificationDeviceStatus
    data object PreferenceSaveFailed : NotificationDeviceStatus
    data class Failed(val detail: String) : NotificationDeviceStatus
}

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
    val deviceStatus: NotificationDeviceStatus = NotificationDeviceStatus.MissingAccount,
    val permissionDialogVisible: Boolean = false,
)

/**
 * Notification matrix parity with current Swift. The full matrix is local and
 * account-scoped because Laravel has no supported category preference API.
 * Only a master transition invokes the single PushRegistrar device-token owner.
 */
class NotificationSettingsViewModel(
    private val setDevicePush: (
        AuthTokenStore.RequestProvenance,
        Boolean,
        (Result<PushRegistrar.DevicePushOutcome>) -> Unit,
    ) -> Unit = { expected, enabled, onComplete ->
        PushRegistrar.setDevicePushEnabled(expected, enabled, onComplete)
    },
    private val hasNotificationPermission: (Context) -> Boolean =
        ::notificationAuthorizationAllowed,
    private val commitPreferences: (Int, NotificationPreferenceMatrix) -> Boolean =
        NotificationAccountPreferences::commit,
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state
    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var appContext: Context? = null

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        if (changed?.accountId != null) appContext?.let { load(it, changed) }
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionJobs.replaceSession()
                sessionOwner = changed
                _state.value = NotificationSettingsUiState()
                if (changed != null) appContext?.let { load(it, changed) }
            }
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        NotificationAccountPreferences.init(context)
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        load(context, owner)
    }

    fun refreshPermissionStatus(context: Context) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner)
        val status = when {
            owner?.accountId == null -> NotificationDeviceStatus.MissingAccount
            !_state.value.master -> NotificationDeviceStatus.Off
            !hasNotificationPermission(context) -> NotificationDeviceStatus.PermissionDenied
            else -> NotificationDeviceStatus.On
        }
        owner?.let { sessionBoundary.apply(it) { _state.update { state -> state.copy(deviceStatus = status) } } }
            ?: run { _state.update { it.copy(deviceStatus = status) } }
    }

    fun retry(context: Context) {
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner)
        if (requestOwner?.session?.accountId == null) {
            _state.update { it.copy(deviceStatus = NotificationDeviceStatus.MissingAccount) }
            return
        }
        applyDevicePush(context, requestOwner, _state.value.master)
    }

    fun onPermissionResult(context: Context, granted: Boolean) {
        if (granted) {
            retry(context)
        } else {
            _state.update {
                it.copy(
                    deviceStatus = NotificationDeviceStatus.PermissionDenied,
                    permissionDialogVisible = true,
                )
            }
        }
    }

    fun dismissPermissionDialog() = _state.update { it.copy(permissionDialogVisible = false) }

    fun setMaster(context: Context, on: Boolean) {
        mutate(context) {
            if (on) it.copy(master = true)
            else NotificationSettingsUiState(
                master = false,
                deviceStatus = it.deviceStatus,
                permissionDialogVisible = it.permissionDialogVisible,
            )
        }
    }

    fun setPackageMaster(context: Context, on: Boolean) {
        mutate(context) {
            if (on) it.copy(packageMaster = true)
            else it.copy(packageMaster = false, packageEmail = false, packageSms = false, packagePush = false)
        }
    }

    fun setPromosMaster(context: Context, on: Boolean) {
        mutate(context) {
            if (on) it.copy(promosMaster = true)
            else it.copy(promosMaster = false, promosEmail = false, promosSms = false, promosPush = false)
        }
    }

    fun setChannel(
        context: Context,
        transform: (NotificationSettingsUiState, Boolean) -> NotificationSettingsUiState,
        on: Boolean,
    ) {
        mutate(context) { transform(it, on) }
    }

    private fun load(context: Context, owner: AuthenticatedSessionOwner) {
        val accountId = owner.accountId
        if (accountId == null) {
            sessionBoundary.apply(owner) {
                _state.update { it.copy(deviceStatus = NotificationDeviceStatus.MissingAccount) }
            }
            return
        }
        var loaded: NotificationSettingsUiState? = null
        val read = sessionBoundary.runWhileCurrent(owner) {
            val matrix = NotificationAccountPreferences.load(accountId)
                ?: return@runWhileCurrent false
            loaded = matrix.toUiState(
                deviceStatus = when {
                    !matrix.master -> NotificationDeviceStatus.Off
                    !hasNotificationPermission(context) -> NotificationDeviceStatus.PermissionDenied
                    else -> NotificationDeviceStatus.On
                },
            )
            true
        }
        if (read) loaded?.let { state -> sessionBoundary.apply(owner) { _state.value = state } }
    }

    private fun mutate(
        context: Context,
        transform: (NotificationSettingsUiState) -> NotificationSettingsUiState,
    ) {
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner)
        if (requestOwner?.session?.accountId == null) {
            _state.update { it.copy(deviceStatus = NotificationDeviceStatus.MissingAccount) }
            return
        }
        val previous = _state.value
        val desired = transform(previous)
        val persisted = sessionBoundary.runWhileCurrent(requestOwner.session) {
            commitPreferences(
                requestOwner.session.accountId,
                desired.toPreferenceMatrix(),
            )
        }
        if (!persisted) {
            sessionBoundary.apply(requestOwner.session) {
                _state.update {
                    it.copy(
                        deviceStatus = NotificationDeviceStatus.PreferenceSaveFailed,
                    )
                }
            }
            return
        }
        if (!sessionBoundary.apply(requestOwner.session) { _state.value = desired }) return
        if (previous.master != desired.master) {
            applyDevicePush(context, requestOwner, desired.master)
        } else {
            sessionBoundary.apply(requestOwner.session) {
                _state.update {
                    it.copy(
                        deviceStatus = when {
                            !desired.master -> NotificationDeviceStatus.Off
                            !hasNotificationPermission(context) -> NotificationDeviceStatus.PermissionDenied
                            else -> NotificationDeviceStatus.On
                        },
                    )
                }
            }
        }
    }

    private fun applyDevicePush(
        context: Context,
        requestOwner: com.ga.airdrop.core.session.AuthenticatedRequestOwner,
        enabled: Boolean,
    ) {
        if (enabled && !hasNotificationPermission(context)) {
            sessionBoundary.apply(requestOwner.session) {
                _state.update {
                    it.copy(
                        deviceStatus = NotificationDeviceStatus.PermissionDenied,
                        permissionDialogVisible = true,
                    )
                }
            }
            return
        }
        if (!sessionBoundary.apply(requestOwner.session) {
                _state.update { it.copy(deviceStatus = NotificationDeviceStatus.Applying) }
            }
        ) return
        setDevicePush(requestOwner.provenance, enabled) { result ->
            val stillOwned = sessionBoundary.requestOwner(requestOwner.session)?.provenance == requestOwner.provenance
            if (!stillOwned) return@setDevicePush
            sessionBoundary.apply(requestOwner.session) {
                _state.update { current ->
                    result.fold(
                        onSuccess = { outcome ->
                            current.copy(
                                deviceStatus = when (outcome) {
                                    PushRegistrar.DevicePushOutcome.RegistrationRequested -> NotificationDeviceStatus.On
                                    PushRegistrar.DevicePushOutcome.Disabled -> NotificationDeviceStatus.Off
                                },
                                permissionDialogVisible = false,
                            )
                        },
                        onFailure = { error ->
                            current.copy(
                                deviceStatus = NotificationDeviceStatus.Failed(
                                    error.message?.takeIf { it.isNotBlank() }
                                        ?: "Tap Retry to try again.",
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

}

internal fun NotificationSettingsUiState.pushWanted(): Boolean = master

private fun NotificationPreferenceMatrix.toUiState(
    deviceStatus: NotificationDeviceStatus,
): NotificationSettingsUiState = NotificationSettingsUiState(
    master = master,
    packageMaster = packageMaster,
    packageEmail = packageEmail,
    packageSms = packageSms,
    packagePush = packagePush,
    promosMaster = promosMaster,
    promosEmail = promosEmail,
    promosSms = promosSms,
    promosPush = promosPush,
    deviceStatus = deviceStatus,
)

private fun NotificationSettingsUiState.toPreferenceMatrix(): NotificationPreferenceMatrix =
    NotificationPreferenceMatrix(
        master = master,
        packageMaster = packageMaster,
        packageEmail = packageEmail,
        packageSms = packageSms,
        packagePush = packagePush,
        promosMaster = promosMaster,
        promosEmail = promosEmail,
        promosSms = promosSms,
        promosPush = promosPush,
    )
