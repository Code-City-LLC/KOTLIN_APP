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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PackageDetailsAuthorityViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val ownerA = AuthenticatedSessionOwner("package-owner-a", 11)
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
    fun `positive id fetches the canonical detail endpoint without alias search`() = runTest(dispatcher) {
        val repo = FakePackagesRepository(
            detailResults = ArrayDeque(
                listOf(Result.success(detail(id = 42, status = "7"))),
            ),
        )

        val viewModel = newViewModel("0042", repo)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repo.searchRequests)
        assertEquals(listOf("42"), repo.detailRequests)
        assertEquals(42, viewModel.state.value.authoritativePackageId)
        assertTrue(viewModel.state.value.hasAuthoritativeDetail)
        assertTrue(viewModel.state.value.showChargesAndCart)
        assertTrue(viewModel.state.value.canAddToCart)
    }

    @Test
    fun `exact tracking alias resolves a positive id before detail fetch`() = runTest(dispatcher) {
        val repo = FakePackagesRepository(
            searchRows = listOf(
                ShipmentPackage(id = 7, trackingCode = "TRACK-OTHER"),
                ShipmentPackage(id = 42, trackingCode = "TRACK-EXACT"),
            ),
            detailResults = ArrayDeque(
                listOf(Result.success(detail(id = 42, status = "18"))),
            ),
        )

        val viewModel = newViewModel(" track-exact ", repo)
        advanceUntilIdle()

        assertEquals(listOf("track-exact"), repo.searchRequests)
        assertEquals(listOf("42"), repo.detailRequests)
        assertEquals(42, viewModel.state.value.authoritativePackageId)
        assertTrue(viewModel.state.value.showChargesAndCart)
        assertFalse(viewModel.state.value.canAddToCart)
    }

    @Test
    fun `tracking alias scans pagination metadata through page two before detail fetch`() =
        runTest(dispatcher) {
            val alias = "TRACK-PAGE-TWO"
            val repo = FakePackagesRepository(
                searchPages = mapOf(
                    1 to Paged(
                        List(20) { index ->
                            ShipmentPackage(id = 1_000 + index, trackingCode = "OTHER-$index")
                        },
                        isLastPage = false,
                    ),
                    2 to Paged(
                        listOf(ShipmentPackage(id = 42, trackingCode = alias)),
                        isLastPage = true,
                    ),
                ),
                detailResults = ArrayDeque(
                    listOf(Result.success(detail(id = 42, status = "7"))),
                ),
            )

            val viewModel = newViewModel(alias, repo)
            advanceUntilIdle()

            assertEquals(listOf(1, 2), repo.pageRequests)
            assertEquals(listOf(50, 50), repo.perPageRequests)
            assertEquals(listOf("42"), repo.detailRequests)
            assertEquals(42, viewModel.state.value.authoritativePackageId)
        }

    @Test
    fun `ambiguous alias and fabricated zero row fail before detail endpoint`() = runTest(dispatcher) {
        val repo = FakePackagesRepository(
            searchRows = listOf(
                ShipmentPackage(id = 0, trackingCode = "TRACK-AMBIGUOUS"),
                ShipmentPackage(id = 42, trackingCode = "TRACK-AMBIGUOUS"),
                ShipmentPackage(id = 43, trackingCode = "TRACK-AMBIGUOUS"),
            ),
        )

        val viewModel = newViewModel("TRACK-AMBIGUOUS", repo)
        advanceUntilIdle()

        assertTrue(repo.detailRequests.isEmpty())
        assertNull(viewModel.state.value.detail)
        assertNull(viewModel.state.value.authoritativePackageId)
        assertTrue(viewModel.state.value.error.orEmpty().contains("No package found"))
    }

    @Test
    fun `failure exposes retry and actions only appear after authoritative retry success`() =
        runTest(dispatcher) {
            val repo = FakePackagesRepository(
                detailResults = ArrayDeque(
                    listOf(
                        Result.failure(IllegalStateException("Package service unavailable")),
                        Result.success(detail(id = 42, status = "7")),
                    ),
                ),
            )

            val viewModel = newViewModel("42", repo)
            assertTrue(viewModel.state.value.loading)
            advanceUntilIdle()

            assertEquals("Package service unavailable", viewModel.state.value.error)
            assertTrue(viewModel.state.value.canRetry)
            assertNull(viewModel.state.value.detail)
            assertFalse(viewModel.state.value.showChargesAndCart)
            assertFalse(viewModel.state.value.canAddToCart)

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf("42", "42"), repo.detailRequests)
            assertTrue(viewModel.state.value.hasAuthoritativeDetail)
            assertTrue(viewModel.state.value.canAddToCart)
        }

    @Test
    fun `mismatched detail response never becomes authoritative`() = runTest(dispatcher) {
        val repo = FakePackagesRepository(
            detailResults = ArrayDeque(
                listOf(Result.success(detail(id = 99, status = "7"))),
            ),
        )

        val viewModel = newViewModel("42", repo)
        advanceUntilIdle()

        assertNull(viewModel.state.value.detail)
        assertFalse(viewModel.state.value.hasAuthoritativeDetail)
        assertTrue(viewModel.state.value.error.orEmpty().contains("could not be verified"))
    }

    @Test
    fun `old session response cannot hydrate replacement account`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakePackagesRepository(
            detailResults = ArrayDeque(
                listOf(Result.success(detail(id = 42, status = "7"))),
            ),
            detailGate = gate,
        )
        val viewModel = newViewModel("42", repo)

        runCurrent()
        assertEquals(listOf("42"), repo.detailRequests)
        boundary.replace(AuthenticatedSessionOwner("package-owner-b", 22))
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.state.value.detail)
        assertNull(viewModel.state.value.authoritativePackageId)
        assertFalse(viewModel.state.value.canRetry)
        assertTrue(viewModel.state.value.error.orEmpty().contains("no longer available"))

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("The retired route must never refetch under account B", 1, repo.detailRequests.size)
    }

    private fun newViewModel(
        reference: String,
        repo: FakePackagesRepository,
    ) = PackageDetailsViewModel(
        packageId = reference,
        repo = repo,
        hubRepo = FakeHubRepository,
        cartServer = NoOpCartServer,
        sessionBoundary = boundary,
    )

    private fun detail(id: Int, status: String) = ShipmentPackageDetail(
        id = id,
        status = status,
        statusName = when (status) {
            "7" -> "Ready for Pickup"
            "18" -> "Paid and Ready for Pick Up"
            else -> null
        },
        trackingCode = "TRACK-$id",
    )

    private class FakePackagesRepository(
        private val searchRows: List<ShipmentPackage> = emptyList(),
        private val searchPages: Map<Int, Paged<ShipmentPackage>>? = null,
        private val detailResults: ArrayDeque<Result<ShipmentPackageDetail>> = ArrayDeque(),
        private val detailGate: CompletableDeferred<Unit>? = null,
    ) : ShipmentsPackagesRepository {
        val searchRequests = mutableListOf<String>()
        val pageRequests = mutableListOf<Int>()
        val perPageRequests = mutableListOf<Int>()
        val detailRequests = mutableListOf<String>()

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
            return Result.success(searchPages?.get(page) ?: Paged(searchRows))
        }

        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> {
            detailRequests += packageId
            detailGate?.await()
            return detailResults.removeFirstOrNull()
                ?: Result.failure(IllegalStateException("No queued detail response"))
        }

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
