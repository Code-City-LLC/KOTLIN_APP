package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Laravel's canonical package journey — `GET /api/v1/packages/{id}/timeline`.
 *
 * The server owns ordering, labels, icon keys, state, duplicate collapse,
 * last-mile composition and the single pending step. Kotlin used to derive all
 * of that from `/packages/{id}` history plus `/delivery-tracking`, in three
 * separate screens, each with its own copy of the rules. Kemar's call, once the
 * endpoint landed: switch, so the answer lives on the side that owns it.
 *
 * Verified identical to what the client-side merge produced for AIRQA0725003
 * before the swap — this moved the answer, it did not change it.
 */
@Serializable
data class PackageTimelinePayload(
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageId: Int? = null,
    @SerialName("tracking_code")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingCode: String? = null,
    val entries: List<PackageTimelineEntry> = emptyList(),
    /** Key of the row the package is on right now; null when nothing has happened. */
    @SerialName("current_key")
    @Serializable(with = FlexibleStringSerializer::class)
    val currentKey: String? = null,
    @SerialName("has_delivery")
    val hasDelivery: Boolean? = null,
)

@Serializable
data class PackageTimelineEntry(
    /** Stable per-row identity. Warehouse rows repeat a status legitimately. */
    @Serializable(with = FlexibleStringSerializer::class)
    val key: String? = null,
    /** Warehouse status id; null on a last-mile leg. */
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val label: String? = null,
    /** Server-owned glyph key. Unknown keys fall back rather than guess. */
    @Serializable(with = FlexibleStringSerializer::class)
    val icon: String? = null,
    /** Preformatted for display. */
    @Serializable(with = FlexibleStringSerializer::class)
    val at: String? = null,
    @SerialName("at_iso")
    @Serializable(with = FlexibleStringSerializer::class)
    val atIso: String? = null,
    /** done | current | pending */
    @Serializable(with = FlexibleStringSerializer::class)
    val state: String? = null,
    /** status | delivery */
    @Serializable(with = FlexibleStringSerializer::class)
    val source: String? = null,
)
