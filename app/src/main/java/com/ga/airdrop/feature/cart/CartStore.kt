package com.ga.airdrop.feature.cart

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Local cart, Android counterpart of `FigmaCartStore` (Swift) / RN
 * `cartModel`. One line per product/package id (idempotent add), sorted by
 * title case-insensitively like the Swift store. Unlike Swift's in-memory
 * singleton this one is persisted to SharedPreferences so the cart survives
 * process death (RN parity — its cart model is persisted).
 *
 * Every mutation feeds [SessionStore] `cartCount` so all tab headers show
 * the live badge.
 *
 * ORCHESTRATOR NOTES:
 *  - call [CartStore.init] from Application/MainActivity startup (screens
 *    also call it lazily, so this is belt-and-braces);
 *  - call [CartStore.clear] as part of logout hygiene.
 */
object CartStore {

    @Serializable
    enum class CartLineSource {
        Product,
        Package,
    }

    /** Mirror of Swift `FigmaCartLine`. [priceUsd] is the unit price. */
    @Serializable
    data class CartLine(
        val id: Int,
        val packageId: Int? = null,
        // Single source-of-truth for product-vs-package cart lines. Existing
        // persisted rows default to Product to preserve the old checkout
        // behavior and avoid syncing unknown legacy rows as shipment packages.
        val source: CartLineSource = CartLineSource.Product,
        val imageUrl: String? = null,
        val title: String = "",
        val qty: Int = 1,
        val priceUsd: Double = 0.0,
        /**
         * Swift FigmaCartLineKind — true for auction / e-commerce products
         * (owned server-side by the auction holding account), false for the
         * buyer's own shipment packages. Drives the checkout `is_auction`
         * flag so a mixed cart is declared honestly.
         */
        val isAuction: Boolean = false,
    )

    private const val PREFS = "airdrop_cart"
    private const val KEY_LINES = "cart_lines"

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<CartLine>>(emptyList())

    /** Sorted like Swift: title, case-insensitive. */
    val items: StateFlow<List<CartLine>> = _items

    val count: Int get() = _items.value.size

    /** Idempotent; safe to call from every shop/cart screen. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY_LINES, null)
        val restored = raw?.let {
            runCatching { json.decodeFromString(ListSerializer(CartLine.serializer()), it) }.getOrNull()
        }.orEmpty()
        _items.value = sorted(restored)
        publishCount()
    }

    fun contains(id: Int): Boolean = _items.value.any { it.id == id }

    /** Returns true when the line was added (false = already in cart). */
    fun add(line: CartLine): Boolean =
        // Membership check folded into the atomic transform so two concurrent
        // adds of the same id can't both slip past a separate contains() gate.
        mutate { list -> if (list.any { it.id == line.id }) list else list + line }

    /** Returns the resulting membership (true = now in cart), like Swift `toggle`. */
    fun toggle(line: CartLine): Boolean {
        return if (contains(line.id)) {
            remove(line.id)
            false
        } else {
            add(line)
            true
        }
    }

    fun remove(id: Int) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    /** Clear all lines — after verified payment, cache clear, or logout. */
    fun clear() {
        mutate { emptyList() }
    }

    /** Swift parity: `items.reduce(0) { $0 + qty * priceUSD }`. */
    fun totalUsd(): Double = _items.value.sumOf { it.qty * it.priceUsd }

    // Atomic read-modify-write. MutableStateFlow.update re-runs the transform
    // on a CAS miss, so concurrent add/remove/clear can't clobber each other's
    // changes the way `_items.value = f(_items.value)` did (BUG_AUDIT H30).
    // Returns whether the list actually changed so add() can report membership
    // and we skip a redundant persist/publish on no-op mutations.
    private fun mutate(transform: (List<CartLine>) -> List<CartLine>): Boolean {
        var changed = false
        _items.update { current ->
            val next = sorted(transform(current))
            changed = next != current
            next
        }
        if (changed) {
            persist()
            publishCount()
        }
        return changed
    }

    private fun sorted(lines: List<CartLine>): List<CartLine> =
        lines.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private fun persist() {
        prefs?.edit()
            ?.putString(KEY_LINES, json.encodeToString(ListSerializer(CartLine.serializer()), _items.value))
            ?.apply()
    }

    private fun publishCount() {
        SessionStore.update { it.copy(cartCount = _items.value.size) }
    }
}

/**
 * Separate "Saved for Later" cache, matching Swift `FigmaSavedForLaterStore`.
 * It deliberately does not update [SessionStore.cartCount]: saved items are
 * parked outside the active checkout cart until the user moves them back.
 */
object SavedForLaterStore {

    private const val PREFS = "airdrop_saved_for_later"
    private const val KEY_LINES = "saved_for_later_lines"

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<CartStore.CartLine>>(emptyList())

    /** Newest first, like Swift `list.insert(line, at: 0)`. */
    val items: StateFlow<List<CartStore.CartLine>> = _items

    val count: Int get() = _items.value.size

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY_LINES, null)
        val restored = raw?.let {
            runCatching { json.decodeFromString(ListSerializer(CartStore.CartLine.serializer()), it) }
                .getOrNull()
        }.orEmpty()
        _items.value = restored
    }

    fun contains(line: CartStore.CartLine): Boolean =
        _items.value.any { it.id == line.id }

    /** Idempotent save. Returns true only when a new saved row was inserted. */
    fun save(line: CartStore.CartLine): Boolean =
        mutate { list -> if (list.any { it.id == line.id }) list else listOf(line) + list }

    fun remove(id: Int) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    fun clearAll() {
        _items.value = emptyList()
        prefs?.edit()?.remove(KEY_LINES)?.apply()
    }

    private fun mutate(transform: (List<CartStore.CartLine>) -> List<CartStore.CartLine>): Boolean {
        var changed = false
        _items.update { current ->
            val next = transform(current)
            changed = next != current
            next
        }
        if (changed) persist()
        return changed
    }

    private fun persist() {
        prefs?.edit()
            ?.putString(KEY_LINES, json.encodeToString(ListSerializer(CartStore.CartLine.serializer()), _items.value))
            ?.apply()
    }
}
