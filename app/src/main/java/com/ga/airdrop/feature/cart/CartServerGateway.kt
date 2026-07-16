package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CartPackage
import com.ga.airdrop.data.repo.PackagesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One owner for the existing Laravel `/cart` and package-cart endpoints. */
interface CartServerGateway {
    suspend fun cart(expectedSession: AuthTokenStore.RequestProvenance): Result<List<CartStore.CartLine>>
    suspend fun addPackage(packageId: Int, expectedSession: AuthTokenStore.RequestProvenance): Result<Unit>
    suspend fun removePackage(packageId: Int, expectedSession: AuthTokenStore.RequestProvenance): Result<Unit>
}

class DataCartServerGateway(
    private val packages: PackagesRepository = PackagesRepository(ApiClient.service),
) : CartServerGateway {
    override suspend fun cart(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<List<CartStore.CartLine>> =
        packages.cart(expectedSession).mapCatching { snapshot ->
            val ids = snapshot.packages.map(CartPackage::id)
            require(snapshot.packageCount == null || snapshot.packageCount == snapshot.packages.size) {
                "Cart response package count did not match its package list"
            }
            require(ids.all { it != null && it > 0 } && ids.distinct().size == ids.size) {
                "Cart response contained an invalid or duplicate package ID"
            }
            snapshot.packages.map(CartPackage::toCartLine)
        }

    override suspend fun addPackage(
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> =
        packages.addPackageToCart(packageId, expectedSession).map { }

    override suspend fun removePackage(
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> =
        packages.removePackageFromCart(packageId, expectedSession).map { }
}

internal fun CartPackage.toCartLine(): CartStore.CartLine = CartStore.CartLine(
    id = requireNotNull(id).also { require(it > 0) },
    packageId = id,
    title = description?.takeIf(String::isNotBlank)
        ?: trackingCode?.takeIf(String::isNotBlank)
        ?: "Package #${requireNotNull(id)}",
    qty = 1,
    priceUsd = totalCharges ?: additionalChargesTotal ?: additionalCharges ?: shippingCost ?: 0.0,
    kind = CartStore.CartLineKind.PACKAGE,
    weightKg = weightKg?.takeIf { it > 0.0 }
        ?: (weightLbs ?: weight)?.takeIf { it > 0.0 }?.times(0.45359237),
    status = statusName,
    statusCode = status,
    serverConfirmed = true,
    isAuction = false,
)

/**
 * The one server-backed package-cart mutation rail. Cart, Packages,
 * Shipments, and Package Details supply only UI callbacks; ownership,
 * provenance, generations, legacy cleanup, and stale-response rejection live
 * here so those entry points cannot drift.
 */
internal class PackageCartMutationCoordinator(
    private val cartServer: CartServerGateway,
    private val sessionBoundary: AuthenticatedSessionBoundary,
) {
    private enum class Intent { ADD, REMOVE, TOGGLE }

    internal companion object {
        val inFlightLock = Any()
        val inFlightOwners = mutableMapOf<CartStore.CartLineKey, AuthenticatedSessionOwner>()

        fun resetForTests() = synchronized(inFlightLock) { inFlightOwners.clear() }
    }

    fun toggle(
        line: CartStore.CartLine,
        scope: CoroutineScope,
        onStarted: () -> Unit = {},
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit,
    ) = mutate(line, Intent.TOGGLE, scope, onStarted, onSuccess, onFailure)

    fun add(
        line: CartStore.CartLine,
        scope: CoroutineScope,
        onStarted: () -> Unit = {},
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit,
    ) = mutate(line, Intent.ADD, scope, onStarted, onSuccess, onFailure)

    fun remove(
        line: CartStore.CartLine,
        scope: CoroutineScope,
        onStarted: () -> Unit = {},
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit,
    ) = mutate(line, Intent.REMOVE, scope, onStarted, onSuccess, onFailure)

    private fun mutate(
        line: CartStore.CartLine,
        intent: Intent,
        scope: CoroutineScope,
        onStarted: () -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val migrated = line.migrated()
        if (migrated.resolvedKind != CartStore.CartLineKind.PACKAGE || migrated.id <= 0) {
            onFailure("This package has no valid server identity.")
            return
        }
        val owner = sessionBoundary.capture() ?: run {
            onFailure("Log in before changing server cart items.")
            return
        }
        val requestOwner = sessionBoundary.requestOwner(owner) ?: run {
            onFailure("Your session changed. Log in again before updating the cart.")
            return
        }
        var mutation: CartStore.PackageMutation? = null
        var adding = false
        var packageId = 0
        var sourceLine = migrated
        var completedSynchronously = false
        val prepared = sessionBoundary.runWhileCurrent(owner) {
            CartStore.onAuthenticatedSessionChanged(owner)
            val existing = CartStore.items.value.firstOrNull { it.key == migrated.key }
            adding = when (intent) {
                Intent.ADD -> true
                Intent.REMOVE -> false
                Intent.TOGGLE -> existing == null
            }
            if (intent == Intent.ADD && existing?.serverConfirmed == true &&
                existing.isCheckoutEligible()
            ) {
                completedSynchronously = true
                onSuccess()
                return@runWhileCurrent true
            }
            if (intent == Intent.REMOVE && existing == null) {
                completedSynchronously = true
                onSuccess()
                return@runWhileCurrent true
            }
            sourceLine = existing ?: migrated
            packageId = if (adding) {
                (sourceLine.packageId ?: migrated.packageId)?.takeIf { it > 0 }
            } else {
                (sourceLine.packageId ?: migrated.packageId ?: sourceLine.id).takeIf { it > 0 }
            } ?: 0
            if (packageId <= 0) {
                completedSynchronously = true
                onFailure("This package has no valid server identity.")
                return@runWhileCurrent true
            }
            if (adding && !isPackageCartEligibleStatus(sourceLine.statusCode)) {
                completedSynchronously = true
                onFailure("This package is unavailable for cart checkout.")
                return@runWhileCurrent true
            }
            val accepted = synchronized(inFlightLock) {
                if (inFlightOwners[sourceLine.key] == owner) false
                else {
                    // A replacement owner can supersede A immediately. The
                    // CartStore generation makes A's later response harmless.
                    inFlightOwners[sourceLine.key] = owner
                    true
                }
            }
            if (!accepted) {
                completedSynchronously = true
                return@runWhileCurrent true
            }
            mutation = CartStore.beginPackageMutation(sourceLine, adding)
            if (mutation == null) {
                synchronized(inFlightLock) {
                    if (inFlightOwners[sourceLine.key] == owner) inFlightOwners.remove(sourceLine.key)
                }
                completedSynchronously = true
                onFailure("This package is unavailable for cart updates.")
            } else {
                onStarted()
            }
            true
        }
        if (!prepared) {
            onFailure("Your session changed. Log in again before updating the cart.")
            return
        }
        if (completedSynchronously) return
        val exactMutation = mutation ?: return

        scope.launch {
            var inFlightReleased = false
            fun releaseInFlight() {
                if (inFlightReleased) return
                synchronized(inFlightLock) {
                    if (inFlightOwners[sourceLine.key] == owner) {
                        inFlightOwners.remove(sourceLine.key)
                    }
                }
                inFlightReleased = true
            }

            try {
                val result = if (adding) {
                    cartServer.addPackage(packageId, requestOwner.provenance)
                } else {
                    cartServer.removePackage(packageId, requestOwner.provenance)
                }
                val applied = sessionBoundary.runWhileCurrent(owner) {
                    if (result.isSuccess) {
                        if (!CartStore.finishPackageMutation(exactMutation, succeeded = true)) {
                            return@runWhileCurrent false
                        }
                        onSuccess()
                    } else if (!adding && !sourceLine.serverConfirmed) {
                        // A pre-server legacy row must remain removable even when
                        // its DELETE is a 404. Confirmed rows stay on any failure.
                        if (!CartStore.finishPackageMutation(exactMutation, succeeded = true)) {
                            return@runWhileCurrent false
                        }
                        onSuccess()
                    } else {
                        CartStore.finishPackageMutation(exactMutation, succeeded = false)
                        onFailure(result.exceptionOrNull()?.message ?: "Unable to update your cart. Please try again.")
                    }
                    true
                }
                if (!applied) CartStore.finishPackageMutation(exactMutation, succeeded = false)
            } catch (cancelled: CancellationException) {
                // The request may have reached Laravel before the caller's
                // ViewModel scope was cancelled. Release the UI/in-flight lock
                // immediately, but keep checkout held on this exact key until
                // an authoritative GET begun after this cancellation lands.
                var recoverySnapshot: CartStore.ServerCartSnapshot? = null
                sessionBoundary.runWhileCurrent(owner) {
                    if (CartStore.markPackageMutationOutcomeUnknown(exactMutation)) {
                        recoverySnapshot = CartStore.beginServerCartSnapshot()
                    }
                    true
                }
                releaseInFlight()
                val snapshot = recoverySnapshot
                if (snapshot != null) {
                    withContext(NonCancellable) {
                        val authoritative = try {
                            cartServer.cart(requestOwner.provenance)
                        } catch (_: Throwable) {
                            Result.failure(IllegalStateException("Cart reconciliation failed"))
                        }
                        authoritative.onSuccess { lines ->
                            sessionBoundary.runWhileCurrent(owner) {
                                CartStore.reconcileServerPackages(lines, snapshot)
                                true
                            }
                        }
                    }
                }
                throw cancelled
            } finally {
                releaseInFlight()
            }
        }
    }
}
