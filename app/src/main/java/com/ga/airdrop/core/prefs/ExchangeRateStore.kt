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

    private var prefs: SharedPreferences? = null

    @Volatile
    var current: Double = DEFAULT_USD_TO_JMD
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs?.getString(KEY, null)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { current = it }
    }

    /** Publish a live rate for every later screen (and the next cold launch). */
    fun update(rate: Double) {
        if (rate <= 0) return
        current = rate
        prefs?.edit()?.putString(KEY, rate.toString())?.apply()
    }
}
