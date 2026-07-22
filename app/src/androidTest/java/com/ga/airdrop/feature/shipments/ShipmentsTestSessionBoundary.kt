package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.CartStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow

internal class ShipmentsTestSessionBoundary(
    initial: AuthenticatedSessionOwner? =
        DefaultAuthenticatedSessionBoundary.capture()
            ?: AuthenticatedSessionOwner("shipments-compose-test-session", 1),
) : AuthenticatedSessionBoundary {
    private val current = MutableStateFlow(initial)
    override val changes = current

    override fun capture(): AuthenticatedSessionOwner? = current.value

    override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean =
        current.value == owner

    override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
        if (!isCurrent(owner)) return false
        action()
        return true
    }

    override fun runWhileCurrent(
        owner: AuthenticatedSessionOwner,
        action: () -> Boolean,
    ): Boolean = isCurrent(owner) && action()

    override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? =
        owner.takeIf(::isCurrent)?.let {
            AuthenticatedRequestOwner(
                session = it,
                provenance = AuthTokenStore.RequestProvenance(
                    revision = 1,
                    sessionId = it.sessionId,
                    accountId = it.accountId,
                ),
            )
        }

    override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
        isCurrent(owner) && owner.accountId == accountId

    fun replace(owner: AuthenticatedSessionOwner?) {
        current.value = owner
    }
}

internal object ShipmentsTestCartServer : CartServerGateway {
    private val addCalls = AtomicInteger()
    val addCallCount: Int get() = addCalls.get()

    fun reset() {
        addCalls.set(0)
    }

    override suspend fun cart(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<List<CartStore.CartLine>> = Result.success(emptyList())

    override suspend fun addPackage(
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> {
        addCalls.incrementAndGet()
        return Result.success(Unit)
    }

    override suspend fun removePackage(
        packageId: Int,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = Result.success(Unit)
}
