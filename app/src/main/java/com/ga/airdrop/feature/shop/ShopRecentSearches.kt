package com.ga.airdrop.feature.shop

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shop recent-searches ring — Swift FigmaShopViewController §C.7
 * ("Airdrop.shop.recentSearches.v1"): the last [MAX] queries the user
 * explicitly searched for, newest-first, deduped case-insensitively. Surfaced
 * as the "Recent:" chip strip while the Shop search field is focused; tapping a
 * chip re-runs that search.
 */
object ShopRecentSearches {
    private const val PREFS = "shop_recent_searches"
    private const val KEY = "entries"
    const val MAX = 5

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Saved queries, newest first; empty before [init] or when unset, and the
     *  key is cleared if the stored blob fails to decode. */
    fun read(): List<String> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrElse {
                store.edit().remove(KEY).apply()
                emptyList()
            }
    }

    /** Logout hygiene — recent queries are the prior user's data. */
    fun clear() {
        prefs?.edit()?.remove(KEY)?.apply()
    }

    /** Push [query] to the front of the ring. No-op before [init]. */
    fun save(query: String) {
        val store = prefs ?: return
        store.edit().putString(KEY, json.encodeToString(pushRecent(read(), query))).apply()
    }

    /**
     * Pure ring update — Swift saveRecentSearch: drop any case-insensitive
     * duplicate of [query], insert it at the front, cap at [MAX]. A blank
     * query leaves the list unchanged (still capped).
     */
    internal fun pushRecent(list: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return list.take(MAX)
        val deduped = list.filterNot { it.equals(q, ignoreCase = true) }
        return (listOf(q) + deduped).take(MAX)
    }
}
