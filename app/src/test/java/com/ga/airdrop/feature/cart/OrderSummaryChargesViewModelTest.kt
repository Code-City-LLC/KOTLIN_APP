package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderSummaryChargesViewModelTest {
    private val owner = AuthenticatedSessionOwner("charges-session", 77)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mixed checkout requests shipment details only and preserves identity`() =
        runTest(dispatcher) {
            val shipment = packageLine(71)
            val sale = saleLine(72)
            val original = flow(listOf(shipment, sale))
            val flowAccess = FakeFlowAccess(original)
            val repository = RecordingRepository { key, packageId, _ ->
                Result.success(snapshot(key, packageId, mapOf("Freight" to 8.0)))
            }
            val viewModel = viewModel(repository, flowAccess)

            viewModel.hydrate(listOf(shipment, sale))
            advanceUntilIdle()

            assertEquals(listOf(shipment.key to shipment.packageId), repository.calls.map {
                it.key to it.packageId
            })
            assertEquals(original.chargeCaptureIdentity(), flowAccess.flow.chargeCaptureIdentity())
            assertEquals(listOf(shipment.key), flowAccess.flow.shipmentChargeSnapshots.map {
                it.cartKey
            })
            assertEquals(listOf(shipment.key), viewModel.state.value.snapshots.map { it.cartKey })
            assertFalse(viewModel.state.value.loading)
            assertTrue(viewModel.state.value.failures.isEmpty())
        }

    @Test
    fun `sale-only checkout performs zero package-detail calls`() = runTest(dispatcher) {
        val sale = saleLine(81)
        val flowAccess = FakeFlowAccess(flow(listOf(sale)))
        val repository = RecordingRepository { _, _, _ -> error("must not be called") }
        val viewModel = viewModel(repository, flowAccess)

        viewModel.hydrate(listOf(sale))
        advanceUntilIdle()

        assertTrue(repository.calls.isEmpty())
        assertTrue(viewModel.state.value.canonicalFlowAvailable)
        assertTrue(viewModel.state.value.snapshots.isEmpty())
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `partial failure retries only the failed shipment`() = runTest(dispatcher) {
        val first = packageLine(91)
        val second = packageLine(92)
        var secondAttempts = 0
        val flowAccess = FakeFlowAccess(flow(listOf(first, second)))
        val repository = RecordingRepository { key, packageId, _ ->
            if (key == second.key && secondAttempts++ == 0) {
                Result.failure(IllegalStateException("temporary failure"))
            } else {
                Result.success(snapshot(key, packageId, mapOf("Insurance" to packageId.toDouble())))
            }
        }
        val viewModel = viewModel(repository, flowAccess)

        viewModel.hydrate(listOf(first, second))
        advanceUntilIdle()
        assertEquals(setOf(second.key), viewModel.state.value.failures.keys)
        assertEquals(listOf(first.key, second.key), repository.calls.map(RepositoryCall::key))

        viewModel.retryFailed()
        advanceUntilIdle()

        assertEquals(listOf(first.key, second.key, second.key), repository.calls.map(RepositoryCall::key))
        assertTrue(viewModel.state.value.failures.isEmpty())
        assertEquals(
            setOf(first.key, second.key),
            viewModel.state.value.snapshots.map(CheckoutShipmentChargeSnapshot::cartKey).toSet(),
        )
    }

    @Test
    fun `replacement flow rejects delayed response before persistence`() = runTest(dispatcher) {
        val shipment = packageLine(101)
        val delayed = CompletableDeferred<Result<CheckoutShipmentChargeSnapshot>>()
        val flowAccess = FakeFlowAccess(flow(listOf(shipment)))
        val repository = RecordingRepository { _, _, _ -> delayed.await() }
        val viewModel = viewModel(repository, flowAccess)

        viewModel.hydrate(listOf(shipment))
        runCurrent()
        assertEquals(1, repository.calls.size)
        flowAccess.flow = flowAccess.flow.copy(id = "replacement-flow")
        delayed.complete(
            Result.success(snapshot(shipment.key, requireNotNull(shipment.packageId), mapOf("Freight" to 4.0))),
        )
        advanceUntilIdle()

        assertEquals(0, flowAccess.recordCalls)
        assertNull(viewModel.state.value.identity)
        assertTrue(viewModel.state.value.snapshots.isEmpty())
    }

    @Test
    fun `replacement authenticated session cancels and clears delayed hydration`() =
        runTest(dispatcher) {
            val shipment = packageLine(111)
            val delayed = CompletableDeferred<Result<CheckoutShipmentChargeSnapshot>>()
            val boundary = FakeBoundary(owner)
            val flowAccess = FakeFlowAccess(flow(listOf(shipment)))
            val repository = RecordingRepository { _, _, _ -> delayed.await() }
            val viewModel = OrderSummaryChargesViewModel(repository, boundary, flowAccess)

            viewModel.hydrate(listOf(shipment))
            runCurrent()
            boundary.current.value = AuthenticatedSessionOwner("replacement-session", 88)
            runCurrent()
            delayed.complete(
                Result.success(snapshot(shipment.key, requireNotNull(shipment.packageId), emptyMap())),
            )
            advanceUntilIdle()

            assertEquals(0, flowAccess.recordCalls)
            assertNull(viewModel.state.value.identity)
            assertFalse(viewModel.state.value.loading)
        }

    private fun viewModel(
        repository: OrderSummaryChargesRepository,
        flowAccess: FakeFlowAccess,
    ) = OrderSummaryChargesViewModel(repository, FakeBoundary(owner), flowAccess)

    private fun flow(lines: List<CartStore.CartLine>) = CheckoutFlow(
        id = "captured-flow",
        ownerSessionId = owner.sessionId,
        ownerAccountId = owner.accountId,
        cartKeys = lines.map(CartStore.CartLine::key),
        packageIds = lines.map { requireNotNull(it.packageId) },
        isAuction = lines.any { it.resolvedKind == CartStore.CartLineKind.AUCTION },
        currency = "USD",
        phase = CheckoutPhase.ORDER_SUMMARY,
    )

    private fun packageLine(id: Int) = CartStore.CartLine(
        id = id,
        packageId = id,
        title = "Package $id",
        priceUsd = 10.0,
        kind = CartStore.CartLineKind.PACKAGE,
        statusCode = 7,
        serverConfirmed = true,
    )

    private fun saleLine(id: Int) = CartStore.CartLine(
        id = id,
        packageId = id + 1_000,
        title = "Sale $id",
        priceUsd = 20.0,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )

    private fun snapshot(
        key: CartStore.CartLineKey,
        packageId: Int,
        charges: Map<String, Double>,
    ) = CheckoutShipmentChargeSnapshot(
        cartKey = key,
        packageId = packageId,
        declaredValueUsd = 100.0,
        additionalCharges = charges,
        additionalChargesTotalUsd = charges.values.sum(),
        exchangeRateUsdToJmd = 160.0,
    )

    private data class RepositoryCall(
        val key: CartStore.CartLineKey,
        val packageId: Int,
        val provenance: AuthTokenStore.RequestProvenance,
    )

    private class RecordingRepository(
        private val response: suspend (
            CartStore.CartLineKey,
            Int,
            AuthTokenStore.RequestProvenance,
        ) -> Result<CheckoutShipmentChargeSnapshot>,
    ) : OrderSummaryChargesRepository {
        val calls = mutableListOf<RepositoryCall>()

        override suspend fun packageDetail(
            cartKey: CartStore.CartLineKey,
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<CheckoutShipmentChargeSnapshot> {
            calls += RepositoryCall(cartKey, packageId, expectedSession)
            return response(cartKey, packageId, expectedSession)
        }
    }

    private class FakeFlowAccess(initial: CheckoutFlow) : OrderSummaryChargeFlowAccess {
        var flow = initial
        var recordCalls = 0

        override fun current(owner: AuthenticatedSessionOwner): CheckoutFlow? =
            flow.takeIf { it.isOwnedBy(owner) }

        override fun record(
            owner: AuthenticatedSessionOwner,
            expectedIdentity: CheckoutChargeCaptureIdentity,
            snapshots: List<CheckoutShipmentChargeSnapshot>,
        ): CheckoutFlow? {
            if (!flow.isOwnedBy(owner) || flow.chargeCaptureIdentity() != expectedIdentity) return null
            recordCalls++
            val merged = flow.shipmentChargeSnapshots
                .associateBy(CheckoutShipmentChargeSnapshot::cartKey)
                .toMutableMap()
                .apply { snapshots.forEach { put(it.cartKey, it) } }
            flow = flow.copy(
                shipmentChargeSnapshots = flow.cartKeys.mapNotNull(merged::get),
            )
            return flow
        }
    }

    private class FakeBoundary(initial: AuthenticatedSessionOwner?) : AuthenticatedSessionBoundary {
        val current = MutableStateFlow(initial)
        override val changes = current

        override fun capture(): AuthenticatedSessionOwner? = current.value

        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = current.value == owner

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
                        revision = 17L,
                        sessionId = it.sessionId,
                        accountId = it.accountId,
                    ),
                )
            }

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && (owner.accountId == null || owner.accountId == accountId)
    }
}
