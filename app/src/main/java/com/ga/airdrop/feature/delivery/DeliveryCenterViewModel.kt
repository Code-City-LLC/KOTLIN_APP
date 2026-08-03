package com.ga.airdrop.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.repo.DeliveryTrackingGateway
import com.ga.airdrop.data.repo.DeliveryTrackingRepository
import com.ga.airdrop.data.repo.JourneyFulfilment
import com.ga.airdrop.data.repo.PackageJourney
import com.ga.airdrop.data.repo.TrackedDelivery
import com.ga.airdrop.data.model.PackageTimelineEntry
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeliveryCenterContent {
    Loading,
    Error,
    Empty,
    List,
    Detail,
    NoDelivery,
}

data class DeliveryCenterUiState(
    /**
     * Track's root list.
     *
     * ⚠️ THIS USED TO BE `List<ActiveDelivery>` FROM `/deliveries/active`, AND
     * THAT IS WHY A PICKUP WAS INVISIBLE.
     *
     * `/deliveries/active` reads the `package_deliveries` table. A package held
     * for collection has no row there, so no amount of client work could put it
     * on this screen — a customer whose package was ready to collect opened
     * Track and read "No shipments to track". `/packages/journeys` is the
     * superset: pickup and delivery alike, each with a server-composed rail.
     */
    val journeys: List<PackageJourney> = emptyList(),
    val selectedPackageId: Int? = null,
    /**
     * The last mile, when there is one. Null for a pickup — and null is no
     * longer a dead end: see [content].
     */
    val delivery: TrackedDelivery? = null,
    /**
     * Laravel's canonical journey for the selected package — the full rail,
     * already ordered, labelled, iconed and capped to one pending step. Kotlin
     * used to build this itself from raw history plus the last mile; the server
     * owns it now.
     */
    val timeline: List<PackageTimelineEntry> = emptyList(),
    /**
     * True when the canonical journey could NOT be read, while the last-mile
     * delivery data was fetched fine.
     *
     * ⚠️ I ORIGINALLY DOCUMENTED THIS AS "degrades to the last mile". THAT WAS
     * WRONG and the comment is corrected rather than deleted, because the wrong
     * model is the interesting part.
     *
     * The last mile does NOT survive a timeline failure: `dispatch`,
     * `out_for_delivery` and `delivered` are timeline icon keys, and
     * TrackJourney's KDoc states the endpoint owns "last-mile composition".
     * One call supplies the whole rail, so when it fails the rail is EMPTY —
     * not shortened. Keeping the screen (the Your Delivery card, tracking
     * number, contact action) is still right and still the reason not to blank
     * it; but there is no partial journey to show.
     *
     * The flag therefore means "we could not read ANY of this package's
     * journey", and the UI must say exactly that. Claiming only the earlier
     * steps are missing invites the customer to read the empty rail as
     * "nothing else has happened", which is the same false-shorter-journey this
     * branch exists to remove.
     */
    val timelineUnavailable: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
    internal val retryPackageId: Int? = null,
) {
    val content: DeliveryCenterContent
        get() = when {
            loading -> DeliveryCenterContent.Loading
            error != null -> DeliveryCenterContent.Error
            // ⚠️ `delivery != null` USED TO BE THE GATE HERE, and it is the
            // second half of the pickup bug. Even once a pickup row reached
            // this screen, opening it fell through to NoDelivery — "we don't
            // have delivery information" — for a package that has a perfectly
            // good warehouse journey the server is happy to describe.
            //
            // The rail is composed by /packages/{id}/timeline, not by the
            // delivery record; `delivery` was never rendered as steps, only
            // used as a non-null gate (see DeliveryDetail). So the timeline is
            // what decides whether there is a journey to show.
            hasJourneyToShow -> DeliveryCenterContent.Detail
            // Still reachable, and still honest: a package was opened, the read
            // did NOT fail, and the server's answer was genuinely "nothing has
            // happened to this package yet". Inventing progress for it is the
            // thing this state exists to refuse.
            selectedPackageId != null -> DeliveryCenterContent.NoDelivery
            journeys.size > 1 -> DeliveryCenterContent.List
            loadedOnce -> DeliveryCenterContent.Empty
            else -> DeliveryCenterContent.Loading
        }

    /**
     * A timeline read that FAILED counts as something to show, because the
     * screen has to say so.
     *
     * ⚠️ `selectedJourney != null` IS LOAD-BEARING, and leaving it out put a
     * PICKUP back on a delivery-worded dead end inside the very branch that
     * exists to stop that.
     *
     * Laravel ships an explicit EMPTY journey (`entries:[] / total:0`) for a
     * package with nothing recorded yet. For a pickup that read succeeds, is
     * empty, and has no last mile — so without this clause the gate was false
     * and Track fell through to [DeliveryCenterContent.NoDelivery]: "Delivery
     * details are not available. Package #41 does not have a delivery journey
     * to show yet." The word delivery, twice, over a package sitting at the
     * collection counter, named by internal id instead of its ARD.
     *
     * We are NOT missing information in that state. `/packages/journeys`
     * already returned a validated row for this package — id, ARD, description,
     * status name, and a required non-empty stages rail. iOS renders that card
     * unconditionally (FigmaDeliveryCenterViewController.renderTimeline runs for
     * every single-journey case, empty entries included), so the two platforms
     * disagreed on identical bytes.
     *
     * NoDelivery stays reachable and stays honest — for a package we were
     * handed no journey for at all, e.g. a deep link into something Track
     * cannot describe.
     */
    private val hasJourneyToShow: Boolean
        get() = selectedPackageId != null &&
            (
                timeline.isNotEmpty() || timelineUnavailable ||
                    delivery != null || selectedJourney != null
                )

    val selectedJourney: PackageJourney?
        get() = journeys.firstOrNull { it.packageId == selectedPackageId }

    val canReturnToList: Boolean
        get() = journeys.size > 1 && selectedPackageId != null
}

/**
 * Canonical 0/1/many Delivery Center state machine.
 *
 * 0 -> honest empty state; 1 -> detail; 2+ -> list then package-id detail.
 * Every stage displayed by the screen is the ordered server projection.
 */
class DeliveryCenterViewModel(
    private val initialPackageId: Int? = null,
    private val gateway: DeliveryTrackingGateway = DeliveryTrackingRepository(ApiClient.service),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryCenterUiState())
    val state: StateFlow<DeliveryCenterUiState> = _state

    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var loadJob: Job? = null
    private val loadEpoch = AtomicInteger(0)

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionJobs.replaceSession()
                loadJob = null
                loadEpoch.incrementAndGet()
                sessionOwner = changed
                _state.value = DeliveryCenterUiState()
                if (changed != null) loadInitial()
            }
        }
        loadInitial()
    }

    fun refresh() {
        val selected = _state.value.selectedPackageId
        if (selected != null) {
            loadDetail(selected, preserveList = _state.value.journeys, isRefresh = true)
        } else {
            loadJourneys(isRefresh = _state.value.loadedOnce)
        }
    }

    fun retry() {
        val packageId = _state.value.retryPackageId
        if (packageId != null) {
            loadDetail(packageId, preserveList = _state.value.journeys, isRefresh = false)
        } else {
            loadJourneys(isRefresh = false)
        }
    }

    fun selectDelivery(packageId: Int) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        if (current.journeys.none { it.packageId == packageId }) return
        loadDetail(packageId, preserveList = current.journeys, isRefresh = false)
    }

    /** Returns true when Back was consumed by detail -> multi-delivery list. */
    fun returnToList(): Boolean {
        val current = _state.value
        if (!current.canReturnToList) return false
        loadJob?.cancel()
        loadEpoch.incrementAndGet()
        _state.update {
            it.copy(
                selectedPackageId = null,
                delivery = null,
                loading = false,
                refreshing = false,
                error = null,
                retryPackageId = null,
            )
        }
        return true
    }

    private fun loadInitial() {
        val directPackageId = initialPackageId?.takeIf { it > 0 }
        if (directPackageId != null) {
            loadDetail(directPackageId, preserveList = emptyList(), isRefresh = false)
        } else {
            loadJourneys(isRefresh = false)
        }
    }

    private fun loadJourneys(isRefresh: Boolean) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob?.cancel()
        val epoch = loadEpoch.incrementAndGet()
        sessionBoundary.apply(owner) {
            _state.update {
                it.copy(
                    selectedPackageId = null,
                    delivery = null,
                    loading = !isRefresh,
                    refreshing = isRefresh,
                    error = null,
                    retryPackageId = null,
                )
            }
        }
        loadJob = sessionJobs.launch {
            loadAllJourneys(owner).fold(
                onSuccess = { journeys ->
                    if (!isCurrent(owner, epoch)) return@fold
                    when (journeys.size) {
                        0 -> publish(
                            owner,
                            epoch,
                            DeliveryCenterUiState(loadedOnce = true, loading = false),
                        )
                        1 -> fetchAndPublishDetail(
                            owner = owner,
                            epoch = epoch,
                            packageId = journeys.single().packageId,
                            journeys = journeys,
                        )
                        else -> publish(
                            owner,
                            epoch,
                            DeliveryCenterUiState(
                                journeys = journeys,
                                loadedOnce = true,
                                loading = false,
                            ),
                        )
                    }
                },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    publishError(owner, epoch, failure, retryPackageId = null)
                },
            )
        }
    }

    private fun loadDetail(
        packageId: Int,
        preserveList: List<PackageJourney>,
        isRefresh: Boolean,
    ) {
        if (packageId <= 0) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob?.cancel()
        val epoch = loadEpoch.incrementAndGet()
        sessionBoundary.apply(owner) {
            _state.update {
                it.copy(
                    journeys = preserveList,
                    selectedPackageId = packageId,
                    loading = !isRefresh,
                    refreshing = isRefresh,
                    error = null,
                    retryPackageId = null,
                )
            }
        }
        loadJob = sessionJobs.launch {
            fetchAndPublishDetail(owner, epoch, packageId, preserveList)
        }
    }

    /**
     * ⚠️ THE ORDER OF THESE TWO READS USED TO DECIDE WHETHER THE SCREEN EXISTED.
     *
     * This was `deliveryTracking(...).fold { … }` with the timeline fetched
     * INSIDE the success arm, so `/packages/{id}/delivery-tracking` was a
     * precondition for showing anything at all. Two consequences:
     *
     *  1. A pickup package has no `package_deliveries` row, so that call cannot
     *     succeed for one — the customer got "Couldn't load" over a package the
     *     server was perfectly able to describe.
     *  2. Even for a real delivery, a hiccup on the last-mile read blanked a
     *     journey that had loaded fine. The rail is composed by
     *     `/packages/{id}/timeline`; `delivery` is never rendered as steps.
     *
     * The canonical journey now leads. The last mile is asked for only when the
     * journey says there is one, and its failure alone no longer blanks Track.
     */
    private suspend fun fetchAndPublishDetail(
        owner: AuthenticatedSessionOwner,
        epoch: Int,
        packageId: Int,
        journeys: List<PackageJourney>,
    ) {
        val timelineResult = gateway.packageTimeline(packageId)
        val journey = journeys.firstOrNull { it.packageId == packageId }
        // A pickup is not a delivery that happens to have no driver yet. Asking
        // for its last mile is asking for a row that does not exist.
        val deliveryResult = if (journey?.fulfilment == JourneyFulfilment.Pickup) {
            null
        } else {
            gateway.deliveryTracking(packageId)
        }

        // Only when we could read NEITHER is there nothing to put on screen.
        if (timelineResult.isFailure && deliveryResult?.isSuccess != true) {
            val failure = timelineResult.exceptionOrNull()
                ?: deliveryResult?.exceptionOrNull()
                ?: IllegalStateException(TRACK_UNAVAILABLE)
            if (failure is CancellationException) throw failure
            publishError(
                owner = owner,
                epoch = epoch,
                failure = failure,
                retryPackageId = packageId,
                journeys = journeys,
            )
            return
        }

        publish(
            owner,
            epoch,
            DeliveryCenterUiState(
                journeys = journeys,
                selectedPackageId = packageId,
                delivery = deliveryResult?.getOrNull()?.delivery,
                timeline = timelineResult.getOrNull().orEmpty(),
                timelineUnavailable = timelineResult.isFailure,
                loading = false,
                loadedOnce = true,
            ),
        )
    }

    private suspend fun loadAllJourneys(
        owner: AuthenticatedSessionOwner,
    ): Result<List<PackageJourney>> = runCatching {
        val all = mutableListOf<PackageJourney>()
        val seenPackageIds = mutableSetOf<Int>()
        var page = 1
        while (true) {
            if (!sessionBoundary.isCurrent(owner)) throw CancellationException("Session replaced")
            val response = gateway.packageJourneys(page = page, perPage = ACTIVE_PAGE_SIZE)
                .getOrThrow()
            response.journeys.forEach { journey ->
                // ⚠️ A ROW ON TWO PAGES IS NORMAL, NOT CORRUPTION — KEEP THE FIRST.
                //
                // This used to `error(TRACK_UNAVAILABLE)` on a repeat, which
                // discarded EVERY package and rendered "Tracking information is
                // unavailable" — including the pickup this branch exists to
                // surface. But overlap is an ordinary property of offset
                // pagination over live data: if a package changes status
                // between the page-1 and page-2 requests, the server re-sorts
                // and one row legitimately lands on both pages.
                //
                // iOS hit exactly this on 2026-07-25 and fixed it the same way
                // (FigmaDeliveryCenterViewController.fetchAllJourneys, verbatim:
                // "overlap is a normal property of offset pagination over live
                // data ... A user with >50 active deliveries could lose the
                // whole screen to that"). Android kept throwing, so the two
                // platforms rendered different screens from identical bytes.
                //
                // The guard was inherited from /deliveries/active, where a
                // second page was rare. /packages/journeys carries EVERY
                // trackable package, so multi-page reads are now the common
                // case and this fired far more often than it ever did before.
                //
                // Integrity is not lost: DeliveryTrackingRepository.packageJourneys
                // still rejects a duplicate id WITHIN a page, which is the shape
                // that actually indicates a corrupt payload.
                if (seenPackageIds.add(journey.packageId)) all += journey
            }
            if (!response.hasNextPage) break
            page += 1
            if (page > MAX_ACTIVE_PAGES) error(TRACK_UNAVAILABLE)
        }
        all
    }

    private fun publish(
        owner: AuthenticatedSessionOwner,
        epoch: Int,
        value: DeliveryCenterUiState,
    ) {
        if (!isCurrent(owner, epoch)) return
        sessionBoundary.apply(owner) {
            if (epoch == loadEpoch.get()) _state.value = value
        }
    }

    private fun publishError(
        owner: AuthenticatedSessionOwner,
        epoch: Int,
        failure: Throwable,
        retryPackageId: Int?,
        journeys: List<PackageJourney> = _state.value.journeys,
    ) {
        if (!isCurrent(owner, epoch)) return
        sessionBoundary.apply(owner) {
            if (epoch != loadEpoch.get()) return@apply
            _state.update {
                it.copy(
                    journeys = journeys,
                    selectedPackageId = retryPackageId,
                    delivery = null,
                    // The rail must go with the error. Leaving a stale timeline
                    // behind would render the previous package's journey under
                    // an error banner.
                    timeline = emptyList(),
                    timelineUnavailable = false,
                    loading = false,
                    refreshing = false,
                    loadedOnce = true,
                    error = failure.message?.takeIf(String::isNotBlank) ?: TRACK_UNAVAILABLE,
                    retryPackageId = retryPackageId,
                )
            }
        }
    }

    private fun isCurrent(owner: AuthenticatedSessionOwner, epoch: Int): Boolean =
        epoch == loadEpoch.get() && sessionBoundary.isCurrent(owner)

    companion object {
        private const val ACTIVE_PAGE_SIZE = 50
        private const val MAX_ACTIVE_PAGES = 100
        private const val TRACK_UNAVAILABLE =
            "Tracking information is unavailable. Please try again."

        fun factory(initialPackageId: Int?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DeliveryCenterViewModel(initialPackageId = initialPackageId) as T
            }
    }
}
