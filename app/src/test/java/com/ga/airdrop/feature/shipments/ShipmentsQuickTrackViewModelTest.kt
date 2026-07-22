package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.feature.cart.CartServerGateway
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShipmentsQuickTrackViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val ownerA = AuthenticatedSessionOwner("quick-track-a", 1)
    private lateinit var boundary: FakeBoundary

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        boundary = FakeBoundary(ownerA)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exact alias hands off one resolved positive package id`() = runTest(dispatcher) {
        val packages = FakePackagesRepository(
            rows = listOf(
                ShipmentPackage(id = 42, trackingCode = "TRACK-EXACT"),
                ShipmentPackage(id = 77, trackingCode = "TRACK-OTHER"),
            ),
        )
        val viewModel = newViewModel(packages)
        advanceUntilIdle()
        val resolved = mutableListOf<Int>()

        viewModel.openQuickTrack()
        viewModel.updateQuickTrackCode("track-exact")
        viewModel.submitQuickTrack(onResolved = resolved::add)
        advanceUntilIdle()

        assertEquals(listOf(42), resolved)
        assertEquals(listOf("TRACK-EXACT"), packages.searchRequests)
        assertFalse(viewModel.quickTrack.value.visible)
        assertEquals(42, viewModel.quickTrack.value.recents.single().packageId)
    }

    @Test
    fun `positive numeric id hands off directly without search or shortlist dependency`() =
        runTest(dispatcher) {
            val packages = FakePackagesRepository(rows = emptyList())
            val viewModel = newViewModel(packages)
            advanceUntilIdle()
            val resolved = mutableListOf<Int>()

            viewModel.openQuickTrack()
            viewModel.submitQuickTrack("0042", resolved::add)

            assertEquals(listOf(42), resolved)
            assertTrue(packages.searchRequests.isEmpty())
            assertFalse(viewModel.quickTrack.value.visible)
            assertEquals(42, viewModel.quickTrack.value.recents.single().packageId)
        }

    @Test
    fun `zero rows and arbitrary embedded digits never fabricate handoff ids`() =
        runTest(dispatcher) {
            val packages = FakePackagesRepository(
                rows = listOf(
                    ShipmentPackage(id = 0, trackingCode = "TRACK-ZERO"),
                    ShipmentPackage(id = 42, trackingCode = "SOMETHING-ELSE"),
                ),
            )
            val viewModel = newViewModel(packages)
            advanceUntilIdle()
            val resolved = mutableListOf<Int>()

            viewModel.openQuickTrack()
            viewModel.submitQuickTrack("TRACK-ZERO", resolved::add)
            advanceUntilIdle()
            assertTrue(resolved.isEmpty())
            assertTrue(viewModel.quickTrack.value.error.orEmpty().contains("No package found"))

            viewModel.submitQuickTrack("UPS-42", resolved::add)
            advanceUntilIdle()
            assertTrue(resolved.isEmpty())
        }

    @Test
    fun `exact alias split across pages remains ambiguous and never hands off`() =
        runTest(dispatcher) {
            val alias = "TRACK-AMBIGUOUS"
            val packages = FakePackagesRepository(
                rows = emptyList(),
                pages = mapOf(
                    1 to Paged(
                        listOf(ShipmentPackage(id = 42, trackingCode = alias)) +
                            List(19) { index ->
                                ShipmentPackage(id = 2_000 + index, trackingCode = "OTHER-$index")
                            },
                        isLastPage = false,
                    ),
                    2 to Paged(
                        listOf(ShipmentPackage(id = 43, courierNumber = alias)),
                        isLastPage = true,
                    ),
                ),
            )
            val viewModel = newViewModel(packages)
            advanceUntilIdle()
            val resolved = mutableListOf<Int>()

            viewModel.openQuickTrack()
            viewModel.submitQuickTrack(alias, resolved::add)
            advanceUntilIdle()

            assertEquals(listOf(1, 2), packages.pageRequests)
            assertEquals(listOf(50, 50), packages.perPageRequests)
            assertTrue(resolved.isEmpty())
            assertTrue(viewModel.quickTrack.value.error.orEmpty().contains("No package found"))
        }

    @Test
    fun `quick track back cancels lookup and prevents late navigation`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val packages = FakePackagesRepository(
            rows = listOf(ShipmentPackage(id = 42, trackingCode = "TRACK-EXACT")),
            gate = gate,
        )
        val viewModel = newViewModel(packages)
        advanceUntilIdle()
        val resolved = mutableListOf<Int>()

        viewModel.openQuickTrack()
        viewModel.submitQuickTrack("TRACK-EXACT", resolved::add)
        runCurrent()
        viewModel.dismissQuickTrack()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(resolved.isEmpty())
        assertFalse(viewModel.quickTrack.value.visible)
        assertFalse(viewModel.quickTrack.value.loading)
    }

    @Test
    fun `replacement session cannot receive old quick track response`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val packages = FakePackagesRepository(
            rows = listOf(ShipmentPackage(id = 42, trackingCode = "TRACK-EXACT")),
            gate = gate,
        )
        val viewModel = newViewModel(packages)
        advanceUntilIdle()
        val resolved = mutableListOf<Int>()

        viewModel.openQuickTrack()
        viewModel.submitQuickTrack("TRACK-EXACT", resolved::add)
        runCurrent()
        boundary.replace(AuthenticatedSessionOwner("quick-track-b", 2))
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(resolved.isEmpty())
        assertEquals(QuickTrackUiState(), viewModel.quickTrack.value)
    }

    private fun newViewModel(packages: ShipmentsPackagesRepository) = ShipmentsViewModel(
        repo = FakeHubRepository,
        packagesRepo = packages,
        cartServer = NoOpCartServer,
        sessionBoundary = boundary,
    )

    private class FakePackagesRepository(
        private val rows: List<ShipmentPackage>,
        private val pages: Map<Int, Paged<ShipmentPackage>>? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : ShipmentsPackagesRepository {
        val searchRequests = mutableListOf<String>()
        val pageRequests = mutableListOf<Int>()
        val perPageRequests = mutableListOf<Int>()

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<Paged<ShipmentPackage>> {
            searchRequests += search.orEmpty()
            pageRequests += page
            perPageRequests += perPage
            gate?.await()
            return Result.success(pages?.get(page) ?: Paged(rows))
        }

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(IllegalStateException("Not used"))

        override suspend fun packageStatuses() = Result.success(emptyList<PackageStatusInfo>())

        override suspend fun uploadInvoices(
            packageId: String,
            files: List<InvoiceUploadFile>,
        ) = Result.success(Unit)

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.success(Unit)
    }

    private object FakeHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private object NoOpCartServer : CartServerGateway {
        override suspend fun cart(expectedSession: AuthTokenStore.RequestProvenance) =
            Result.success(emptyList<com.ga.airdrop.feature.cart.CartStore.CartLine>())

        override suspend fun addPackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.success(Unit)

        override suspend fun removePackage(
            packageId: Int,
            expectedSession: AuthTokenStore.RequestProvenance,
        ) = Result.success(Unit)
    }

    private class FakeBoundary(initial: AuthenticatedSessionOwner?) :
        AuthenticatedSessionBoundary {
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
}
