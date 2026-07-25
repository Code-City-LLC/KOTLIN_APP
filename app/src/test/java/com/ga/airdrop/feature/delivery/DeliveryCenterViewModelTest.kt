package com.ga.airdrop.feature.delivery

import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.repo.ActiveDeliveriesPage
import com.ga.airdrop.data.repo.ActiveDelivery
import com.ga.airdrop.data.repo.DeliveryTrackingGateway
import com.ga.airdrop.data.repo.DeliveryTrackingResult
import com.ga.airdrop.data.repo.TrackedDelivery
import com.ga.airdrop.data.repo.TrackedDeliveryStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryCenterViewModelTest {

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
    fun zeroActiveDeliveriesPublishesHonestEmptyState() = runTest(dispatcher) {
        val gateway = gateway(
            active = { _, _ -> Result.success(page(emptyList())) },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.Empty, viewModel.state.value.content)
        assertTrue(viewModel.state.value.loadedOnce)
        assertNull(viewModel.state.value.selectedPackageId)
    }

    @Test
    fun oneActiveDeliveryOpensItsServerDetailWithoutAListShell() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val gateway = gateway(
            active = { page, perPage ->
                calls += "active:$page:$perPage"
                Result.success(page(listOf(active(41))))
            },
            detail = { packageId ->
                calls += "detail:$packageId"
                Result.success(DeliveryTrackingResult(packageId, tracking()))
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(listOf("active:1:50", "detail:41"), calls)
        assertEquals(DeliveryCenterContent.Detail, viewModel.state.value.content)
        assertEquals(41, viewModel.state.value.selectedPackageId)
        assertEquals(
            listOf("Accepted by dispatch", "Vehicle departed", "Handed to customer"),
            viewModel.state.value.delivery?.stages?.map(TrackedDeliveryStage::label),
        )
        assertFalse(viewModel.state.value.canReturnToList)
    }

    @Test
    fun multiplePaginatedDeliveriesListThenDrillAndBackToSameList() = runTest(dispatcher) {
        val activeCalls = mutableListOf<Int>()
        val detailCalls = mutableListOf<Int>()
        val gateway = gateway(
            active = { requestedPage, _ ->
                activeCalls += requestedPage
                when (requestedPage) {
                    1 -> Result.success(page(listOf(active(11)), hasNext = true, currentPage = 1))
                    2 -> Result.success(page(listOf(active(22)), hasNext = false, currentPage = 2))
                    else -> error("Unexpected page $requestedPage")
                }
            },
            detail = { packageId ->
                detailCalls += packageId
                Result.success(DeliveryTrackingResult(packageId, tracking()))
            },
        )
        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.List, viewModel.state.value.content)
        assertEquals(listOf(11, 22), viewModel.state.value.activeDeliveries.map(ActiveDelivery::packageId))
        assertEquals(listOf(1, 2), activeCalls)
        assertTrue(detailCalls.isEmpty())

        viewModel.selectDelivery(22)
        advanceUntilIdle()
        assertEquals(DeliveryCenterContent.Detail, viewModel.state.value.content)
        assertEquals(listOf(22), detailCalls)
        assertTrue(viewModel.state.value.canReturnToList)

        assertTrue(viewModel.returnToList())
        assertEquals(DeliveryCenterContent.List, viewModel.state.value.content)
        assertEquals(listOf(11, 22), viewModel.state.value.activeDeliveries.map(ActiveDelivery::packageId))
    }

    @Test
    fun pageFailurePublishesErrorWithoutLeakingPartialListAndRetryRecovers() = runTest(dispatcher) {
        var attempt = 0
        val gateway = gateway(
            active = { requestedPage, _ ->
                when {
                    attempt == 0 && requestedPage == 1 ->
                        Result.success(page(listOf(active(11)), hasNext = true))
                    attempt == 0 -> {
                        attempt += 1
                        Result.failure(IllegalStateException("Delivery service unavailable"))
                    }
                    else -> Result.success(page(emptyList()))
                }
            },
        )
        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.Error, viewModel.state.value.content)
        assertTrue(viewModel.state.value.activeDeliveries.isEmpty())
        assertEquals("Delivery service unavailable", viewModel.state.value.error)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(DeliveryCenterContent.Empty, viewModel.state.value.content)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun directPackageIdSkipsListAndNullDeliveryRemainsTruthful() = runTest(dispatcher) {
        var activeCalls = 0
        val detailCalls = mutableListOf<Int>()
        val gateway = gateway(
            active = { _, _ ->
                activeCalls += 1
                Result.success(page(emptyList()))
            },
            detail = { packageId ->
                detailCalls += packageId
                Result.success(DeliveryTrackingResult(packageId, delivery = null))
            },
        )
        val viewModel = DeliveryCenterViewModel(
            initialPackageId = 77,
            gateway = gateway,
            sessionBoundary = boundary(),
        )
        advanceUntilIdle()

        assertEquals(0, activeCalls)
        assertEquals(listOf(77), detailCalls)
        assertEquals(DeliveryCenterContent.NoDelivery, viewModel.state.value.content)
        assertEquals(77, viewModel.state.value.selectedPackageId)
    }

    @Test
    fun replacedAccountCannotPublishDelayedOldAccountCompletion() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<Result<ActiveDeliveriesPage>>()
        var activeCalls = 0
        var detailCalls = 0
        val boundary = boundary()
        val gateway = gateway(
            active = { _, _ ->
                activeCalls += 1
                if (activeCalls == 1) {
                    withContext(NonCancellable) { oldCompletion.await() }
                } else {
                    Result.success(page(emptyList()))
                }
            },
            detail = {
                detailCalls += 1
                Result.success(DeliveryTrackingResult(it, tracking()))
            },
        )
        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary)
        advanceUntilIdle()

        boundary.replace(AuthenticatedSessionOwner("session-b", accountId = 2))
        advanceUntilIdle()
        assertEquals(DeliveryCenterContent.Empty, viewModel.state.value.content)

        oldCompletion.complete(Result.success(page(listOf(active(99)))))
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.Empty, viewModel.state.value.content)
        assertTrue(viewModel.state.value.activeDeliveries.isEmpty())
        assertEquals(0, detailCalls)
    }

    private fun active(packageId: Int) = ActiveDelivery(
        packageId = packageId,
        trackingCode = "AD-$packageId",
        description = "Package $packageId",
        status = "assigned",
        scheduledDate = null,
        currentStageKey = "assigned",
        updatedAt = null,
    )

    private fun page(
        deliveries: List<ActiveDelivery>,
        hasNext: Boolean = false,
        currentPage: Int = 1,
    ) = ActiveDeliveriesPage(deliveries, currentPage, hasNext)

    private fun tracking() = TrackedDelivery(
        status = "out_for_delivery",
        scheduledDate = null,
        assignedAt = "2026-07-22T12:00:00+00:00",
        outForDeliveryAt = "2026-07-22T13:00:00+00:00",
        deliveredAt = null,
        stages = listOf(
            TrackedDeliveryStage("accepted", "Accepted by dispatch", "done", null),
            TrackedDeliveryStage("road", "Vehicle departed", "current", null),
            TrackedDeliveryStage("handed_over", "Handed to customer", "pending", null),
        ),
    )

    private fun gateway(
        active: suspend (Int, Int) -> Result<ActiveDeliveriesPage> = { _, _ ->
            error("Unexpected active-deliveries call")
        },
        detail: suspend (Int) -> Result<DeliveryTrackingResult> = {
            error("Unexpected delivery-tracking call")
        },
    ) = object : DeliveryTrackingGateway {
        override suspend fun activeDeliveries(page: Int, perPage: Int) = active(page, perPage)
        override suspend fun deliveryTracking(packageId: Int) = detail(packageId)
    }

    private fun boundary() = TestSessionBoundary(
        AuthenticatedSessionOwner("session-a", accountId = 1),
    )

    private class TestSessionBoundary(
        private var owner: AuthenticatedSessionOwner?,
    ) : AuthenticatedSessionBoundary {
        private val ownerFlow = MutableStateFlow(owner)
        override val changes: Flow<AuthenticatedSessionOwner?> = ownerFlow

        fun replace(next: AuthenticatedSessionOwner?) {
            owner = next
            ownerFlow.value = next
        }

        override fun capture(): AuthenticatedSessionOwner? = owner

        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean =
            this.owner?.sessionId == owner.sessionId

        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false
            action()
            return true
        }

        override fun runWhileCurrent(
            owner: AuthenticatedSessionOwner,
            action: () -> Boolean,
        ): Boolean = isCurrent(owner) && action() && isCurrent(owner)

        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? = null

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && this.owner?.accountId == accountId
    }
}
