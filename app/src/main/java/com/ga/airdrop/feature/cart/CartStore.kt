package com.ga.airdrop.feature.cart

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One package-cart status rule consumed by store, coordinator, and UI. */
fun isPackageCartEligibleStatus(statusCode: Int?): Boolean = statusCode == 7

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
    enum class CartLineKind { PACKAGE, AUCTION }

    @Serializable
    data class CartLineKey(val kind: CartLineKind, val id: Int) {
        override fun toString(): String = "${kind.name.lowercase()}:$id"
    }

    /** Mirror of Swift `FigmaCartLine`. [priceUsd] is the unit price. */
    @Serializable
    data class CartLine(
        val id: Int,
        val packageId: Int? = null,
        val imageUrl: String? = null,
        val title: String = "",
        val qty: Int = 1,
        val priceUsd: Double = 0.0,
        /**
         * Stable domain identity. Nullable only so rows written by older APKs
         * still decode; [resolvedKind] migrates them from [isAuction].
         */
        val kind: CartLineKind? = null,
        /** Numeric package weight used by the delivery fee contract. */
        val weightKg: Double? = null,
        val shippingMethod: String? = null,
        val status: String? = null,
        val statusCode: Int? = null,
        /**
         * True only after `/cart` or a successful package PUT confirms this
         * package. Old persisted package rows decode false and remain
         * removable, but cannot silently enter checkout.
         */
        val serverConfirmed: Boolean = false,
        /**
         * Swift FigmaCartLineKind — true for auction / e-commerce products
         * (owned server-side by the auction holding account), false for the
         * buyer's own shipment packages. Drives the checkout `is_auction`
         * flag so a mixed cart is declared honestly.
         */
        val isAuction: Boolean = false,
    ) {
        val resolvedKind: CartLineKind
            get() = kind ?: if (isAuction) CartLineKind.AUCTION else CartLineKind.PACKAGE

        val key: CartLineKey get() = CartLineKey(resolvedKind, id)

        fun migrated(): CartLine = if (kind == null) copy(kind = resolvedKind) else this

        /** New rows must be checkout-addressable; restored legacy rows remain removable. */
        fun isEligibleForNewCartAdd(): Boolean =
            id > 0 && (packageId ?: 0) > 0 &&
                (resolvedKind == CartLineKind.AUCTION || isPackageCartEligibleStatus(statusCode))

        fun isCheckoutEligible(): Boolean =
            isEligibleForNewCartAdd() &&
                (resolvedKind == CartLineKind.AUCTION || serverConfirmed)
    }

    data class ServerCartSnapshot internal constructor(
        internal val sequence: Long,
        internal val localMutationRevision: Long,
        internal val reconciliationGeneration: Long,
    )

    data class PackageMutation internal constructor(
        val key: CartLineKey,
        internal val line: CartLine,
        internal val adding: Boolean,
        internal val generation: Long,
    )

    private const val PREFS = "airdrop_cart"
    private const val KEY_LINES = "cart_lines"
    private const val KEY_OWNER_SESSION = "owner_session"
    private const val KEY_OWNER_ACCOUNT = "owner_account"

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null
    private var boundOwner: AuthenticatedSessionOwner? = null
    private val mutationLock = Any()
    private var localMutationRevision = 0L
    private var nextSnapshotSequence = 0L
    private var lastAppliedSnapshotSequence = 0L
    private var nextPackageMutationGeneration = 0L
    private var nextReconciliationGeneration = 0L
    private val lastLocalMutationByKey = mutableMapOf<CartLineKey, Long>()
    private val pendingPackageMutations = mutableMapOf<CartLineKey, PackageMutation>()
    /**
     * A cancelled HTTP call has an unknown server outcome. Keep checkout
     * closed for that exact key until a GET /cart snapshot begun afterwards
     * reconciles it; this is distinct from an in-flight mutation lock.
     */
    private val reconciliationRequiredByKey = mutableMapOf<CartLineKey, Long>()

    /** Fault-injection seam for JVM durability tests; null in production. */
    internal var synchronousCommitOverrideForTests: (() -> Boolean)? = null

    private val _items = MutableStateFlow<List<CartLine>>(emptyList())

    /** Sorted like Swift: title, case-insensitive. */
    val items: StateFlow<List<CartLine>> = _items

    val count: Int get() = _items.value.size

    /** Idempotent; safe to call from every shop/cart screen. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        restore(p, currentAuthenticatedOwner())
    }

    private fun restore(p: SharedPreferences, owner: AuthenticatedSessionOwner?) {
        prefs = p
        val storedSession = p.getString(KEY_OWNER_SESSION, null)
        val storedAccount = if (p.contains(KEY_OWNER_ACCOUNT)) p.getInt(KEY_OWNER_ACCOUNT, 0) else null
        val ownsPersistedRows = owner != null && storedSession == owner.sessionId &&
            (storedAccount == null || storedAccount == owner.accountId)
        boundOwner = owner
        val raw = if (ownsPersistedRows) p.getString(KEY_LINES, null) else null
        val decoded = raw?.let {
            runCatching { json.decodeFromString(ListSerializer(CartLine.serializer()), it) }.getOrNull()
        }.orEmpty()
        val restored = decoded.map(CartLine::migrated).distinctBy(CartLine::key)
        _items.value = sorted(restored)
        if (!ownsPersistedRows) p.edit().clear().apply()
        persist()
        publishCount()
    }

    /** Test-only process recreation using the same serialized preferences. */
    internal fun restoreForTests(
        storage: SharedPreferences,
        owner: AuthenticatedSessionOwner?,
    ) = synchronized(mutationLock) {
        dropProcessStateForTestsLocked()
        restore(storage, owner)
    }

    /** Drop only process memory; deliberately leaves the backing store intact. */
    internal fun dropProcessStateForTests() = synchronized(mutationLock) {
        dropProcessStateForTestsLocked()
        publishCount()
    }

    private fun dropProcessStateForTestsLocked() {
        prefs = null
        boundOwner = null
        pendingPackageMutations.clear()
        reconciliationRequiredByKey.clear()
        lastLocalMutationByKey.clear()
        localMutationRevision = 0L
        nextSnapshotSequence = 0L
        lastAppliedSnapshotSequence = 0L
        nextPackageMutationGeneration = 0L
        nextReconciliationGeneration = 0L
        _items.value = emptyList()
    }

    /** Application-scope auth boundary; does not depend on a Cart ViewModel existing. */
    fun onAuthenticatedSessionChanged(owner: AuthenticatedSessionOwner?) {
        synchronized(mutationLock) {
            val previous = boundOwner
            val sameSession = previous?.sessionId != null && previous.sessionId == owner?.sessionId
            val compatibleAccount = previous?.accountId == null || previous.accountId == owner?.accountId
            if (!sameSession || !compatibleAccount) {
                pendingPackageMutations.clear()
                reconciliationRequiredByKey.clear()
                boundOwner = owner
                mutate { emptyList() }
                if (owner == null) prefs?.edit()?.clear()?.apply() else persist()
            } else {
                boundOwner = owner
                persist()
            }
        }
    }

    /** Legacy ambiguous lookup retained for binary/test compatibility only. */
    fun contains(id: Int): Boolean = _items.value.any { it.id == id }

    fun contains(key: CartLineKey): Boolean = _items.value.any { it.key == key }

    fun contains(line: CartLine): Boolean = contains(line.key)

    /** Returns true when the line was added (false = already in cart). */
    fun add(line: CartLine): Boolean {
        if (!line.isEligibleForNewCartAdd()) return false
        // Membership check folded into the atomic transform so two concurrent
        // adds of the same id can't both slip past a separate contains() gate.
        return mutate { list ->
            val migrated = line.migrated()
            if (list.any { it.key == migrated.key }) list else list + migrated
        }
    }

    /** Returns the resulting membership (true = now in cart), like Swift `toggle`. */
    fun toggle(line: CartLine): Boolean = synchronized(mutationLock) {
        val migrated = line.migrated()
        var resultingMembership = false
        mutate { current ->
            if (current.any { it.key == migrated.key }) {
                current.filterNot { it.key == migrated.key }
            } else if (migrated.isEligibleForNewCartAdd()) {
                resultingMembership = true
                current + migrated
            } else {
                current
            }
        }
        resultingMembership
    }

    /** Legacy ambiguous removal; new code must use [remove] with a key. */
    fun remove(id: Int) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    fun remove(key: CartLineKey) {
        mutate { list -> list.filterNot { it.key == key } }
    }

    fun remove(keys: Set<CartLineKey>) {
        if (keys.isEmpty()) return
        mutate { list -> list.filterNot { it.key in keys } }
    }

    /**
     * Paid-return phase one. The exact rows reach disk before the pending
     * checkout authority is consumed. A retry is idempotent when the rows are
     * already absent, which makes a crash between the two durable commits
     * recoverable instead of resurrecting paid items.
     */
    fun removePaidKeysDurably(keys: Set<CartLineKey>): Boolean = synchronized(mutationLock) {
        if (keys.isEmpty()) return@synchronized false
        val before = _items.value
        val after = sorted(before.filterNot { it.key in keys })
        if (!persistSynchronously(after)) return@synchronized false
        keys.forEach(pendingPackageMutations::remove)
        _items.value = after
        val changedKeys = before.associateBy(CartLine::key).keys - after.associateBy(CartLine::key).keys
        markLocalMutation(changedKeys)
        publishCount()
        true
    }

    /** Capture before dispatching GET `/cart`; its response is reconciled against this revision. */
    fun beginServerCartSnapshot(): ServerCartSnapshot = synchronized(mutationLock) {
        ServerCartSnapshot(
            sequence = ++nextSnapshotSequence,
            localMutationRevision = localMutationRevision,
            reconciliationGeneration = nextReconciliationGeneration,
        )
    }

    /**
     * Server `/cart` is authoritative for package rows, except keys changed by
     * a newer local server mutation. Later-started snapshots supersede older
     * ones, so delayed GETs cannot resurrect a successful DELETE or erase a
     * successful PUT. Auction rows always stay local.
     */
    fun reconcileServerPackages(lines: List<CartLine>, snapshot: ServerCartSnapshot): Boolean =
        synchronized(mutationLock) {
            if (snapshot.sequence <= lastAppliedSnapshotSequence) return@synchronized false
            lastAppliedSnapshotSequence = snapshot.sequence
            val packages = lines
                .map(CartLine::migrated)
                .filter {
                    it.resolvedKind == CartLineKind.PACKAGE &&
                        it.id > 0 && (it.packageId ?: 0) > 0
                }
                .associateBy(CartLine::key)
            // Frozen Swift treats a successful-but-empty package payload as
            // ambiguous because decoder/envelope failures collapse to the same
            // shape. Preserve cached package rows and any unknown-mutation hold
            // until a non-empty authoritative snapshot arrives. Explicit local
            // DELETE success and paid removal still remove exact keys directly.
            if (
                packages.isEmpty() &&
                _items.value.any { it.resolvedKind == CartLineKind.PACKAGE }
            ) {
                return@synchronized false
            }
            val changed = mutate(trackAsLocalMutation = false) { current ->
                val localAuctions = current.filter { it.resolvedKind == CartLineKind.AUCTION }
                val protectedLocalPackages = current.filter { line ->
                    line.resolvedKind == CartLineKind.PACKAGE &&
                        (isProtectedFrom(snapshot, line.key) ||
                            (!line.serverConfirmed && line.key !in packages))
                }
                val authoritativePackages = packages.values.filterNot { line ->
                    isProtectedFrom(snapshot, line.key)
                }
                localAuctions + protectedLocalPackages + authoritativePackages
            }
            reconciliationRequiredByKey.entries.removeAll { (_, generation) ->
                generation <= snapshot.reconciliationGeneration
            }
            changed
        }

    fun beginPackageMutation(line: CartLine, adding: Boolean): PackageMutation? =
        synchronized(mutationLock) {
            val migrated = line.migrated()
            if (migrated.resolvedKind != CartLineKind.PACKAGE || migrated.id <= 0 ||
                (adding && !migrated.isEligibleForNewCartAdd()) ||
                reconciliationRequiredByKey.containsKey(migrated.key)
            ) {
                return@synchronized null
            }
            PackageMutation(
                key = migrated.key,
                line = migrated,
                adding = adding,
                generation = ++nextPackageMutationGeneration,
            ).also { pendingPackageMutations[migrated.key] = it }
        }

    fun hasPendingPackageMutations(keys: Collection<CartLineKey>? = null): Boolean =
        synchronized(mutationLock) {
            if (keys == null) {
                pendingPackageMutations.isNotEmpty() || reconciliationRequiredByKey.isNotEmpty()
            } else {
                keys.any { pendingPackageMutations.containsKey(it) || reconciliationRequiredByKey.containsKey(it) }
            }
        }

    /**
     * Cancellation-safe transition from "request running" to "server outcome
     * unknown". A newer exact-key mutation wins and cannot be cancelled by a
     * delayed older coroutine.
     */
    fun markPackageMutationOutcomeUnknown(mutation: PackageMutation): Boolean =
        synchronized(mutationLock) {
            val pending = pendingPackageMutations[mutation.key]
            if (pending?.generation != mutation.generation) return@synchronized false
            pendingPackageMutations.remove(mutation.key)
            reconciliationRequiredByKey[mutation.key] = ++nextReconciliationGeneration
            true
        }

    /** Returns false for a stale completion superseded by a newer mutation. */
    fun finishPackageMutation(mutation: PackageMutation, succeeded: Boolean): Boolean =
        synchronized(mutationLock) {
            val pending = pendingPackageMutations[mutation.key]
            if (pending?.generation != mutation.generation) return@synchronized false
            pendingPackageMutations.remove(mutation.key)
            if (succeeded) {
                val changed = if (mutation.adding) {
                    val confirmed = mutation.line.copy(serverConfirmed = true)
                    mutate { current ->
                        val withoutOld = current.filterNot { it.key == mutation.key }
                        withoutOld + confirmed
                    }
                } else {
                    mutate { current -> current.filterNot { it.key == mutation.key } }
                }
                // Even a no-op server success invalidates snapshots begun before it.
                if (!changed) markLocalMutation(setOf(mutation.key))
            }
            true
        }

    /** Clear all lines — after verified payment, cache clear, or logout. */
    fun clear() {
        synchronized(mutationLock) {
            pendingPackageMutations.clear()
            reconciliationRequiredByKey.clear()
            mutate { emptyList() }
        }
    }

    /** Swift parity: `items.reduce(0) { $0 + qty * priceUSD }`. */
    fun totalUsd(): Double = _items.value
        .filter(CartLine::isCheckoutEligible)
        .sumOf { it.qty * it.priceUsd }

    fun totalWeightKg(): Double? = _items.value
        .filter(CartLine::isCheckoutEligible)
        .mapNotNull(CartLine::weightKg)
        .sum()
        .takeIf { it > 0.0 }

    // Atomic read-modify-write. MutableStateFlow.update re-runs the transform
    // on a CAS miss, so concurrent add/remove/clear can't clobber each other's
    // changes the way `_items.value = f(_items.value)` did (BUG_AUDIT H30).
    // Returns whether the list actually changed so add() can report membership
    // and we skip a redundant persist/publish on no-op mutations.
    private fun mutate(
        trackAsLocalMutation: Boolean = true,
        transform: (List<CartLine>) -> List<CartLine>,
    ): Boolean = synchronized(mutationLock) {
        var before: List<CartLine> = emptyList()
        var after: List<CartLine> = emptyList()
        _items.update { current ->
            before = current
            sorted(transform(current)).also { after = it }
        }
        val changed = after != before
        if (changed) {
            if (trackAsLocalMutation) {
                val beforeByKey = before.associateBy(CartLine::key)
                val afterByKey = after.associateBy(CartLine::key)
                markLocalMutation((beforeByKey.keys + afterByKey.keys).filterTo(mutableSetOf()) { key ->
                    beforeByKey[key] != afterByKey[key]
                })
            }
            persist()
            publishCount()
        }
        changed
    }

    private fun isProtectedFrom(snapshot: ServerCartSnapshot, key: CartLineKey): Boolean =
        pendingPackageMutations.containsKey(key) ||
            (lastLocalMutationByKey[key] ?: Long.MIN_VALUE) > snapshot.localMutationRevision

    private fun markLocalMutation(keys: Set<CartLineKey>) {
        if (keys.isEmpty()) return
        val revision = ++localMutationRevision
        keys.forEach { lastLocalMutationByKey[it] = revision }
    }

    private fun sorted(lines: List<CartLine>): List<CartLine> =
        lines.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private fun persist() {
        val owner = boundOwner ?: run {
            prefs?.edit()?.clear()?.apply()
            return
        }
        editState(owner, _items.value)?.apply()
    }

    private fun persistSynchronously(lines: List<CartLine>): Boolean {
        synchronousCommitOverrideForTests?.let { return it() }
        val owner = boundOwner ?: return false
        return editState(owner, lines)?.commit() == true
    }

    private fun editState(
        owner: AuthenticatedSessionOwner,
        lines: List<CartLine>,
    ): SharedPreferences.Editor? = prefs?.edit()?.apply {
            putString(KEY_LINES, json.encodeToString(ListSerializer(CartLine.serializer()), lines))
            putString(KEY_OWNER_SESSION, owner.sessionId)
            if (owner.accountId == null) remove(KEY_OWNER_ACCOUNT) else putInt(KEY_OWNER_ACCOUNT, owner.accountId)
        }

    private fun publishCount() {
        SessionStore.update { it.copy(cartCount = _items.value.size) }
    }

    private fun currentAuthenticatedOwner(): AuthenticatedSessionOwner? =
        com.ga.airdrop.core.auth.AuthTokenStore.snapshot().let { snapshot ->
            snapshot.sessionId?.takeIf { snapshot.token != null }
                ?.let { AuthenticatedSessionOwner(it, snapshot.accountId) }
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
    private const val KEY_OWNER_SESSION = "owner_session"
    private const val KEY_OWNER_ACCOUNT = "owner_account"

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null
    private var boundOwner: AuthenticatedSessionOwner? = null

    private val _items = MutableStateFlow<List<CartStore.CartLine>>(emptyList())

    /** Newest first, like Swift `list.insert(line, at: 0)`. */
    val items: StateFlow<List<CartStore.CartLine>> = _items

    val count: Int get() = _items.value.size

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val owner = com.ga.airdrop.core.auth.AuthTokenStore.snapshot().let { snapshot ->
            snapshot.sessionId?.takeIf { snapshot.token != null }
                ?.let { AuthenticatedSessionOwner(it, snapshot.accountId) }
        }
        val storedSession = p.getString(KEY_OWNER_SESSION, null)
        val storedAccount = if (p.contains(KEY_OWNER_ACCOUNT)) p.getInt(KEY_OWNER_ACCOUNT, 0) else null
        val ownsPersistedRows = owner != null && storedSession == owner.sessionId &&
            (storedAccount == null || storedAccount == owner.accountId)
        boundOwner = owner
        val raw = if (ownsPersistedRows) p.getString(KEY_LINES, null) else null
        val restored = raw?.let {
            runCatching { json.decodeFromString(ListSerializer(CartStore.CartLine.serializer()), it) }
                .getOrNull()
        }.orEmpty()
        val migrated = restored.map(CartStore.CartLine::migrated).distinctBy(CartStore.CartLine::key)
        _items.value = migrated
        if (!ownsPersistedRows) p.edit().clear().apply()
        persist()
    }

    fun contains(line: CartStore.CartLine): Boolean =
        _items.value.any { it.key == line.key }

    /** Idempotent save. Returns true only when a new saved row was inserted. */
    fun save(line: CartStore.CartLine): Boolean =
        mutate { list -> if (list.any { it.key == line.key }) list else listOf(line.migrated()) + list }

    fun remove(id: Int) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    fun remove(key: CartStore.CartLineKey) {
        mutate { list -> list.filterNot { it.key == key } }
    }

    fun clearAll() {
        _items.value = emptyList()
        prefs?.edit()?.remove(KEY_LINES)?.apply()
    }

    fun onAuthenticatedSessionChanged(owner: AuthenticatedSessionOwner?) {
        val previous = boundOwner
        val sameSession = previous?.sessionId != null && previous.sessionId == owner?.sessionId
        val compatibleAccount = previous?.accountId == null || previous.accountId == owner?.accountId
        if (!sameSession || !compatibleAccount) {
            boundOwner = owner
            clearAll()
            if (owner == null) prefs?.edit()?.clear()?.apply() else persist()
        } else {
            boundOwner = owner
            persist()
        }
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
        val owner = boundOwner ?: run {
            prefs?.edit()?.clear()?.apply()
            return
        }
        prefs?.edit()?.apply {
            putString(KEY_LINES, json.encodeToString(ListSerializer(CartStore.CartLine.serializer()), _items.value))
            putString(KEY_OWNER_SESSION, owner.sessionId)
            if (owner.accountId == null) remove(KEY_OWNER_ACCOUNT) else putInt(KEY_OWNER_ACCOUNT, owner.accountId)
        }?.apply()
    }

}
