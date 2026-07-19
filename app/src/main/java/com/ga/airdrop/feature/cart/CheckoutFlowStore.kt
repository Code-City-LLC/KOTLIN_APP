package com.ga.airdrop.feature.cart

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Canonical "Your Note" persistence rail shared by Cart and Order Summary.
 *
 * Frozen Swift scopes the note by account and keeps it after checkout. The
 * ViewModel is only a projection of this store. Checkout identity does not
 * carry a second editable note; final dispatch captures this store while the
 * exact authenticated owner is locked.
 */
object CartNoteStore {
    private const val PREFS = "airdrop_cart_notes"
    private const val KEY_PREFIX = "account_"
    private const val MAX_NOTE_LENGTH = 2_000

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** Anonymous or not-yet-bound sessions cannot share a device-level note. */
    @Synchronized
    fun note(owner: AuthenticatedSessionOwner): String {
        val key = owner.preferenceKey() ?: return ""
        return prefs?.getString(key, null).orEmpty()
    }

    /** Swift parity: trim on save; an empty value removes only this account. */
    @Synchronized
    fun save(owner: AuthenticatedSessionOwner, value: String): Boolean {
        val key = owner.preferenceKey() ?: return false
        val normalized = value.trim().take(MAX_NOTE_LENGTH)
        val editor = prefs?.edit() ?: return false
        if (normalized.isEmpty()) editor.remove(key) else editor.putString(key, normalized)
        return editor.commit()
    }

    @Synchronized
    internal fun restoreForTests(storage: SharedPreferences) {
        prefs = storage
    }

    @Synchronized
    internal fun dropProcessStateForTests() {
        prefs = null
    }

    private fun AuthenticatedSessionOwner.preferenceKey(): String? =
        accountId?.takeIf { it > 0 }?.let { "$KEY_PREFIX$it" }
}

@Serializable
enum class CheckoutPhase { DELIVERY, PROFILE_INFORMATION, ORDER_SUMMARY }

enum class CapturedLineRemovalResult { UPDATED, EMPTY }

@Serializable
data class CheckoutFlow(
    val id: String,
    val ownerSessionId: String,
    val ownerAccountId: Int? = null,
    val cartKeys: List<CartStore.CartLineKey>,
    val packageIds: List<Int>,
    val isAuction: Boolean,
    val totalWeightKg: Double? = null,
    val currency: String? = null,
    val deliveryMode: String? = null,
    val deliveryAddress: String? = null,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val pickupLocation: String? = null,
    val deliveryFee: Double? = null,
    val deliveryFeeCurrency: String? = null,
    val phase: CheckoutPhase = CheckoutPhase.DELIVERY,
) {
    fun isOwnedBy(owner: AuthenticatedSessionOwner): Boolean =
        ownerSessionId == owner.sessionId &&
            (ownerAccountId == null || ownerAccountId == owner.accountId)

    companion object {
        fun start(
            owner: AuthenticatedSessionOwner,
            lines: List<CartStore.CartLine>,
        ): CheckoutFlow? {
            if (lines.isEmpty()) return null
            if (lines.any { !it.isCheckoutEligible() }) return null
            if (lines.any { it.id <= 0 }) return null
            val packageIds = lines.mapNotNull(CartStore.CartLine::packageId)
            if (packageIds.size != lines.size || packageIds.any { it <= 0 }) return null
            val cartKeys = lines.map(CartStore.CartLine::key)
            if (cartKeys.distinct().size != cartKeys.size) return null
            if (CartStore.hasPendingPackageMutations(cartKeys)) return null
            return CheckoutFlow(
                id = UUID.randomUUID().toString(),
                ownerSessionId = owner.sessionId,
                ownerAccountId = owner.accountId,
                cartKeys = cartKeys,
                packageIds = packageIds,
                isAuction = lines.any { it.resolvedKind == CartStore.CartLineKind.AUCTION },
                totalWeightKg = lines.mapNotNull(CartStore.CartLine::weightKg).sum().takeIf { it > 0.0 },
            )
        }
    }
}

@Serializable
data class PendingHostedCheckout(
    val flowId: String,
    val ownerSessionId: String,
    val ownerAccountId: Int? = null,
    val checkoutSessionId: String,
    val cartKeys: List<CartStore.CartLineKey>,
    // Stripe hosted sessions expire; without a timestamp an abandoned Custom
    // Tab deadlocked checkout FOREVER. 0 on legacy persisted rows = expired.
    val createdAtMs: Long = 0L,
) {
    fun matches(sessionId: String, owner: AuthenticatedSessionOwner): Boolean =
        checkoutSessionId == sessionId && ownerSessionId == owner.sessionId &&
            (ownerAccountId == null || ownerAccountId == owner.accountId)
}

/**
 * Durable authority written before the checkout POST leaves the device.
 *
 * The checkout endpoint has no idempotency key or lookup contract. Once a
 * request is dispatched, cancellation/process death makes its outcome
 * unknowable. Keeping this exact authority prevents a second POST until an
 * authoritative hosted-session response can replace it.
 */
@Serializable
data class PendingCheckoutCreation(
    val id: String,
    val flowId: String,
    val ownerSessionId: String,
    val ownerAccountId: Int? = null,
    val cartKeys: List<CartStore.CartLineKey>,
    val packageIds: List<Int>,
    val isAuction: Boolean,
    // See PendingHostedCheckout.createdAtMs — 0 on legacy rows = expired.
    val createdAtMs: Long = 0L,
) {
    fun isOwnedBy(owner: AuthenticatedSessionOwner): Boolean =
        ownerSessionId == owner.sessionId &&
            (ownerAccountId == null || ownerAccountId == owner.accountId)

    fun matches(flow: CheckoutFlow, owner: AuthenticatedSessionOwner): Boolean =
        isOwnedBy(owner) && flowId == flow.id && cartKeys == flow.cartKeys &&
            packageIds == flow.packageIds && isAuction == flow.isAuction
}

enum class CheckoutCurrency(val wireValue: String) { USD("USD"), JMD("JMD") }
enum class CheckoutNextRoute { PROFILE_INFORMATION, ORDER_SUMMARY }
enum class CheckoutPaymentRail { STRIPE, NCB_POWERTRANZ }

fun parseCheckoutCurrency(currency: String?): CheckoutCurrency? = when (currency?.trim()?.uppercase()) {
    CheckoutCurrency.USD.wireValue -> CheckoutCurrency.USD
    CheckoutCurrency.JMD.wireValue -> CheckoutCurrency.JMD
    else -> null
}

fun checkoutNextRoute(currency: String?): CheckoutNextRoute? =
    when (parseCheckoutCurrency(currency)) {
        CheckoutCurrency.JMD -> CheckoutNextRoute.PROFILE_INFORMATION
        CheckoutCurrency.USD -> CheckoutNextRoute.ORDER_SUMMARY
        null -> null
    }

fun checkoutPaymentRail(currency: String?): CheckoutPaymentRail? =
    when (parseCheckoutCurrency(currency)) {
        CheckoutCurrency.USD -> CheckoutPaymentRail.STRIPE
        CheckoutCurrency.JMD -> CheckoutPaymentRail.NCB_POWERTRANZ
        null -> null
    }

/**
 * One persisted checkout owner. It carries non-secret route/context identity
 * across process recreation and is cleared at every local auth teardown.
 */
object CheckoutFlowStore {
    private const val PREFS = "airdrop_checkout_flow"
    private const val KEY_FLOW = "flow"
    private const val KEY_PENDING_HOSTED = "pending_hosted"
    private const val KEY_PENDING_CREATION = "pending_creation"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: SharedPreferences? = null
    private var flow: CheckoutFlow? = null
    private var pendingHosted: PendingHostedCheckout? = null
    private var pendingCreation: PendingCheckoutCreation? = null

    /** Fault-injection seam for JVM durability tests; null in production. */
    internal var synchronousCommitOverrideForTests: (() -> Boolean)? = null

    /** Clock seam for TTL tests; production = wall clock. */
    internal var clockMsForTests: (() -> Long)? = null

    private fun nowMs(): Long = clockMsForTests?.invoke() ?: System.currentTimeMillis()

    // Stripe Checkout Sessions expire after 24h — after that no browser can
    // complete the payment, so the pending authority is safe to release.
    private const val HOSTED_PENDING_TTL_MS: Long = 24L * 60 * 60 * 1000

    // A creation whose response never arrived: the customer never received a
    // URL, so no browser holds the session. 30 minutes is generous.
    private const val CREATION_PENDING_TTL_MS: Long = 30L * 60 * 1000

    /**
     * Deadlock fix: abandoned checkouts (closed Custom Tab, crashed return)
     * previously blocked start() FOREVER because nothing ever cleared the
     * pending records outside the payment-return deeplink. Expire them on
     * Stripe's own lifetime instead. Legacy rows (createdAtMs=0) expire
     * immediately, healing already-stuck installs.
     */
    @Synchronized
    private fun expireStalePending() {
        val now = nowMs()
        var changed = false
        pendingHosted?.let {
            if (now - it.createdAtMs > HOSTED_PENDING_TTL_MS) {
                pendingHosted = null
                changed = true
            }
        }
        pendingCreation?.let {
            if (now - it.createdAtMs > CREATION_PENDING_TTL_MS) {
                pendingCreation = null
                changed = true
            }
        }
        if (changed) persist()
    }

    @Synchronized
    fun init(context: Context) {
        CartNoteStore.init(context)
        if (prefs != null) return
        restore(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            currentAuthenticatedOwner(),
        )
    }

    private fun restore(storage: SharedPreferences, owner: AuthenticatedSessionOwner?) {
        prefs = storage
        flow = decode(KEY_FLOW, CheckoutFlow.serializer())
        pendingHosted = decode(KEY_PENDING_HOSTED, PendingHostedCheckout.serializer())
        pendingCreation = decode(KEY_PENDING_CREATION, PendingCheckoutCreation.serializer())
        if (owner == null || flow?.isOwnedBy(owner) != true ||
            (pendingHosted != null && pendingHosted?.matches(pendingHosted!!.checkoutSessionId, owner) != true) ||
            (pendingCreation != null && pendingCreation?.matches(flow!!, owner) != true) ||
            (pendingHosted != null && pendingCreation != null)
        ) {
            clear()
        } else {
            bindFlowOwner(owner)
            bindPendingOwner(owner)
            bindCreationOwner(owner)
        }
    }

    /** Test-only process recreation using the same serialized preferences. */
    @Synchronized
    internal fun restoreForTests(
        storage: SharedPreferences,
        owner: AuthenticatedSessionOwner?,
    ) {
        prefs = null
        flow = null
        pendingHosted = null
        pendingCreation = null
        restore(storage, owner)
    }

    /** Drop only process memory; deliberately leaves the backing store intact. */
    @Synchronized
    internal fun dropProcessStateForTests() {
        prefs = null
        flow = null
        pendingHosted = null
        pendingCreation = null
    }

    @Synchronized
    fun onAuthenticatedSessionChanged(owner: AuthenticatedSessionOwner?) {
        if (owner == null || flow?.isOwnedBy(owner) != true ||
            (pendingHosted != null && pendingHosted?.matches(pendingHosted!!.checkoutSessionId, owner) != true) ||
            (pendingCreation != null && pendingCreation?.matches(flow!!, owner) != true) ||
            (pendingHosted != null && pendingCreation != null)
        ) {
            clear()
            return
        }
        bindFlowOwner(owner)
        if (pendingHosted != null) bindPendingOwner(owner)
        if (pendingCreation != null) bindCreationOwner(owner)
    }

    @Synchronized
    fun start(owner: AuthenticatedSessionOwner, lines: List<CartStore.CartLine>): CheckoutFlow? {
        expireStalePending()
        // A browser may still complete this exact Stripe session. Never orphan
        // it by silently replacing its owner/cart keys with a second flow.
        if (pendingHosted != null || pendingCreation != null) return null
        val next = CheckoutFlow.start(owner, lines) ?: return null
        flow = next
        pendingHosted = null
        pendingCreation = null
        persist()
        return next
    }

    @Synchronized
    fun current(owner: AuthenticatedSessionOwner): CheckoutFlow? =
        if (bindFlowOwner(owner)) flow else null

    @Synchronized
    fun update(
        owner: AuthenticatedSessionOwner,
        expectedFlowId: String? = null,
        transform: (CheckoutFlow) -> CheckoutFlow,
    ): CheckoutFlow? {
        if (pendingCreation != null) return null
        if (!bindFlowOwner(owner)) return null
        val current = flow ?: return null
        if (expectedFlowId != null && current.id != expectedFlowId) return null
        val updated = transform(current).copy(
            id = current.id,
            ownerSessionId = current.ownerSessionId,
            ownerAccountId = current.ownerAccountId,
            cartKeys = current.cartKeys,
            packageIds = current.packageIds,
            isAuction = current.isAuction,
            totalWeightKg = current.totalWeightKg,
        )
        if (!isValidTransition(current, updated)) return null
        flow = updated
        persist()
        return updated
    }

    /**
     * Shrinks the immutable checkout capture only after its cart mutation has
     * succeeded. The updated identity reaches disk before Order Summary may
     * continue; removing the final row exits the checkout instead of leaving
     * an empty payable flow behind.
     */
    @Synchronized
    fun removeCapturedLine(
        owner: AuthenticatedSessionOwner,
        expectedFlowId: String,
        removedKey: CartStore.CartLineKey,
        remainingLines: List<CartStore.CartLine>,
    ): CapturedLineRemovalResult? {
        if (pendingHosted != null || pendingCreation != null || !bindFlowOwner(owner)) return null
        val current = flow?.takeIf {
            it.id == expectedFlowId && it.phase == CheckoutPhase.ORDER_SUMMARY
        } ?: return null
        if (current.cartKeys.size != current.packageIds.size) return null
        val removedIndex = current.cartKeys.indexOf(removedKey).takeIf { it >= 0 } ?: return null
        if (current.cartKeys.lastIndexOf(removedKey) != removedIndex) return null

        val expectedKeys = current.cartKeys.toMutableList().apply { removeAt(removedIndex) }
        val expectedPackageIds = current.packageIds.toMutableList().apply { removeAt(removedIndex) }
        val remainingByKey = remainingLines.associateBy(CartStore.CartLine::key)
        if (remainingByKey.size != remainingLines.size) return null
        val exactRemaining = expectedKeys.map { remainingByKey[it] ?: return null }
        if (exactRemaining.any { !it.isCheckoutEligible() } ||
            exactRemaining.mapNotNull(CartStore.CartLine::packageId) != expectedPackageIds ||
            CartStore.hasPendingPackageMutations(expectedKeys)
        ) return null

        val previous = current
        val result = if (exactRemaining.isEmpty()) {
            flow = null
            CapturedLineRemovalResult.EMPTY
        } else {
            flow = current.copy(
                cartKeys = expectedKeys,
                packageIds = expectedPackageIds,
                isAuction = exactRemaining.any {
                    it.resolvedKind == CartStore.CartLineKind.AUCTION
                },
                totalWeightKg = exactRemaining.mapNotNull(CartStore.CartLine::weightKg)
                    .sum()
                    .takeIf { it > 0.0 },
            )
            CapturedLineRemovalResult.UPDATED
        }
        if (!persistSynchronously()) {
            flow = previous
            return null
        }
        return result
    }

    /** Explicit Back-to-Delivery transition; forbidden once Stripe is pending. */
    @Synchronized
    fun rewindToDelivery(owner: AuthenticatedSessionOwner, expectedFlowId: String): CheckoutFlow? {
        if (pendingHosted != null || pendingCreation != null || !bindFlowOwner(owner)) return null
        val current = flow?.takeIf { it.id == expectedFlowId } ?: return null
        if (current.phase == CheckoutPhase.DELIVERY) return current
        return current.copy(currency = null, phase = CheckoutPhase.DELIVERY).also {
            flow = it
            persist()
        }
    }

    /**
     * Order Summary Back must persist the phase before navigation. JMD returns
     * to Profile Information; USD returns to Delivery so currency can be
     * chosen again. A pending hosted session is never rewound.
     */
    @Synchronized
    fun rewindOrderSummary(owner: AuthenticatedSessionOwner, expectedFlowId: String): CheckoutFlow? {
        if (pendingHosted != null || pendingCreation != null || !bindFlowOwner(owner)) return null
        val current = flow?.takeIf {
            it.id == expectedFlowId && it.phase == CheckoutPhase.ORDER_SUMMARY
        } ?: return null
        val rewound = when (parseCheckoutCurrency(current.currency)) {
            CheckoutCurrency.JMD -> current.copy(phase = CheckoutPhase.PROFILE_INFORMATION)
            CheckoutCurrency.USD -> current.copy(currency = null, phase = CheckoutPhase.DELIVERY)
            null -> return null
        }
        flow = rewound
        if (!persistSynchronously()) {
            flow = current
            return null
        }
        return rewound
    }

    @Synchronized
    fun beginHostedCheckoutCreation(owner: AuthenticatedSessionOwner): PendingCheckoutCreation? {
        expireStalePending()
        if (pendingHosted != null || pendingCreation != null || !bindFlowOwner(owner)) return null
        val current = flow ?: return null
        if (current.phase != CheckoutPhase.ORDER_SUMMARY ||
            parseCheckoutCurrency(current.currency) != CheckoutCurrency.USD ||
            current.cartKeys.isEmpty() || current.packageIds.isEmpty() ||
            current.cartKeys.size != current.packageIds.size
        ) return null
        val creation = PendingCheckoutCreation(
            id = UUID.randomUUID().toString(),
            flowId = current.id,
            ownerSessionId = current.ownerSessionId,
            ownerAccountId = current.ownerAccountId,
            cartKeys = current.cartKeys,
            packageIds = current.packageIds,
            isAuction = current.isAuction,
            createdAtMs = nowMs(),
        )
        pendingCreation = creation
        // This commit is the duplicate-POST barrier. A failed commit means the
        // caller must send zero network requests.
        if (!persistSynchronously()) {
            pendingCreation = null
            return null
        }
        return creation
    }

    @Synchronized
    fun creating(owner: AuthenticatedSessionOwner): PendingCheckoutCreation? {
        expireStalePending()
        return pendingCreation?.takeIf { bindCreationOwner(owner) }
    }

    @Synchronized
    fun recordHostedCheckout(
        owner: AuthenticatedSessionOwner,
        creationId: String,
        checkoutSessionId: String,
    ): PendingHostedCheckout? {
        if (!bindFlowOwner(owner) || !bindCreationOwner(owner)) return null
        val current = flow ?: return null
        if (current.phase != CheckoutPhase.ORDER_SUMMARY ||
            parseCheckoutCurrency(current.currency) != CheckoutCurrency.USD
        ) return null
        if (creationId.isBlank() || checkoutSessionId.isBlank()) return null
        val creation = pendingCreation?.takeIf {
            it.id == creationId && it.matches(current, owner)
        } ?: return null
        val existing = pendingHosted
        if (existing != null) {
            return existing.takeIf {
                it.flowId == current.id && it.matches(checkoutSessionId, owner)
            }
        }
        val recorded = PendingHostedCheckout(
            flowId = current.id,
            ownerSessionId = current.ownerSessionId,
            ownerAccountId = current.ownerAccountId,
            checkoutSessionId = checkoutSessionId,
            cartKeys = current.cartKeys,
            createdAtMs = nowMs(),
        )
        pendingHosted = recorded
        pendingCreation = null
        // This identity is the only authority allowed to consume paid cart
        // rows after a browser/process round-trip. It must reach disk before
        // callers expose the hosted URL; apply()'s asynchronous flush leaves
        // a process-death window that can orphan a real payment.
        if (!persistSynchronously()) {
            pendingHosted = null
            pendingCreation = creation
            return null
        }
        return recorded
    }

    @Synchronized
    fun pending(sessionId: String, owner: AuthenticatedSessionOwner): PendingHostedCheckout? =
        pendingHosted?.takeIf {
            bindPendingOwner(owner) && it.checkoutSessionId == sessionId
        }

    /** Recover the sole exact pending identity for a bare Stripe cancel URL. */
    @Synchronized
    fun pending(owner: AuthenticatedSessionOwner): PendingHostedCheckout? {
        expireStalePending()
        return pendingHosted?.takeIf { bindPendingOwner(owner) }
    }

    /** Returns the exact initiating rows and consumes the verified checkout. */
    @Synchronized
    fun consumePaid(sessionId: String, owner: AuthenticatedSessionOwner): Set<CartStore.CartLineKey>? {
        if (!bindPendingOwner(owner)) return null
        val pending = pendingHosted?.takeIf { it.checkoutSessionId == sessionId } ?: return null
        val previousFlow = flow
        pendingHosted = null
        if (flow?.id == pending.flowId) flow = null
        if (!persistSynchronously()) {
            pendingHosted = pending
            flow = previousFlow
            return null
        }
        return pending.cartKeys.toSet()
    }

    /** Called only after the bound status endpoint returns a terminal non-paid state. */
    @Synchronized
    fun releaseTerminalNotPaid(sessionId: String, owner: AuthenticatedSessionOwner): Boolean {
        if (!bindPendingOwner(owner)) return false
        val pending = pendingHosted?.takeIf { it.checkoutSessionId == sessionId } ?: return false
        val previousFlow = flow
        pendingHosted = null
        if (flow?.id == pending.flowId) flow = null
        return if (persistSynchronously()) {
            true
        } else {
            pendingHosted = pending
            flow = previousFlow
            false
        }
    }

    @Synchronized
    fun clear(): Boolean {
        flow = null
        pendingHosted = null
        pendingCreation = null
        // Auth/security teardown must not leave an asynchronous write window.
        // On disk failure memory remains fail-closed; the persisted owner UUID
        // cannot bind to the replacement authenticated session on reload.
        return prefs?.edit()?.clear()?.commit() ?: true
    }

    private fun persist() {
        editPersistedState()?.apply()
    }

    private fun persistSynchronously(): Boolean =
        synchronousCommitOverrideForTests?.invoke() ?: (editPersistedState()?.commit() == true)

    private fun editPersistedState(): SharedPreferences.Editor? =
        prefs?.edit()?.apply {
            val currentFlow = flow
            if (currentFlow == null) remove(KEY_FLOW)
            else putString(KEY_FLOW, json.encodeToString(CheckoutFlow.serializer(), currentFlow))
            val pending = pendingHosted
            if (pending == null) remove(KEY_PENDING_HOSTED)
            else putString(KEY_PENDING_HOSTED, json.encodeToString(PendingHostedCheckout.serializer(), pending))
            val creation = pendingCreation
            if (creation == null) remove(KEY_PENDING_CREATION)
            else putString(KEY_PENDING_CREATION, json.encodeToString(PendingCheckoutCreation.serializer(), creation))
        }

    /** Same UUID may strengthen from unknown to known account, never weaken or switch. */
    private fun bindFlowOwner(owner: AuthenticatedSessionOwner): Boolean {
        val current = flow ?: return false
        if (!current.isOwnedBy(owner)) return false
        if (current.ownerAccountId == null && owner.accountId != null) {
            flow = current.copy(ownerAccountId = owner.accountId)
            pendingHosted = pendingHosted?.takeIf { it.flowId == current.id }?.copy(
                ownerAccountId = owner.accountId,
            ) ?: pendingHosted
            pendingCreation = pendingCreation?.takeIf { it.flowId == current.id }?.copy(
                ownerAccountId = owner.accountId,
            ) ?: pendingCreation
            persist()
        }
        return true
    }

    private fun bindPendingOwner(owner: AuthenticatedSessionOwner): Boolean {
        val pending = pendingHosted ?: return false
        if (!pending.matches(pending.checkoutSessionId, owner)) return false
        if (pending.ownerAccountId == null && owner.accountId != null) {
            pendingHosted = pending.copy(ownerAccountId = owner.accountId)
            flow = flow?.takeIf { it.id == pending.flowId }?.copy(ownerAccountId = owner.accountId) ?: flow
            persist()
        }
        return true
    }

    private fun bindCreationOwner(owner: AuthenticatedSessionOwner): Boolean {
        val creation = pendingCreation ?: return false
        val current = flow ?: return false
        if (!creation.matches(current, owner)) return false
        if (creation.ownerAccountId == null && owner.accountId != null) {
            pendingCreation = creation.copy(ownerAccountId = owner.accountId)
            flow = current.copy(ownerAccountId = owner.accountId)
            persist()
        }
        return true
    }

    private fun isValidTransition(current: CheckoutFlow, updated: CheckoutFlow): Boolean {
        val updatedCurrency = parseCheckoutCurrency(updated.currency)
        val currentCurrency = parseCheckoutCurrency(current.currency)
        if (current.currency != null && updatedCurrency != currentCurrency) return false
        return when (current.phase) {
            CheckoutPhase.DELIVERY -> when (updated.phase) {
                CheckoutPhase.DELIVERY -> updated.currency == null || updatedCurrency != null
                CheckoutPhase.PROFILE_INFORMATION -> updatedCurrency == CheckoutCurrency.JMD
                CheckoutPhase.ORDER_SUMMARY -> updatedCurrency == CheckoutCurrency.USD
            }
            CheckoutPhase.PROFILE_INFORMATION ->
                updatedCurrency == CheckoutCurrency.JMD &&
                    updated.phase in setOf(CheckoutPhase.PROFILE_INFORMATION, CheckoutPhase.ORDER_SUMMARY)
            CheckoutPhase.ORDER_SUMMARY ->
                updatedCurrency == currentCurrency && updated.phase == CheckoutPhase.ORDER_SUMMARY
        }
    }

    private fun <T> decode(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        prefs?.getString(key, null)?.let { raw ->
            runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
        }

    private fun currentAuthenticatedOwner(): AuthenticatedSessionOwner? =
        com.ga.airdrop.core.auth.AuthTokenStore.snapshot().let { snapshot ->
            snapshot.sessionId?.takeIf { snapshot.token != null }
                ?.let { AuthenticatedSessionOwner(it, snapshot.accountId) }
        }
}
