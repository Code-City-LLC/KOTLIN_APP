package com.ga.airdrop.feature.shop

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.BuildConfig

/**
 * "Notify me when in stock" — port of Swift NotifyWhenInStockStore
 * (deep-audit §C.7). A local, device-only watch-list of product IDs the
 * customer wants a heads-up on. The CTA appears on a product detail only
 * when `inventory == 0`; tapping subscribes, and a launch-time poll fires
 * a local notification (channel `airdrop_alerts`) the moment a watched
 * product is back in stock, then drops it from the list.
 *
 * Gated like report-damage — enabled in debug/staging, dark on Production
 * until the rollout is promoted (Swift ships it behind
 * AirdropFeatureFlags.notifyWhenInStock, off by default).
 */
object NotifyInStockStore {

    private const val PREFS_NAME = "airdrop.notify_in_stock"
    private const val KEY_IDS = "product_ids"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    internal fun restoreForTests(storage: SharedPreferences) {
        prefs = storage
    }

    /** Swift AirdropFeatureFlags.notifyWhenInStock — off on prod until promoted. */
    fun featureEnabled(): Boolean =
        BuildConfig.DEBUG || !BuildConfig.ENV_NAME.equals("Production", ignoreCase = true)

    fun subscribedIds(): Set<Int> =
        prefs?.getStringSet(KEY_IDS, emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()

    fun isSubscribed(productId: Int): Boolean = productId in subscribedIds()

    fun subscribe(productId: Int) {
        if (productId <= 0) return
        val next = subscribedIds() + productId
        persist(next)
    }

    fun unsubscribe(productId: Int) {
        persist(subscribedIds() - productId)
    }

    fun clearAll() {
        prefs?.edit()?.remove(KEY_IDS)?.apply()
    }

    private fun persist(ids: Set<Int>) {
        prefs?.edit()?.putStringSet(KEY_IDS, ids.map(Int::toString).toSet())?.apply()
    }

    /** Current stock for a watched product from the polled catalog. */
    data class StockSnapshot(val inventory: Int?, val title: String)

    /**
     * Poll each watched product against [snapshot] (id → current stock,
     * built once from the live product lists — Swift polls the same way);
     * for any now in stock, [notify] it and drop it from the list. Products
     * absent from the snapshot (unknown/unreachable) stay subscribed.
     */
    suspend fun poll(
        snapshot: suspend () -> Map<Int, StockSnapshot>,
        notify: (productId: Int, title: String) -> Unit,
    ) {
        val watched = subscribedIds()
        if (watched.isEmpty()) return
        val current = runCatching { snapshot() }.getOrElse { return }
        for (id in watched) {
            val row = current[id] ?: continue
            if (decideBackInStock(row.inventory)) {
                notify(id, row.title)
                unsubscribe(id)
            }
        }
    }
}

/** True only when inventory is known AND positive (null stays subscribed). */
internal fun decideBackInStock(inventory: Int?): Boolean = inventory != null && inventory > 0

/** Swift stock-gate: the CTA shows only when a product is out of stock. */
internal fun showNotifyInStockCta(inventory: Int?, featureEnabled: Boolean): Boolean =
    featureEnabled && (inventory ?: 0) <= 0
