package com.ga.airdrop.core.prefs

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.DeliverySettingsPayload

/**
 * Swift AirdropAPICache "instant warehouse paint" (2e3f879): a stale-while-
 * revalidate snapshot of GET /delivery/settings so the Delivery Method screen
 * paints warehouses synchronously before the network answers.
 *
 * Guards mirrored from Swift:
 *  - 24h TTL (stale reads return null).
 *  - Never overwrite a good cache with an empty-warehouse response.
 *  - Session-bound: the snapshot is stamped with the auth sessionId and only
 *    served back to the same session, so a logout/login can't bleed one
 *    account's snapshot into another (Swift's session-generation guard +
 *    logout cache wipe in one rule).
 */
object DeliverySettingsCache {

    private const val PREFS = "delivery_settings_cache"
    private const val KEY_JSON = "payload.v1"
    private const val KEY_AT = "storedAt.v1"
    private const val KEY_SESSION = "sessionId.v1"
    internal const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Synchronous cached payload for the CURRENT session, or null when cold/stale. */
    fun read(now: Long = System.currentTimeMillis()): DeliverySettingsPayload? {
        val p = prefs ?: return null
        val sessionId = AuthTokenStore.snapshot().sessionId ?: return null
        if (p.getString(KEY_SESSION, null) != sessionId) return null
        val storedAt = p.getLong(KEY_AT, 0L)
        if (storedAt <= 0L || now - storedAt > MAX_AGE_MS) return null
        val json = p.getString(KEY_JSON, null) ?: return null
        return runCatching {
            AirdropJson.decodeFromString(DeliverySettingsPayload.serializer(), json)
        }.getOrNull()
    }

    /** Write-through after a live fetch; skips empty payloads over a good cache. */
    fun write(payload: DeliverySettingsPayload, now: Long = System.currentTimeMillis()) {
        val p = prefs ?: return
        val sessionId = AuthTokenStore.snapshot().sessionId ?: return
        val incomingEmpty = payload.settings?.warehouses.orEmpty().isEmpty()
        if (incomingEmpty && read(now) != null) return
        val json = runCatching {
            AirdropJson.encodeToString(DeliverySettingsPayload.serializer(), payload)
        }.getOrNull() ?: return
        p.edit()
            .putString(KEY_JSON, json)
            .putLong(KEY_AT, now)
            .putString(KEY_SESSION, sessionId)
            .apply()
    }
}
