package com.ga.airdrop.feature.delivery

import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.repo.ActiveDeliveriesPage
import com.ga.airdrop.data.repo.JourneyFulfilment
import com.ga.airdrop.data.repo.JourneyStage
import com.ga.airdrop.data.repo.PackageJourney
import com.ga.airdrop.data.model.PackageTimelineEntry
import com.ga.airdrop.data.repo.DeliveryTrackingGateway
import com.ga.airdrop.data.repo.DeliveryTrackingResult
import com.ga.airdrop.data.repo.PackageJourneysPage
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
            journeys = { _, _ -> Result.success(page(emptyList())) },
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
            journeys = { page, perPage ->
                calls += "journeys:$page:$perPage"
                Result.success(page(listOf(active(41))))
            },
            detail = { packageId ->
                calls += "detail:$packageId"
                Result.success(DeliveryTrackingResult(packageId, tracking()))
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(listOf("journeys:1:50", "detail:41"), calls)
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
        val journeyCalls = mutableListOf<Int>()
        val detailCalls = mutableListOf<Int>()
        val gateway = gateway(
            journeys = { requestedPage, _ ->
                journeyCalls += requestedPage
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
        assertEquals(listOf(11, 22), viewModel.state.value.journeys.map(PackageJourney::packageId))
        assertEquals(listOf(1, 2), journeyCalls)
        assertTrue(detailCalls.isEmpty())

        viewModel.selectDelivery(22)
        advanceUntilIdle()
        assertEquals(DeliveryCenterContent.Detail, viewModel.state.value.content)
        assertEquals(listOf(22), detailCalls)
        assertTrue(viewModel.state.value.canReturnToList)

        assertTrue(viewModel.returnToList())
        assertEquals(DeliveryCenterContent.List, viewModel.state.value.content)
        assertEquals(listOf(11, 22), viewModel.state.value.journeys.map(PackageJourney::packageId))
    }

    @Test
    fun pageOneFailurePublishesErrorWithoutLeakingPartialListAndRetryRecovers() = runTest(dispatcher) {
        // ⚠️ THIS TEST USED TO FAIL ON *PAGE 2* AND EXPECT A BLANK ERROR SCREEN.
        // That contract is gone, deliberately.
        //
        // Discarding pages 1..K-1 because page K blipped threw away packages we
        // already held in order to render an error card. iOS fixed it on
        // 2026-08-10 (AirdropAPI.collectJourneys) and relayed it in ORC #95156;
        // aLaterPageFailingKeepsThePagesThatAlreadyLoaded now pins the new
        // behaviour.
        //
        // What this test still guards is the half that did NOT change and must
        // not be lost in the relaxation: a PAGE-1 failure has nothing to show,
        // so it is still a hard error, it must not leak a partial list, and
        // retry must recover.
        var attempt = 0
        val gateway = gateway(
            journeys = { _, _ ->
                if (attempt == 0) {
                    attempt += 1
                    Result.failure(IllegalStateException("Delivery service unavailable"))
                } else {
                    Result.success(page(emptyList()))
                }
            },
        )
        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.Error, viewModel.state.value.content)
        assertTrue(viewModel.state.value.journeys.isEmpty())
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
            journeys = { _, _ ->
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

    // ── The pickup gap ──────────────────────────────────────────────────────
    //
    // Two separate things kept a package held for collection off this screen,
    // and fixing either alone leaves it invisible:
    //
    //   1. the root list read /deliveries/active, which is backed by
    //      package_deliveries and structurally cannot contain a pickup; and
    //   2. the detail gate was `delivery != null`, so even a pickup that DID
    //      reach the list fell through to "we don't have delivery information".
    //
    // These three tests hold both.

    @Test
    fun aLonePickupOpensItsJourneyInsteadOfSayingThereIsNoDelivery() = runTest(dispatcher) {
        val detailCalls = mutableListOf<Int>()
        val gateway = gateway(
            journeys = { _, _ ->
                Result.success(page(listOf(active(41, JourneyFulfilment.Pickup))))
            },
            detail = { detailCalls += it; error("must not be asked for a pickup") },
            timeline = {
                Result.success(
                    listOf(
                        PackageTimelineEntry(
                            key = "received",
                            label = "Received at Warehouse",
                            state = "done",
                        ),
                        PackageTimelineEntry(
                            key = "ready_pickup",
                            label = "Ready for Pickup",
                            state = "current",
                        ),
                    ),
                )
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(
            "a pickup has a real warehouse journey; NoDelivery is the wrong answer",
            DeliveryCenterContent.Detail,
            viewModel.state.value.content,
        )
        assertEquals(
            "the last mile does not exist for a pickup, so asking for it is a bug",
            emptyList<Int>(),
            detailCalls,
        )
        assertEquals(
            listOf("Received at Warehouse", "Ready for Pickup"),
            viewModel.state.value.timeline.map { it.label },
        )
        assertNull("and there is no delivery to show", viewModel.state.value.delivery)
    }

    @Test
    fun aPickupAppearsInTheListAlongsideADelivery() = runTest(dispatcher) {
        val gateway = gateway(
            journeys = { _, _ ->
                Result.success(
                    page(
                        listOf(
                            active(44, JourneyFulfilment.Pickup),
                            active(22, JourneyFulfilment.Delivery),
                        ),
                    ),
                )
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.List, viewModel.state.value.content)
        assertEquals(
            "the pickup must be on Track, not filtered out of it",
            listOf(44, 22),
            viewModel.state.value.journeys.map(PackageJourney::packageId),
        )
    }

    @Test
    fun aLastMileFailureNoLongerBlanksAJourneyThatLoadedFine() = runTest(dispatcher) {
        // The rail comes from /packages/{id}/timeline; `delivery` is never
        // rendered as steps. Failing the whole screen on the last-mile read is
        // the same "one bad read blanks everything" shape fixed elsewhere.
        val gateway = gateway(
            journeys = { _, _ -> Result.success(page(listOf(active(41)))) },
            detail = { Result.failure(IllegalStateException("delivery service down")) },
            timeline = {
                Result.success(
                    listOf(PackageTimelineEntry(key = "received", label = "Received", state = "done")),
                )
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(
            "the journey loaded; showing 'Couldn't load' over it is the bug",
            DeliveryCenterContent.Detail,
            viewModel.state.value.content,
        )
        assertNull(viewModel.state.value.error)
        assertEquals(listOf("Received"), viewModel.state.value.timeline.map { it.label })
    }

    @Test
    fun bothReadsFailingIsStillAnHonestErrorScreen() = runTest(dispatcher) {
        // The relaxation above must not become blanket tolerance: when there is
        // genuinely nothing to render, say so rather than showing an empty rail.
        val gateway = gateway(
            journeys = { _, _ -> Result.success(page(listOf(active(41)))) },
            detail = { Result.failure(IllegalStateException("delivery service down")) },
            timeline = { Result.failure(IllegalStateException("Tracking is unavailable")) },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.Error, viewModel.state.value.content)
        assertEquals("Tracking is unavailable", viewModel.state.value.error)
    }

    @Test
    fun aPickupWithNothingRecordedYetStillShowsItsCardNotADeliveryDeadEnd() = runTest(dispatcher) {
        // Laravel ships an EXPLICIT empty journey (entries:[] / total:0) for a
        // package with no recorded events. For a pickup that read succeeds, is
        // empty, and has no last mile — and Track used to fall through to
        // NoDelivery: "Delivery details are not available. Package #41 does not
        // have a delivery journey to show yet." The word delivery, twice, over
        // a package sitting at the collection counter, named by internal id
        // instead of its ARD.
        //
        // We are not missing anything in that state: /packages/journeys already
        // returned a validated row for this package. iOS renders that card
        // unconditionally, so the platforms disagreed on identical bytes.
        val gateway = gateway(
            journeys = { _, _ ->
                Result.success(page(listOf(active(41, JourneyFulfilment.Pickup))))
            },
            detail = { error("must not be asked for a pickup") },
            timeline = { Result.success(emptyList()) },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(
            "a pickup we HAVE a journey for must render its card, not a " +
                "delivery-worded dead end",
            DeliveryCenterContent.Detail,
            viewModel.state.value.content,
        )
        assertEquals(41, viewModel.state.value.selectedJourney?.packageId)
        assertFalse(
            "the read succeeded — this is an empty journey, not a failed one",
            viewModel.state.value.timelineUnavailable,
        )
    }

    @Test
    fun aPackageWeHaveNoJourneyForStillReachesTheHonestNoDeliveryState() = runTest(dispatcher) {
        // The clause above must not swallow the genuine case. A deep link into
        // a package Track was handed no journey for, whose timeline is empty
        // and which has no delivery, still has nothing to show.
        val gateway = gateway(
            journeys = { _, _ -> Result.success(page(emptyList())) },
            detail = { Result.success(DeliveryTrackingResult(it, delivery = null)) },
            timeline = { Result.success(emptyList()) },
        )
        val viewModel = DeliveryCenterViewModel(
            initialPackageId = 77,
            gateway = gateway,
            sessionBoundary = boundary(),
        )
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.NoDelivery, viewModel.state.value.content)
    }

    @Test
    fun aLaterPageFailingKeepsThePagesThatAlreadyLoaded() = runTest(dispatcher) {
        // ⚠️ ONLY PAGE 1 IS FATAL.
        //
        // The loop used `.getOrThrow()` on every page inside runCatching, so a
        // blip on page 3 discarded pages 1-2 and blanked Track. Those packages
        // were already in hand; we dropped them to render an error card.
        //
        // iOS fixed exactly this on 2026-08-10 (AirdropAPI.collectJourneys,
        // `catch { break }`) and relayed it to this lane in ORC #95156. Kotlin
        // had the identical defect.
        val gateway = gateway(
            journeys = { requestedPage, _ ->
                when (requestedPage) {
                    1 -> Result.success(page(listOf(active(11)), hasNext = true, currentPage = 1))
                    2 -> Result.success(page(listOf(active(22)), hasNext = true, currentPage = 2))
                    else -> Result.failure(IllegalStateException("transient blip on page 3"))
                }
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertNull(
            "a later-page blip must not blank Track. Got: ${viewModel.state.value.error}",
            viewModel.state.value.error,
        )
        assertEquals(
            "pages 1-2 were already loaded and must render",
            listOf(11, 22),
            viewModel.state.value.journeys.map(PackageJourney::packageId),
        )
    }

    @Test
    fun aPageOneFailureIsStillFatal() = runTest(dispatcher) {
        // The relaxation above must not swallow the case where there is
        // genuinely nothing to show.
        val gateway = gateway(
            journeys = { _, _ -> Result.failure(IllegalStateException("Tracking is unavailable")) },
        )
        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertEquals(DeliveryCenterContent.Error, viewModel.state.value.content)
        assertEquals("Tracking is unavailable", viewModel.state.value.error)
    }

    @Test
    fun aHeavyAccountPastThePageCapRendersWhatLoadedInsteadOfErroring() = runTest(dispatcher) {
        // ⚠️ THE CEILING IS A STOP, NOT AN ERROR.
        //
        // `error(TRACK_UNAVAILABLE)` past MAX_ACTIVE_PAGES meant an account with
        // more than ~20,000 packages saw "Tracking information is unavailable" —
        // punished for having too many. Past the cap we render the most
        // relevant set the server already ordered. iOS bounds the same walk and
        // never throws at the ceiling (509607a).
        //
        // Every page claims another follows, so only the cap ends this walk.
        val gateway = gateway(
            journeys = { requestedPage, _ ->
                Result.success(
                    page(listOf(active(requestedPage)), hasNext = true, currentPage = requestedPage),
                )
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertNull(
            "hitting the page cap must not blank Track. Got: ${viewModel.state.value.error}",
            viewModel.state.value.error,
        )
        assertEquals(
            "the walk stops at the cap and keeps every page it read",
            400,
            viewModel.state.value.journeys.size,
        )
    }

    @Test
    fun aPackageAppearingOnTwoPagesKeepsTheFirstCopyInsteadOfKillingTrack() = runTest(dispatcher) {
        // ⚠️ OVERLAP IS NORMAL, NOT CORRUPTION.
        //
        // Offset pagination over live data legitimately repeats a row: if a
        // package changes status between the page-1 and page-2 requests the
        // server re-sorts and one package lands on both pages. This loop used
        // to error() on that, which threw away EVERY package and showed
        // "Tracking information is unavailable" — including the pickup this
        // whole branch exists to surface.
        //
        // iOS hit this on 2026-07-25 and fixed it the same way
        // (FigmaDeliveryCenterViewController.fetchAllJourneys): keep the first
        // occurrence. Android kept throwing, so the two platforms rendered
        // different screens from identical bytes.
        //
        // The exposure got much worse here: /deliveries/active rarely returned
        // more than one page, but /packages/journeys carries every trackable
        // package, so multi-page reads are now the common case.
        val gateway = gateway(
            journeys = { requestedPage, _ ->
                when (requestedPage) {
                    1 -> Result.success(
                        page(listOf(active(11), active(22)), hasNext = true, currentPage = 1),
                    )
                    // 22 repeats — the server re-sorted between requests.
                    2 -> Result.success(
                        page(listOf(active(22), active(33)), hasNext = false, currentPage = 2),
                    )
                    else -> error("Unexpected page $requestedPage")
                }
            },
        )

        val viewModel = DeliveryCenterViewModel(gateway = gateway, sessionBoundary = boundary())
        advanceUntilIdle()

        assertNull(
            "a legitimately repeated row must NOT blank Track. Got error: " +
                "${viewModel.state.value.error}",
            viewModel.state.value.error,
        )
        assertEquals(DeliveryCenterContent.List, viewModel.state.value.content)
        assertEquals(
            "keep the first occurrence, drop the duplicate, preserve server order",
            listOf(11, 22, 33),
            viewModel.state.value.journeys.map(PackageJourney::packageId),
        )
    }

    @Test
    fun replacedAccountCannotPublishDelayedOldAccountCompletion() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<Result<PackageJourneysPage>>()
        var activeCalls = 0
        var detailCalls = 0
        val boundary = boundary()
        val gateway = gateway(
            journeys = { _, _ ->
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
        assertTrue(viewModel.state.value.journeys.isEmpty())
        assertEquals(0, detailCalls)
    }

    private fun active(
        packageId: Int,
        fulfilment: JourneyFulfilment = JourneyFulfilment.Delivery,
    ) = PackageJourney(
        packageId = packageId,
        ardNumber = "AD-$packageId",
        description = "Package $packageId",
        valueUsd = null,
        weightLbs = null,
        shipper = null,
        status = 7,
        statusName = if (fulfilment == JourneyFulfilment.Pickup) {
            "Ready for Pickup"
        } else {
            "Preparing for Dispatch"
        },
        statusIcon = null,
        fulfilment = fulfilment,
        currentStage = "assigned",
        stages = listOf(JourneyStage("assigned", "Preparing for Dispatch", null, 7, "current", null)),
    )

    private fun page(
        journeys: List<PackageJourney>,
        hasNext: Boolean = false,
        currentPage: Int = 1,
    ) = PackageJourneysPage(journeys, currentPage, hasNext)

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
        // Track's root moved to /packages/journeys. Any call to the old
        // deliveries-only root from this screen is now a bug, so it blows up
        // rather than quietly answering.
        active: suspend (Int, Int) -> Result<ActiveDeliveriesPage> = { _, _ ->
            error("Track must not read /deliveries/active — it cannot see a pickup")
        },
        detail: suspend (Int) -> Result<DeliveryTrackingResult> = {
            error("Unexpected delivery-tracking call")
        },
        // Track renders Laravel's canonical journey. Default to an empty rail
        // so existing cases keep testing what they were written to test.
        timeline: suspend (Int) -> Result<List<PackageTimelineEntry>> = {
            Result.success(emptyList())
        },
        journeys: suspend (Int, Int) -> Result<PackageJourneysPage> = { _, _ ->
            error("Unexpected package-journeys call")
        },
    ) = object : DeliveryTrackingGateway {
        override suspend fun packageJourneys(page: Int, perPage: Int) = journeys(page, perPage)
        override suspend fun activeDeliveries(page: Int, perPage: Int) = active(page, perPage)
        override suspend fun deliveryTracking(packageId: Int) = detail(packageId)
        override suspend fun packageTimeline(packageId: Int) = timeline(packageId)
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
