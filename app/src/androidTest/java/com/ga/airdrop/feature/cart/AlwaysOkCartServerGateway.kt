package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.auth.AuthTokenStore

/**
 * Shared instrumentation fake for [CartServerGateway] — every mutation
 * succeeds without touching the network.
 *
 * Why this exists: add-to-cart became server-backed in #138 ("complete full
 * checkout flow"). Parity tests written before that constructed their view
 * models without a `cartServer`, so they silently picked up the real
 * [DataCartServerGateway], hit the network on a test device, failed, and
 * showed "Cart update failed" instead of the success dialog they asserted.
 * Those failures went unnoticed for weeks because the connected gate was
 * path-filtered and never ran shipments instrumentation at all.
 *
 * Any parity test that drives an add-to-cart affordance must pass this (or an
 * equivalent fake) so it exercises the UI contract, not the network.
 */
class AlwaysOkCartServerGateway(
    private val lines: List<CartStore.CartLine> = emptyList(),
) : CartServerGateway {
    override suspend fun cart(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<List<CartStore.CartLine>> = Result.success(lines)

    override suspend fun addPackage(
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun removePackage(
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = Result.success(Unit)
}
