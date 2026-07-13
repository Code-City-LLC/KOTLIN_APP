package com.ga.airdrop.core.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Durable identity of the authenticated account (issue #90).
 *
 * `AuthTokenStore.Snapshot.revision` is process-local and bumps on every
 * token rotation, so it cannot prove durable account ownership for persisted
 * state, and a same-account token refresh must never read as an account
 * switch. This store keeps the backend user id — a stable, non-secret
 * identifier (never the token or any token fingerprint) — persisted in plain
 * prefs so pending push routes can be owner-checked across process death.
 *
 * Bound at login (and re-affirmed by profile loads), cleared by the canonical
 * [clearLocalUserSession] sweep at every auth boundary.
 */
object SessionIdentity {

    private const val PREFS = "airdrop_session_identity"
    private const val KEY_ACCOUNT = "account_id"

    private var prefs: SharedPreferences? = null

    private val _accountId = MutableStateFlow<String?>(null)
    val accountId: StateFlow<String?> = _accountId

    val current: String? get() = _accountId.value

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _accountId.value = prefs?.getString(KEY_ACCOUNT, null)
    }

    fun bind(accountId: String?) {
        val id = accountId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        _accountId.value = id
        prefs?.edit()?.putString(KEY_ACCOUNT, id)?.apply()
    }

    fun clear() {
        _accountId.value = null
        prefs?.edit()?.remove(KEY_ACCOUNT)?.apply()
    }
}
