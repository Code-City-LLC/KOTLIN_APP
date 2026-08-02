package com.ga.airdrop.core.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized USD→JMD rate — Kotlin port of Swift AirdropExchangeRateStore:
 * every screen seeds its JMD totals from the shared last-known rate so
 * cold-launch paints instantly with a real number, and each screen's live
 * /exchange-rates round-trip publishes back here for the next one. The
 * last-known value is persisted so it also survives app restarts.
 */
object ExchangeRateStore {
    /** Server-seeded default (Swift DesignTokens 160.625). */
    const val DEFAULT_USD_TO_JMD = 160.625

    private const val PREFS = "exchange_rate"
    private const val KEY = "usdToJmd"
    private const val KEY_VERIFIED = "usdToJmdVerified"

    private var prefs: SharedPreferences? = null

    @Volatile
    var current: Double = DEFAULT_USD_TO_JMD
        private set

    /**
     * True only once the SERVER has told us a rate.
     *
     * ⚠️ [DEFAULT_USD_TO_JMD] is a seed so a cold launch paints instantly — it
     * is NOT a fact. It has already been wrong in production: the app quoted
     * 160.625 for months while the server said 162.00, understating every JMD
     * figure, because the envelope was never unwrapped and every caller fell
     * back to this constant with nothing recording that it had.
     *
     * A caller about to show money must be able to tell "the real rate" from
     * "the number we start with". Without this flag those are the same Double
     * and the difference is unrecoverable.
     */
    @Volatile
    var verified: Boolean = false
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs?.getString(KEY, null)?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
            current = it
            // A persisted rate only ever gets written by update(), i.e. by a
            // server response — so restoring one restores its verified status
            // too. Otherwise every cold launch would demote a known-good rate
            // back to "unverified" until the network answered.
            verified = prefs?.getBoolean(KEY_VERIFIED, false) ?: false
        }
    }

    /** Publish a live rate for every later screen (and the next cold launch). */
    fun update(rate: Double) {
        if (rate <= 0) return
        current = rate
        verified = true
        prefs?.edit()
            ?.putString(KEY, rate.toString())
            ?.putBoolean(KEY_VERIFIED, true)
            ?.apply()
    }

    /** Drop the user-session value and restore the server-seeded default. */
    fun clear() {
        current = DEFAULT_USD_TO_JMD
        verified = false
        prefs?.edit()?.remove(KEY)?.remove(KEY_VERIFIED)?.apply()
    }
}
