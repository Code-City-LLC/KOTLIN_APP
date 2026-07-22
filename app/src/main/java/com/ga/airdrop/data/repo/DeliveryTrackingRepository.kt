package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.ActiveDeliverySummary
import com.ga.airdrop.data.model.DeliveryTracking
import com.ga.airdrop.data.model.DeliveryTrackingStage

data class ActiveDelivery(
    val packageId: Int,
    val trackingCode: String?,
    val description: String?,
    val status: String,
    val scheduledDate: String?,
    val currentStageKey: String,
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

interface DeliveryTrackingGateway {
    suspend fun activeDeliveries(page: Int, perPage: Int): Result<ActiveDeliveriesPage>
    suspend fun deliveryTracking(packageId: Int): Result<DeliveryTrackingResult>
}

/**
 * Strict boundary around Laravel's customer delivery projection. Malformed or
 * contradictory payloads fail closed instead of letting the UI infer stages.
 */
class DeliveryTrackingRepository(
    private val service: AirdropApiService,
) : DeliveryTrackingGateway {

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

        val deliveries = payload.deliveries.map(ActiveDeliverySummary::toDomain)
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

private fun ActiveDeliverySummary.toDomain(): ActiveDelivery {
    val id = packageId?.takeIf { it > 0 } ?: error(DELIVERY_CONTRACT_ERROR)
    val normalizedStatus = status?.trim()?.takeIf { it in ACTIVE_DELIVERY_STATUSES }
        ?: error(DELIVERY_CONTRACT_ERROR)
    val normalizedStage = currentStageKey?.trim()?.takeIf(String::isNotEmpty)
        ?: error(DELIVERY_CONTRACT_ERROR)
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
    val normalizedStatus = status?.trim()?.takeIf { it in DELIVERY_STATUSES }
        ?: error(DELIVERY_CONTRACT_ERROR)
    if (stages.isEmpty()) error(DELIVERY_CONTRACT_ERROR)
    val normalizedStages = stages.map(DeliveryTrackingStage::toDomain)
    if (normalizedStages.map { it.key }.distinct().size != normalizedStages.size) {
        error(DELIVERY_CONTRACT_ERROR)
    }
    val currentCount = normalizedStages.count { it.state == "current" }
    val expectedCurrentCount = if (normalizedStatus in TERMINAL_DELIVERY_STATUSES) 0 else 1
    if (currentCount != expectedCurrentCount) error(DELIVERY_CONTRACT_ERROR)

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
    val normalizedState = state?.trim()?.takeIf { it in DELIVERY_STAGE_STATES }
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
private val DELIVERY_STATUSES =
    setOf("assigned", "out_for_delivery", "delivered", "failed", "cancelled")
private val ACTIVE_DELIVERY_STATUSES = setOf("assigned", "out_for_delivery")
private val TERMINAL_DELIVERY_STATUSES = setOf("delivered", "failed", "cancelled")
private val DELIVERY_STAGE_STATES = setOf("done", "current", "pending")
