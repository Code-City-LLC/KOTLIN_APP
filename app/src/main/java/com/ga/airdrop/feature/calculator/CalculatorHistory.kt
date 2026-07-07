package com.ga.airdrop.feature.calculator

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Recent-calculations ring buffer — Swift FigmaCalculatorHistory
 * (DEEP_AUDIT_2026_05_22 §B.6): a computed quote is otherwise gone once
 * dismissed, so the last [MAX] calculations are kept newest-first in
 * SharedPreferences as a JSON blob. No PII — only method + weight + invoice +
 * result total. The user can wipe it from Settings → Clear Cache ([clear]).
 */
object CalculatorHistory {
    private const val PREFS = "calculator_history"
    private const val KEY = "entries"
    const val MAX = 5

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    @Serializable
    data class Entry(
        val method: String,          // ShippingMethod.name
        val weightLbs: Double,
        val invoiceUsd: Double,
        val totalUsd: Double? = null,
        val createdAt: Long = 0L,    // epoch millis
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** All saved entries, newest first; empty when unset/uninitialised, and the
     *  key is cleared if the stored blob fails to decode. */
    fun entries(): List<Entry> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Entry>>(raw) }
            .getOrElse {
                store.edit().remove(KEY).apply()
                emptyList()
            }
    }

    /** Push newest to the top, dropping the oldest past [MAX]. No-op before
     *  [init] (e.g. in unit tests that don't touch the store). */
    fun record(entry: Entry) {
        val store = prefs ?: return
        val next = capped(listOf(entry) + entries())
        store.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    /** Clear the entire history (Settings → Clear Cache). */
    fun clear() {
        prefs?.edit()?.remove(KEY)?.apply()
    }

    /** Pure ring-buffer cap — a newest-first list truncated to [MAX]. */
    internal fun capped(list: List<Entry>): List<Entry> = list.take(MAX)

    // ─── Last shipping method (Swift §D.4 "airdrop.calculator.lastMethod") ───

    private const val KEY_LAST_METHOD = "lastMethod"

    /** The method the user last calculated with; STANDARD until a first save. */
    fun lastMethod(): ShippingMethod =
        methodFor(prefs?.getString(KEY_LAST_METHOD, null))

    /** Persist the method of a successful calculation (Swift saveLastMethod). */
    fun saveLastMethod(method: ShippingMethod) {
        prefs?.edit()?.putString(KEY_LAST_METHOD, method.name)?.apply()
    }

    /** Pure name→method resolution with the Swift .standard fallback. */
    internal fun methodFor(name: String?): ShippingMethod =
        ShippingMethod.entries.firstOrNull { it.name == name } ?: ShippingMethod.STANDARD
}
