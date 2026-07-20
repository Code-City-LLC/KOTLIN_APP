package com.ga.airdrop.feature.auth

import android.content.Context
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Swift AirdropIdentityReminder (Kemar 2026-07-19, 2a3cf04 + 44a9c5f):
 * after login, remind accounts with no ID/TRN on file to add them — customs
 * needs the ID and missing info can delay processing. Shown once per account
 * per device; silent on any failure (must never block or break login).
 *
 * Completeness: the server's identity_complete flag wins when present; on
 * older payloads fall back to requiring BOTH TRN and identity number
 * (44a9c5f semantics — prompt when either is missing).
 */
object IdentityReminder {

    const val TITLE = "Add your ID & TRN"
    const val MESSAGE =
        "Customs needs your ID information to process your packages. " +
            "Add your ID and TRN now to avoid delays in processing — " +
            "you can find them any time under More → Profile."
    const val CONFIRM = "Add Now"
    const val DISMISS = "Later"

    private const val PREFS_NAME = "identity_reminder"
    private const val MARKER_PREFIX = "Airdrop.identityReminder.shown."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _showPrompt = MutableStateFlow(false)
    val showPrompt: StateFlow<Boolean> = _showPrompt

    /**
     * Swift promptIfNeeded — fire-and-forget after an explicit login
     * (password or biometric). Every failure path is silent.
     */
    fun checkAfterLogin(
        context: Context,
        fetchUser: suspend () -> Result<AirdropUser> = {
            UserRepository(ApiClient.service).currentUser()
        },
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val user = fetchUser().getOrNull() ?: return@launch
            val accountKey = accountKey(user) ?: return@launch
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val marker = MARKER_PREFIX + accountKey
            if (prefs.getBoolean(marker, false)) return@launch
            if (isIdentityComplete(user)) {
                // Complete accounts are never re-checked on this device.
                prefs.edit().putBoolean(marker, true).apply()
                return@launch
            }
            // Marker written when we actually present, matching Swift.
            prefs.edit().putBoolean(marker, true).apply()
            _showPrompt.value = true
        }
    }

    fun dismiss() {
        _showPrompt.value = false
    }

    /** First non-blank of accountNumber → id → email (Swift order). */
    internal fun accountKey(user: AirdropUser): String? =
        listOf(user.accountNumber, user.id?.toString(), user.email)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()

    /** Server flag wins; fallback requires BOTH TRN and identity number. */
    internal fun isIdentityComplete(user: AirdropUser): Boolean {
        user.identityComplete?.let { return it }
        val trn = user.trnNumber?.trim().orEmpty()
        val idNumber = user.identityNumber?.trim().orEmpty()
        return trn.isNotEmpty() && idNumber.isNotEmpty()
    }
}
