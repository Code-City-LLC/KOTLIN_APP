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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Shipments hub is where a customer goes to check their packages. When
 * `GET /shipments/summary` failed, it told them they had none.
 *
 * `summary()` did have an `onFailure`, but it wrote to `ShipmentsUiState.error`,
 * which nothing renders — outside its own ViewModel that type appears exactly
 * once, as a parameter at `ShipmentsScreen.kt:318`, and that composable never
 * reads `.error`. (The `state.error` reads elsewhere in that file belong to
 * `QuickTrackUiState`, a different type, which is why a grep makes it look
 * handled.) So the counts stayed `null` and the tile rendered
 * `count?.toString() ?: "0"` — "Track Shipment 0 / Packages 0 / Payments 0 /
 * Orders 0", four false statements from one dropped request.
 *
 * That is word-for-word the lesson already written above `packagesFailed` /
 * `paymentsFailed` / `ordersFailed` in this very file. The summary was the
 * fourth request and was left out of that fix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShipmentsSummaryFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failed summary is flagged, so the tiles can stop claiming zero`() = runTest(dispatcher) {
        val viewModel = ShipmentsViewModel(
            repo = FailingSummaryRepo(),
            sessionBoundary = TestBoundary(),
        )
        advanceUntilIdle()

        assertTrue(
            "a failed /shipments/summary must be visible to the UI — writing it to " +
                "`error`, which no composable reads, is what produced \"Packages 0\"",
            viewModel.state.value.summaryFailed,
        )
    }

    @Test
    fun `a successful summary clears the flag`() = runTest(dispatcher) {
        val viewModel = ShipmentsViewModel(
            repo = WorkingRepo(),
            sessionBoundary = TestBoundary(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.summaryFailed)
        assertTrue(
            "the real counts must still come through",
            viewModel.state.value.summary.totalPackages == 7,
        )
    }

    private open class WorkingRepo : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(
            ShipmentsSummary(totalShipments = 3, totalPackages = 7, totalPayments = 2, totalOrders = 1),
        )
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    /** Only the summary fails — the shortlists succeed, so nothing else can mask it. */
    private class FailingSummaryRepo : WorkingRepo() {
        override suspend fun summary() =
            Result.failure<ShipmentsSummary>(IllegalStateException("network"))
    }

    private class TestBoundary : AuthenticatedSessionBoundary {
        private val owner = AuthenticatedSessionOwner(sessionId = "session-a", accountId = 1)
        private val flow = MutableStateFlow<AuthenticatedSessionOwner?>(owner)
        override val changes: Flow<AuthenticatedSessionOwner?> = flow
        override fun capture() = owner
        override fun isCurrent(owner: AuthenticatedSessionOwner) = owner.sessionId == this.owner.sessionId
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            action(); return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) = action()
        override fun requestOwner(owner: AuthenticatedSessionOwner) =
            AuthenticatedRequestOwner(
                session = owner,
                provenance = AuthTokenStore.RequestProvenance(revision = 1L, sessionId = owner.sessionId),
            )
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = true
    }
}
