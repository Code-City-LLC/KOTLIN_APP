package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickTrackRegressionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an exact no-match remains visible in recent tracking lookups`() = runTest(dispatcher) {
        val viewModel = ShipmentsViewModel(
            repo = EmptyHubRepository(),
            packagesRepo = EmptyPackagesRepository(),
            sessionBoundary = TestBoundary(),
        )
        advanceUntilIdle()

        viewModel.submitQuickTrack("MISSING-TRACKING") { error("must not resolve") }
        advanceUntilIdle()

        val state = viewModel.quickTrack.value
        assertTrue(state.error.orEmpty().contains("No package found"))
        assertEquals(1, state.recents.size)
        assertEquals("MISSING-TRACKING", state.recents.single().code)
        assertEquals(null, state.recents.single().packageId)
    }

    private class EmptyHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private class EmptyPackagesRepository : ShipmentsPackagesRepository {
        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ) = Result.success(Paged(emptyList<ShipmentPackage>()))

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException("not used"))

        override suspend fun packageStatuses() = Result.success(emptyList<PackageStatusInfo>())

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException("not used"))

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException("not used"))
    }

    private class TestBoundary : AuthenticatedSessionBoundary {
        private val owner = AuthenticatedSessionOwner(sessionId = "session-a", accountId = 1)
        private val flow = MutableStateFlow<AuthenticatedSessionOwner?>(owner)

        override val changes: Flow<AuthenticatedSessionOwner?> = flow
        override fun capture() = owner
        override fun isCurrent(owner: AuthenticatedSessionOwner) = owner.sessionId == this.owner.sessionId
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            action()
            return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) = AuthenticatedRequestOwner(
            session = owner,
            provenance = AuthTokenStore.RequestProvenance(revision = 1L, sessionId = owner.sessionId),
        )
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = true
    }
}
