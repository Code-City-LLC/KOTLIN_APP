package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.ActiveDeliverySummary
import com.ga.airdrop.data.model.DeliveryTracking
import com.ga.airdrop.data.model.DeliveryTrackingStage
import com.ga.airdrop.data.model.JourneyStageDto
import com.ga.airdrop.data.model.PackageJourneySummary
import com.ga.airdrop.data.model.PackageTimelineEntry
import java.util.Locale

data class ActiveDelivery(
    val packageId: Int,
    val trackingCode: String?,
    val description: String?,
    val status: String,
    val scheduledDate: String?,
    /**
     * Null for a DELIVERED delivery — nothing is "current" once terminal.
     * Laravel #79690 §3. This used to be non-null and threw.
     */
    val currentStageKey: String?,
    val updatedAt: String?,
)

data class ActiveDeliveriesPage(
    val deliveries: List<ActiveDelivery>,
    val currentPage: Int,
    val hasNextPage: Boolean,
)

data class TrackedDeliveryStage(
    val key: String,
    val label: String,
    val state: String,
    val at: String?,
)

data class TrackedDelivery(
    val status: String,
    val scheduledDate: String?,
    val assignedAt: String?,
    val outForDeliveryAt: String?,
    val deliveredAt: String?,
    val stages: List<TrackedDeliveryStage>,
)

data class DeliveryTrackingResult(
    val packageId: Int,
    val delivery: TrackedDelivery?,
)

/**
 * How a package reaches its owner. The server sends this as a REQUIRED literal
 * because the two are genuinely different flows — a pickup is never forced
 * through the four delivery stages — and because it is the thing
 * `/deliveries/active` could not express at all.
 */
enum class JourneyFulfilment { Pickup, Delivery }

/** One composed row of a journey rail. Server-owned; the client renders it. */
data class JourneyStage(
    val key: String,
    val label: String,
    val icon: String?,
    /** Numeric warehouse status; null on a last-mile leg. */
    val status: Int?,
    /** done | current | pending. */
    val state: String,
    val at: String?,
)

/**
 * One Track row from `/packages/journeys` — the superset of [ActiveDelivery]
 * that finally includes pickup and warehouse-only packages.
 */
data class PackageJourney(
    val packageId: Int,
    val ardNumber: String?,
    val description: String?,
    val valueUsd: String?,
    val weightLbs: String?,
    val shipper: String?,
    val status: Int,
    val statusName: String,
    val statusIcon: String?,
    val fulfilment: JourneyFulfilment,
    val currentStage: String?,
    val stages: List<JourneyStage>,
)

data class PackageJourneysPage(
    val journeys: List<PackageJourney>,
    val currentPage: Int,
    val hasNextPage: Boolean,
)

interface DeliveryTrackingGateway {
    /**
     * The canonical Track root. Includes pickup and warehouse-only packages,
     * which [activeDeliveries] structurally cannot.
     */
    suspend fun packageJourneys(page: Int, perPage: Int): Result<PackageJourneysPage>

    suspend fun activeDeliveries(page: Int, perPage: Int): Result<ActiveDeliveriesPage>
    suspend fun deliveryTracking(packageId: Int): Result<DeliveryTrackingResult>

    /**
     * Laravel's canonical journey. The client does not reorder, filter, cap or
     * relabel what comes back — see TrackJourney.
     */
    suspend fun packageTimeline(packageId: Int): Result<List<PackageTimelineEntry>>
}

/**
 * Strict boundary around Laravel's customer delivery projection. Malformed or
 * contradictory payloads fail closed instead of letting the UI infer stages.
 */
class DeliveryTrackingRepository(
    private val service: AirdropApiService,
) : DeliveryTrackingGateway {

    /**
     * The Track root. Every trackable package, pickup included.
     *
     * ⚠️ THIS IS STRICTER THAN [activeDeliveries] ON PURPOSE, and the two rules
     * are not in conflict.
     *
     * [activeDeliveries] drops an unrecognised row (`mapNotNull`) because its
     * hazard is a *new status string* — Laravel keeps adding them, and one
     * `error()` inside a `map` blanked every customer's Track screen once
     * already. That hazard does not exist here: `status` is numeric and
     * deliberately NOT allow-listed, so a status this build has never seen is
     * already valid input, not an unknown row.
     *
     * What is rejected here is genuine corruption — a row with no id, no
     * status, no `fulfilment`, or an empty `stages` rail. Those are schema
     * regressions, and dropping them would silently hide a package from a
     * customer who owns it, which is the very failure this endpoint exists to
     * end. iOS rejects exactly the same set (AirdropAPI.PackageJourney,
     * #89556/#89576/#89595); a client that quietly kept rows iOS refuses would
     * mean two different Track screens over identical bytes.
     */
    override suspend fun packageJourneys(
        page: Int,
        perPage: Int,
    ): Result<PackageJourneysPage> = apiResult {
        require(page > 0 && perPage in 1..50)
        val payload = service.packageJourneys(page = page, perPage = perPage, track = null)
        if (payload.success == false) error(payload.message ?: JOURNEY_CONTRACT_ERROR)

        // ⚠️ `data == null` is a FAILED READ, never "you have no packages".
        // An explicit `[]` is the honest empty state and must come through.
        // Collapsing the two is how a customer with packages gets told they
        // have none — see PackageJourneysPayload.data.
        val rows = payload.data ?: error(payload.message ?: JOURNEY_CONTRACT_ERROR)
        val meta = payload.meta ?: error(payload.message ?: JOURNEY_CONTRACT_ERROR)

        val journeys = rows.map { it.toDomain() }
        // Rejected, not silently deduped: the list needs a stable identity per
        // row, and a duplicate id means the page itself is wrong.
        if (journeys.map { it.packageId }.distinct().size != journeys.size) {
            error(JOURNEY_CONTRACT_ERROR)
        }

        val currentPage = meta.currentPage ?: error(JOURNEY_CONTRACT_ERROR)
        val lastPage = meta.lastPage
        val total = meta.total
        // Page 2 rendered as page 1 shows the wrong packages, so the echo is
        // required to match rather than defaulted to the request.
        if (
            currentPage != page ||
            (lastPage != null && (lastPage <= 0 || lastPage < currentPage)) ||
            (total != null && total < 0)
        ) {
            error(JOURNEY_CONTRACT_ERROR)
        }

        PackageJourneysPage(
            journeys = journeys,
            currentPage = currentPage,
            hasNextPage = lastPage?.let { currentPage < it } ?: (journeys.size >= perPage),
        )
    }

    /**
     * ⚠️ A SOFT-ERROR ENVELOPE IS NOT AN EMPTY JOURNEY.
     *
     * This used to be `service.packageTimeline(id).data?.entries.orEmpty()` with
     * no `success` guard — the only method in this file that skipped the check
     * its siblings apply, and the only one with no packageId cross-check.
     *
     * Why `.orEmpty()` could not save it: when `data` is null or absent,
     * `DataEnvelopeSerializer` re-decodes the WHOLE envelope as the payload
     * (Envelopes.kt:131-135). With `ignoreUnknownKeys = true` and every
     * PackageTimelinePayload field defaulted, that fallback CANNOT fail — so
     * `{"success":false,"message":"Package not found","data":null}` yields
     * `entries = []` and `data` is never null. The result was
     * `Result.success(emptyList())`.
     *
     * Downstream that reads as fact, not as failure: PackageDetailsViewModel
     * sets `timelineOutcome = LOADED`, and the screen's "read failed" arm needs
     * `timeline.isNotEmpty()` so it never fires. A customer whose package has a
     * full journey — drop alerted, received, customs, ready for pickup — is
     * told it has NO recorded history, with no error and no retry. That is the
     * exact harm `timelineOutcome` was introduced to prevent, bypassed one
     * layer beneath it.
     *
     * Now matches [deliveryTracking] and [activeDeliveries]: routed through
     * `apiResult`, rejects `success == false`, and cross-checks the returned
     * package id so a response for a different package cannot be shown.
     */
    override suspend fun packageTimeline(packageId: Int): Result<List<PackageTimelineEntry>> =
        apiResult {
            require(packageId > 0)
            val envelope = service.packageTimeline(packageId)
            val payload = envelope.data
                ?.takeUnless { envelope.success == false }
                ?: error(envelope.message ?: DELIVERY_CONTRACT_ERROR)
            // Null id = the fallback decode above produced a hollow payload from
            // an error envelope. A mismatched id = a response for someone else's
            // package. Neither is a timeline.
            if (payload.packageId == null || payload.packageId != packageId) {
                error(envelope.message ?: DELIVERY_CONTRACT_ERROR)
            }
            payload.entries
        }

    override suspend fun activeDeliveries(
        page: Int,
        perPage: Int,
    ): Result<ActiveDeliveriesPage> = apiResult {
        require(page > 0 && perPage in 1..50)
        val envelope = try {
            service.activeDeliveries(page = page, perPage = perPage)
        } catch (e: retrofit2.HttpException) {
            // Rollout shim: until Laravel ships GET deliveries/active, the
            // endpoint 404s — show the honest empty state, not an error screen.
            // Remove once the backend endpoint is live.
            if (e.code() == 404) {
                return@apiResult ActiveDeliveriesPage(
                    deliveries = emptyList(),
                    currentPage = page,
                    hasNextPage = false,
                )
            }
            throw e
        }
        val payload = envelope.data
            ?.takeUnless { envelope.success == false }
            ?: error(envelope.message ?: DELIVERY_CONTRACT_ERROR)

        // ⚠️ mapNotNull, NOT map. One unrecognised row must never blank the
        // whole screen.
        //
        // toDomain() error()s on an unknown status, and an error() inside map()
        // rejects the ENTIRE payload — Track renders "Couldn't load" over a
        // response that was almost entirely good. The comment on toDomain
        // records that this ALREADY SHIPPED once: the allow-list was
        // {assigned, out_for_delivery}, Laravel started returning `delivered`,
        // and one delivered row killed every customer's Track screen.
        //
        // Widening the allow-list fixes that one instance and leaves the
        // mechanism intact for the next new status. Laravel is adding exactly
        // that: /packages/journeys deliberately carries pickup and
        // warehouse-only packages whose statuses are outside this set
        // (ORC 89528/89569). Dropping the unknown row is a visible gap in one
        // card; error()ing is a blank screen for everyone.
        val deliveries = payload.deliveries.mapNotNull { it.toDomainOrNull() }
        if (deliveries.map { it.packageId }.distinct().size != deliveries.size) {
            error(DELIVERY_CONTRACT_ERROR)
        }

        val currentPage = payload.meta?.currentPage ?: page
        val lastPage = payload.meta?.lastPage
        val total = payload.meta?.total
        if (
            currentPage != page ||
            currentPage <= 0 ||
            (lastPage != null && (lastPage <= 0 || lastPage < currentPage)) ||
            (total != null && total < 0)
        ) {
            error(DELIVERY_CONTRACT_ERROR)
        }

        ActiveDeliveriesPage(
            deliveries = deliveries,
            currentPage = currentPage,
            hasNextPage = lastPage?.let { currentPage < it } ?: (deliveries.size >= perPage),
        )
    }

    override suspend fun deliveryTracking(packageId: Int): Result<DeliveryTrackingResult> = apiResult {
        require(packageId > 0)
        val envelope = service.packageDeliveryTracking(packageId)
        val payload = envelope.data
            ?.takeUnless { envelope.success == false }
            ?: error(envelope.message ?: DELIVERY_CONTRACT_ERROR)
        if (payload.packageId != packageId) error(DELIVERY_CONTRACT_ERROR)

        DeliveryTrackingResult(
            packageId = packageId,
            delivery = payload.delivery?.toDomain(),
        )
    }
}

/**
 * A Track row, or a thrown contract error. There is deliberately no
 * `toDomainOrNull` sibling here — see the KDoc on
 * [DeliveryTrackingRepository.packageJourneys] for why this list fails closed
 * where the active-deliveries list degrades.
 */
private fun PackageJourneySummary.toDomain(): PackageJourney {
    val id = id?.takeIf { it > 0 } ?: error(JOURNEY_CONTRACT_ERROR)
    // REQUIRED but never allow-listed. Laravel adds warehouse statuses
    // constantly; a status this build has never seen is valid input and
    // renders under its server-sent name. A MISSING status is different — it
    // is a schema regression, and treating it as 0 would render some other
    // status's identity.
    val normalizedStatus = status ?: error(JOURNEY_CONTRACT_ERROR)
    // The server guarantees a non-empty name and already substitutes
    // "Unknown Status" for an unnamed status — but a blank slipped through to
    // iOS from a live DB row, so the same fallback is mirrored here rather
    // than rendering an empty status pill.
    val normalizedName = statusName?.trim()?.takeIf(String::isNotEmpty) ?: UNKNOWN_STATUS_NAME
    val normalizedFulfilment = when (fulfilment?.trim()?.lowercase(Locale.US)) {
        "pickup" -> JourneyFulfilment.Pickup
        "delivery" -> JourneyFulfilment.Delivery
        // Not defaulted to Delivery: guessing would route a package we are
        // holding for collection through the four delivery stages and tell the
        // customer it is on a van.
        else -> error(JOURNEY_CONTRACT_ERROR)
    }
    // `railFor()` always emits a progressive rail, so an empty one is a
    // regression, not a package with nothing to show.
    if (stages.isEmpty()) error(JOURNEY_CONTRACT_ERROR)
    val normalizedStages = stages.map(JourneyStageDto::toDomain)
    if (normalizedStages.map { it.key }.distinct().size != normalizedStages.size) {
        error(JOURNEY_CONTRACT_ERROR)
    }
    return PackageJourney(
        packageId = id,
        ardNumber = ardNumber?.trim()?.takeIf(String::isNotEmpty),
        description = description?.trim()?.takeIf(String::isNotEmpty),
        valueUsd = valueUsd?.trim()?.takeIf(String::isNotEmpty),
        weightLbs = weightLbs?.trim()?.takeIf(String::isNotEmpty),
        shipper = shipper?.trim()?.takeIf(String::isNotEmpty),
        status = normalizedStatus,
        statusName = normalizedName,
        statusIcon = statusIcon?.trim()?.takeIf(String::isNotEmpty),
        fulfilment = normalizedFulfilment,
        currentStage = currentStage?.trim()?.takeIf(String::isNotEmpty),
        stages = normalizedStages,
    )
}

private fun JourneyStageDto.toDomain(): JourneyStage {
    val normalizedKey = key?.trim()?.takeIf(String::isNotEmpty) ?: error(JOURNEY_CONTRACT_ERROR)
    val normalizedLabel = label?.trim()?.takeIf(String::isNotEmpty) ?: error(JOURNEY_CONTRACT_ERROR)
    // ⚠️ NOT coerced to "pending" the way DeliveryTrackingStage does it.
    //
    // There the state is one row of a rail whose shape is already known, so a
    // strange value degrades to the neutral row. Here the rail IS the screen:
    // silently calling an unrecognised state "pending" would show a completed
    // pickup as not-yet-happened. An unknown state is a schema change, and it
    // must surface as one.
    val normalizedState = state?.trim()?.lowercase(Locale.US)
        ?.takeIf { it in DELIVERY_STAGE_STATES }
        ?: error(JOURNEY_CONTRACT_ERROR)
    return JourneyStage(
        key = normalizedKey,
        label = normalizedLabel,
        icon = icon?.trim()?.takeIf(String::isNotEmpty),
        status = status,
        state = normalizedState,
        at = at?.trim()?.takeIf(String::isNotEmpty),
    )
}

/**
 * Null for a row this build does not understand, instead of throwing.
 *
 * Contract violations that indicate a BROKEN PAYLOAD are validated before the
 * forward-compatible status check — a missing package id is not a new status,
 * it is corruption. Only a non-blank, unrecognised *status* degrades to a
 * dropped row, because that is the case Laravel keeps legitimately extending.
 */
private fun ActiveDeliverySummary.toDomainOrNull(): ActiveDelivery? {
    packageId?.takeIf { it > 0 } ?: error(DELIVERY_CONTRACT_ERROR)
    val normalizedStatus = status?.trim()?.takeIf(String::isNotEmpty)
        ?: error(DELIVERY_CONTRACT_ERROR)
    if (normalizedStatus !in ACTIVE_LIST_STATUSES) return null
    return toDomain()
}

private fun ActiveDeliverySummary.toDomain(): ActiveDelivery {
    val id = packageId?.takeIf { it > 0 } ?: error(DELIVERY_CONTRACT_ERROR)
    // Laravel #79690 §3: /deliveries/active now RETURNS `delivered` (sorted
    // last) so a finished journey stays on Track. This allow-list was
    // {assigned, out_for_delivery} and `error()`d on anything else — and
    // because the throw happens inside the list map, ONE delivered row
    // rejected the ENTIRE payload and Track rendered "Couldn't load" over a
    // perfectly good response. Reproduced live on pre-staging before fixing.
    // failed/cancelled stay excluded — Laravel does not send them here.
    val normalizedStatus = status?.trim()?.takeIf { it in ACTIVE_LIST_STATUSES }
        ?: error(DELIVERY_CONTRACT_ERROR)
    // Null on a delivered row (nothing is current). Optional, never fatal.
    val normalizedStage = currentStageKey?.trim()?.takeIf(String::isNotEmpty)
    return ActiveDelivery(
        packageId = id,
        trackingCode = trackingCode?.trim()?.takeIf(String::isNotEmpty),
        description = description?.trim()?.takeIf(String::isNotEmpty),
        status = normalizedStatus,
        scheduledDate = scheduledDate?.trim()?.takeIf(String::isNotEmpty),
        currentStageKey = normalizedStage,
        updatedAt = updatedAt?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun DeliveryTracking.toDomain(): TrackedDelivery {
    // SwiftHawk #79761 §1: a fixed status list is fine for ORDERING but must
    // NEVER decide what is allowed to render. Filtering here meant any status
    // Laravel adds later would be dropped silently — no error, no placeholder,
    // the customer simply never sees that step. Kemar says many more statuses
    // are coming. Accept anything non-blank.
    val normalizedStatus = status?.trim()?.takeIf(String::isNotEmpty)
        ?: error(DELIVERY_CONTRACT_ERROR)
    if (stages.isEmpty()) error(DELIVERY_CONTRACT_ERROR)
    val normalizedStages = stages.map(DeliveryTrackingStage::toDomain)
    if (normalizedStages.map { it.key }.distinct().size != normalizedStages.size) {
        error(DELIVERY_CONTRACT_ERROR)
    }
    val currentCount = normalizedStages.count { it.state == "current" }
    // Terminal journeys mark nothing current. For a status we do not yet know,
    // we cannot assert how many rows should be current — so only enforce the
    // invariant for statuses whose semantics we actually know.
    val known = normalizedStatus in DELIVERY_STATUSES
    if (known) {
        val expectedCurrentCount = if (normalizedStatus in TERMINAL_DELIVERY_STATUSES) 0 else 1
        if (currentCount != expectedCurrentCount) error(DELIVERY_CONTRACT_ERROR)
    }

    return TrackedDelivery(
        status = normalizedStatus,
        scheduledDate = scheduledDate?.trim()?.takeIf(String::isNotEmpty),
        assignedAt = assignedAt?.trim()?.takeIf(String::isNotEmpty),
        outForDeliveryAt = outForDeliveryAt?.trim()?.takeIf(String::isNotEmpty),
        deliveredAt = deliveredAt?.trim()?.takeIf(String::isNotEmpty),
        stages = normalizedStages,
    )
}

private fun DeliveryTrackingStage.toDomain(): TrackedDeliveryStage {
    val normalizedKey = key?.trim()?.takeIf(String::isNotEmpty)
        ?: error(DELIVERY_CONTRACT_ERROR)
    val normalizedLabel = label?.trim()?.takeIf(String::isNotEmpty)
        ?: error(DELIVERY_CONTRACT_ERROR)
    // Same rule for the stage state: an unrecognised state renders as
    // "pending" rather than rejecting the whole timeline.
    val normalizedState = state?.trim()?.takeIf(String::isNotEmpty)
        ?.let { if (it in DELIVERY_STAGE_STATES) it else "pending" }
        ?: error(DELIVERY_CONTRACT_ERROR)
    return TrackedDeliveryStage(
        key = normalizedKey,
        label = normalizedLabel,
        state = normalizedState,
        at = at?.trim()?.takeIf(String::isNotEmpty),
    )
}

private const val DELIVERY_CONTRACT_ERROR =
    "Delivery information is unavailable. Please try again."
/** Track's root covers more than deliveries now, so it says so. */
private const val JOURNEY_CONTRACT_ERROR =
    "Tracking information is unavailable. Please try again."
/** Mirrors the server's own fallback for an unnamed status. */
private const val UNKNOWN_STATUS_NAME = "Unknown Status"
/** Known statuses — used for ORDERING and invariants only, never as a filter. */
private val DELIVERY_STATUSES =
    setOf("assigned", "out_for_delivery", "delivered", "failed", "cancelled")
/** Statuses `/deliveries/active` may return. `delivered` added per #79690 §3. */
private val ACTIVE_LIST_STATUSES = setOf("assigned", "out_for_delivery", "delivered")
private val TERMINAL_DELIVERY_STATUSES = setOf("delivered", "failed", "cancelled")
private val DELIVERY_STAGE_STATES = setOf("done", "current", "pending")
