package com.ga.airdrop.core.prefs

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.core.auth.AuthTokenStore

data class NotificationPreferenceMatrix(
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

/** Sole owner of the stable account-scoped notification matrix and master intent. */
object NotificationAccountPreferences {
    const val PREFS = "airdrop_notification_settings"
    private const val KEY_SEEDED = "seeded"
    private const val KEY_MASTER = "isNotifications"
    private const val KEY_PACKAGE_MASTER = "packageMaster"
    private const val KEY_PACKAGE_EMAIL = "packageEmail"
    private const val KEY_PACKAGE_SMS = "packageSMS"
    private const val KEY_PACKAGE_PUSH = "packagePush"
    private const val KEY_PROMOS_MASTER = "promosMaster"
    private const val KEY_PROMOS_EMAIL = "promosEmail"
    private const val KEY_PROMOS_SMS = "promosSMS"
    private const val KEY_PROMOS_PUSH = "promosPush"
    private const val LEGACY_CLAIMED_BY = "__legacy_claimed_by_account"

    private val matrixKeys = listOf(
        KEY_MASTER,
        KEY_PACKAGE_MASTER,
        KEY_PACKAGE_EMAIL,
        KEY_PACKAGE_SMS,
        KEY_PACKAGE_PUSH,
        KEY_PROMOS_MASTER,
        KEY_PROMOS_EMAIL,
        KEY_PROMOS_SMS,
        KEY_PROMOS_PUSH,
    )
    private var preferences: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun load(accountId: Int): NotificationPreferenceMatrix? {
        val stored = preferences ?: return null
        if (accountId <= 0) return null
        if (!hasScopedState(stored, accountId)) {
            val seed = legacySeed(stored, accountId)
            if (!commitLocked(stored, accountId, seed)) return null
        }
        return readLocked(stored, accountId)
    }

    @Synchronized
    fun commit(accountId: Int, matrix: NotificationPreferenceMatrix): Boolean {
        val stored = preferences ?: return false
        if (accountId <= 0) return false
        return commitLocked(stored, accountId, matrix)
    }

    fun currentMasterEnabled(): Boolean = currentMatrix()?.master == true

    fun masterEnabledFor(expected: AuthTokenStore.RequestProvenance): Boolean {
        val accountId = expected.accountId ?: return false
        var enabled = false
        val read = AuthTokenStore.runWhileCurrentSession(
            expectedSessionId = expected.sessionId,
            expectedAccountId = accountId,
        ) {
            val matrix = load(accountId) ?: return@runWhileCurrentSession false
            enabled = matrix.master
            true
        }
        return read && enabled
    }

    fun accountKey(accountId: Int, key: String): String = "account.$accountId.$key"

    private fun currentMatrix(): NotificationPreferenceMatrix? {
        val provenance = AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()) ?: return null
        val accountId = provenance.accountId ?: return null
        var matrix: NotificationPreferenceMatrix? = null
        val read = AuthTokenStore.runWhileCurrentSession(
            expectedSessionId = provenance.sessionId,
            expectedAccountId = accountId,
        ) {
            matrix = load(accountId)
            matrix != null
        }
        return matrix.takeIf { read }
    }

    private fun hasScopedState(stored: SharedPreferences, accountId: Int): Boolean =
        stored.contains(accountKey(accountId, KEY_SEEDED)) ||
            matrixKeys.any { key -> stored.contains(accountKey(accountId, key)) }

    private fun legacySeed(stored: SharedPreferences, accountId: Int): NotificationPreferenceMatrix {
        val claimant = stored.getInt(LEGACY_CLAIMED_BY, 0).takeIf { it > 0 }
        val hasLegacy = matrixKeys.any(stored::contains)
        if (!hasLegacy || (claimant != null && claimant != accountId)) {
            return NotificationPreferenceMatrix()
        }
        return NotificationPreferenceMatrix(
            master = stored.getBoolean(KEY_MASTER, true),
            packageMaster = stored.getBoolean(KEY_PACKAGE_MASTER, false),
            packageEmail = stored.getBoolean(KEY_PACKAGE_EMAIL, false),
            packageSms = stored.getBoolean(KEY_PACKAGE_SMS, false),
            packagePush = stored.getBoolean(KEY_PACKAGE_PUSH, false),
            promosMaster = stored.getBoolean(KEY_PROMOS_MASTER, false),
            promosEmail = stored.getBoolean(KEY_PROMOS_EMAIL, false),
            promosSms = stored.getBoolean(KEY_PROMOS_SMS, false),
            promosPush = stored.getBoolean(KEY_PROMOS_PUSH, false),
        )
    }

    private fun readLocked(stored: SharedPreferences, accountId: Int): NotificationPreferenceMatrix =
        NotificationPreferenceMatrix(
            master = stored.getBoolean(accountKey(accountId, KEY_MASTER), true),
            packageMaster = stored.getBoolean(accountKey(accountId, KEY_PACKAGE_MASTER), false),
            packageEmail = stored.getBoolean(accountKey(accountId, KEY_PACKAGE_EMAIL), false),
            packageSms = stored.getBoolean(accountKey(accountId, KEY_PACKAGE_SMS), false),
            packagePush = stored.getBoolean(accountKey(accountId, KEY_PACKAGE_PUSH), false),
            promosMaster = stored.getBoolean(accountKey(accountId, KEY_PROMOS_MASTER), false),
            promosEmail = stored.getBoolean(accountKey(accountId, KEY_PROMOS_EMAIL), false),
            promosSms = stored.getBoolean(accountKey(accountId, KEY_PROMOS_SMS), false),
            promosPush = stored.getBoolean(accountKey(accountId, KEY_PROMOS_PUSH), false),
        )

    private fun commitLocked(
        stored: SharedPreferences,
        accountId: Int,
        matrix: NotificationPreferenceMatrix,
    ): Boolean {
        val editor = stored.edit()
            .putBoolean(accountKey(accountId, KEY_SEEDED), true)
            .putBoolean(accountKey(accountId, KEY_MASTER), matrix.master)
            .putBoolean(accountKey(accountId, KEY_PACKAGE_MASTER), matrix.packageMaster)
            .putBoolean(accountKey(accountId, KEY_PACKAGE_EMAIL), matrix.packageEmail)
            .putBoolean(accountKey(accountId, KEY_PACKAGE_SMS), matrix.packageSms)
            .putBoolean(accountKey(accountId, KEY_PACKAGE_PUSH), matrix.packagePush)
            .putBoolean(accountKey(accountId, KEY_PROMOS_MASTER), matrix.promosMaster)
            .putBoolean(accountKey(accountId, KEY_PROMOS_EMAIL), matrix.promosEmail)
            .putBoolean(accountKey(accountId, KEY_PROMOS_SMS), matrix.promosSms)
            .putBoolean(accountKey(accountId, KEY_PROMOS_PUSH), matrix.promosPush)
        val claimant = stored.getInt(LEGACY_CLAIMED_BY, 0).takeIf { it > 0 }
        if (claimant == null && matrixKeys.any(stored::contains)) {
            editor.putInt(LEGACY_CLAIMED_BY, accountId)
            matrixKeys.forEach(editor::remove)
        }
        return editor.commit()
    }
}

/**
 * Owns the general pickup/currency cache, which is intentionally scoped to one
 * auth session rather than one account. Swift clears these values on every
 * session replacement and repopulates them from the authoritative user response.
 */
object SessionPreferences {
    const val PREFS = "airdrop_preferences"
    const val KEY_PICKUP = "airdrop.preferences.pickup_location"
    const val KEY_CURRENCY = "airdrop.preferences.payment_currency"
    private const val KEY_SESSION_ID = "__authenticated_session_id"

    private val lock = Any()
    private var preferences: SharedPreferences? = null

    fun init(context: Context, sessionId: String?) {
        synchronized(lock) {
            preferences = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
        replaceSession(sessionId)
    }

    fun replaceSession(sessionId: String?) {
        synchronized(lock) {
            val stored = preferences ?: return@synchronized
            if (stored.getString(KEY_SESSION_ID, null) == sessionId) return@synchronized
            stored.edit()
                .remove(KEY_PICKUP)
                .remove(KEY_CURRENCY)
                .apply {
                    if (sessionId == null) remove(KEY_SESSION_ID) else putString(KEY_SESSION_ID, sessionId)
                }
                .commit()
        }
    }

    fun clearValues(preferences: SharedPreferences) {
        preferences.edit()
            .remove(KEY_PICKUP)
            .remove(KEY_CURRENCY)
            .commit()
    }
}
