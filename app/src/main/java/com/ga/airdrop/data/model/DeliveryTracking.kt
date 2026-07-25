package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Customer-safe mirror of Laravel's delivery-tracking projection.
 *
 * The backend owns stage labels, ordering, state, and timestamps. Android must
 * never infer progress from package status or manufacture timeline rows.
 */
@Serializable
data class ActiveDeliveriesPayload(
    val deliveries: List<ActiveDeliverySummary> = emptyList(),
    val meta: DeliveryTrackingPagination? = null,
)

@Serializable
data class ActiveDeliverySummary(
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageId: Int? = null,
    @SerialName("tracking_code")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingCode: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val status: String? = null,
    @SerialName("scheduled_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val scheduledDate: String? = null,
    @SerialName("current_stage_key")
    @Serializable(with = FlexibleStringSerializer::class)
    val currentStageKey: String? = null,
    @SerialName("updated_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val updatedAt: String? = null,
)

@Serializable
data class DeliveryTrackingPagination(
    @SerialName("current_page")
    @Serializable(with = FlexibleIntSerializer::class)
    val currentPage: Int? = null,
    @SerialName("per_page")
    @Serializable(with = FlexibleIntSerializer::class)
    val perPage: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val total: Int? = null,
    @SerialName("last_page")
    @Serializable(with = FlexibleIntSerializer::class)
    val lastPage: Int? = null,
)

@Serializable
data class DeliveryTrackingPayload(
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageId: Int? = null,
    val delivery: DeliveryTracking? = null,
)

@Serializable
data class DeliveryTracking(
    @Serializable(with = FlexibleStringSerializer::class)
    val status: String? = null,
    @SerialName("scheduled_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val scheduledDate: String? = null,
    @SerialName("assigned_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val assignedAt: String? = null,
    @SerialName("out_for_delivery_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val outForDeliveryAt: String? = null,
    @SerialName("delivered_at")
    @Serializable(with = FlexibleStringSerializer::class)
    val deliveredAt: String? = null,
    val stages: List<DeliveryTrackingStage> = emptyList(),
)

@Serializable
data class DeliveryTrackingStage(
    @Serializable(with = FlexibleStringSerializer::class)
    val key: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val label: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val state: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val at: String? = null,
)
