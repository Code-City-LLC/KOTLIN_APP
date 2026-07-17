package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CartRequestProvenanceTest {

    private val ownerA = AuthenticatedSessionOwner("owner-a", 11)

    @Before
    fun reset() {
        CartStore.clear()
        PackageCartMutationCoordinator.resetForTests()
    }

    @After
    fun clean() {
        CartStore.clear()
        PackageCartMutationCoordinator.resetForTests()
    }

    private fun line(serverConfirmed: Boolean = false) = CartStore.CartLine(
        id = 71,
        packageId = 71,
        title = "Package 71",
        kind = CartStore.CartLineKind.PACKAGE,
        statusCode = 7,
        serverConfirmed = serverConfirmed,
    )

    @Test
    fun `unconfirmed existing add still dispatches exact PUT and becomes confirmed`() = runTest {
        val boundary = FakeBoundary(ownerA)
        val gateway = FakeGateway()
        val coordinator = PackageCartMutationCoordinator(gateway, boundary)
        val legacy = line(serverConfirmed = false)
        CartStore.onAuthenticatedSessionChanged(ownerA)
        CartStore.add(legacy)
        var succeeded = false

        coordinator.add(legacy, this, onSuccess = { succeeded = true }, onFailure = { error(it) })
        runCurrent()
        assertEquals(listOf(boundary.provenance), gateway.addOwners)
        gateway.add.complete(Result.success(Unit))
        advanceUntilIdle()

        assertTrue(succeeded)
        assertTrue(CartStore.items.value.single().serverConfirmed)
    }

    @Test
    fun `failed delete cleans only unconfirmed legacy row`() = runTest {
        val boundary = FakeBoundary(ownerA)
        val gateway = FakeGateway()
        val coordinator = PackageCartMutationCoordinator(gateway, boundary)
        val legacy = line(serverConfirmed = false)
        CartStore.onAuthenticatedSessionChanged(ownerA)
        CartStore.add(legacy)

        coordinator.remove(legacy, this, onFailure = { error(it) })
        runCurrent()
        gateway.remove.complete(Result.failure(IllegalStateException("404")))
        advanceUntilIdle()

        assertFalse(CartStore.contains(legacy.key))
    }

    @Test
    fun `failed delete retains confirmed server row and surfaces error`() = runTest {
        val boundary = FakeBoundary(ownerA)
        val gateway = FakeGateway()
        val coordinator = PackageCartMutationCoordinator(gateway, boundary)
        val confirmed = line(serverConfirmed = true)
        CartStore.onAuthenticatedSessionChanged(ownerA)
        CartStore.add(confirmed)
        var failure: String? = null

        coordinator.remove(confirmed, this, onFailure = { failure = it })
        runCurrent()
        gateway.remove.complete(Result.failure(IllegalStateException("server down")))
        advanceUntilIdle()

        assertTrue(CartStore.contains(confirmed.key))
        assertEquals("server down", failure)
    }

    @Test
    fun `owner replacement rejects delayed account A completion`() = runTest {
        val boundary = FakeBoundary(ownerA)
        val gateway = FakeGateway()
        val coordinator = PackageCartMutationCoordinator(gateway, boundary)
        val candidate = line()
        CartStore.onAuthenticatedSessionChanged(ownerA)

        coordinator.add(candidate, this, onFailure = { })
        runCurrent()
        boundary.current.value = AuthenticatedSessionOwner("owner-b", 22)
        gateway.add.complete(Result.success(Unit))
        advanceUntilIdle()

        assertFalse(CartStore.contains(candidate.key))
        assertEquals(listOf(boundary.provenanceFor(ownerA)), gateway.addOwners)
    }

    @Test
    fun `explicit non-seven state makes zero server calls`() = runTest {
        val boundary = FakeBoundary(ownerA)
        val gateway = FakeGateway()
        val coordinator = PackageCartMutationCoordinator(gateway, boundary)
        var failure: String? = null

        coordinator.add(line().copy(statusCode = 18), this, onFailure = { failure = it })
        advanceUntilIdle()

        assertTrue(gateway.addOwners.isEmpty())
        assertEquals("This package is unavailable for cart checkout.", failure)
    }

    @Test
    fun `cancelled caller releases mutation lock but checkout waits for authoritative cart`() = runTest {
        val authoritativeCart = CompletableDeferred<Result<List<CartStore.CartLine>>>()
        val boundary = FakeBoundary(ownerA)
        val gateway = FakeGateway(cartResult = authoritativeCart)
        val coordinator = PackageCartMutationCoordinator(gateway, boundary)
        val confirmed = line(serverConfirmed = true)
        CartStore.onAuthenticatedSessionChanged(ownerA)
        CartStore.add(confirmed)
        val callerJob = Job()
        val callerScope = CoroutineScope(coroutineContext + callerJob)

        coordinator.remove(confirmed, callerScope, onFailure = { error(it) })
        runCurrent()
        assertTrue(CartStore.hasPendingPackageMutations(setOf(confirmed.key)))
        assertTrue(PackageCartMutationCoordinator.inFlightOwners.containsKey(confirmed.key))

        callerJob.cancel()
        runCurrent()

        assertFalse(PackageCartMutationCoordinator.inFlightOwners.containsKey(confirmed.key))
        assertTrue(CartStore.hasPendingPackageMutations(setOf(confirmed.key)))
        assertNull(CheckoutFlow.start(ownerA, listOf(confirmed)))

        // The cancelled DELETE may or may not have reached the server. Only a
        // GET /cart begun after cancellation is allowed to reopen checkout.
        authoritativeCart.complete(Result.success(listOf(confirmed)))
        advanceUntilIdle()

        assertFalse(CartStore.hasPendingPackageMutations(setOf(confirmed.key)))
        assertNotNull(CheckoutFlow.start(ownerA, CartStore.items.value))
    }

    private class FakeGateway(
        private val cartResult: CompletableDeferred<Result<List<CartStore.CartLine>>> =
            CompletableDeferred(Result.success(emptyList())),
    ) : CartServerGateway {
        val add = CompletableDeferred<Result<Unit>>()
        val remove = CompletableDeferred<Result<Unit>>()
        val addOwners = mutableListOf<AuthTokenStore.RequestProvenance>()
        val removeOwners = mutableListOf<AuthTokenStore.RequestProvenance>()

        override suspend fun cart(
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<List<CartStore.CartLine>> = cartResult.await()

        override suspend fun addPackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<Unit> {
            addOwners += expectedSession
            return add.await()
        }

        override suspend fun removePackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<Unit> {
            removeOwners += expectedSession
            return remove.await()
        }
    }

    private class FakeBoundary(initial: AuthenticatedSessionOwner?) : AuthenticatedSessionBoundary {
        val current = MutableStateFlow(initial)
        override val changes = current
        val provenance: AuthTokenStore.RequestProvenance get() = provenanceFor(requireNotNull(current.value))

        fun provenanceFor(owner: AuthenticatedSessionOwner) = AuthTokenStore.RequestProvenance(
            revision = if (owner.sessionId == "owner-a") 101 else 202,
            sessionId = owner.sessionId,
            accountId = owner.accountId,
        )

        override fun capture(): AuthenticatedSessionOwner? = current.value
        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = current.value == owner
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false
            action()
            return true
        }

        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean =
            isCurrent(owner) && action()

        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? =
            owner.takeIf(::isCurrent)?.let { AuthenticatedRequestOwner(it, provenanceFor(it)) }

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && (owner.accountId == null || owner.accountId == accountId)
    }
}
