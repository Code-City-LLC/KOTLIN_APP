package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /packages/journeys` — the canonical Track ROOT.
 *
 * ⚠️ THIS IS THE ENDPOINT THAT MAKES PICKUP PACKAGES VISIBLE.
 *
 * Track's root was `/deliveries/active`, which is driven by the
 * `package_deliveries` table. A package held for collection has NO delivery
 * row, so it never appeared — a customer whose package was sitting ready for
 * pickup opened Track and saw "No shipments to track". Nothing was broken and
 * nothing errored; the screen was simply reading a list that structurally
 * could not contain them.
 *
 * `/packages/journeys` is the superset: every trackable package, pickup and
 * delivery alike, each carrying a server-composed `stages` rail.
 *
 * ⚠️ A DIFFERENT ENVELOPE FROM EVERY OTHER LIST IN THIS FILE. Laravel answers
 * `{success, data: [...], meta: {...}}` — the array IS `data`, and `meta` is
 * its SIBLING, not nested inside it. That is why this does not go through
 * [DataEnvelope]: `DataEnvelope<List<PackageJourneySummary>>` decodes the rows
 * but drops `meta` on the floor, and `meta.current_page` is the cross-check
 * that stops page 2 being rendered as page 1.
 *
 * Contract mirrored field-for-field from the shipped iOS client
 * (`AirdropAPI.PackageJourney` / `JourneysPayload`, ORC #89538 / #89556 /
 * #89576 / #89595) so both platforms render the same Track screen from the
 * same bytes.
 *
 * Every field here is nullable-with-default ON PURPOSE. Decoding never throws
 * in this codebase; the contract is enforced one layer up in
 * `DeliveryTrackingRepository.packageJourneys`, where a violation becomes a
 * customer-safe `Result.failure` instead of a raw `SerializationException`,
 * and where a single test can drive the real repository. See
 * PackageJourneysContractTest.
 */
@Serializable
data class PackageJourneysPayload(
    val success: Boolean? = null,
    val message: String? = null,
    /**
     * ⚠️ NULLABLE, and the null is load-bearing.
     *
     * `null` means Laravel did not send a `data` key at all — an error body, a
     * shape change, a truncated response. `emptyList()` means it sent `[]`:
     * "you have nothing to track", which is a fact and must render as the
     * honest empty state.
     *
     * Defaulting this to `emptyList()` would collapse those two into the same
     * screen and tell a customer with packages that they have none. That is
     * the exact defect class `packageTimeline` and `airCoinsBalance` were both
     * fixed for; it is not being reintroduced here.
     */
    val data: List<PackageJourneySummary>? = null,
    val meta: DeliveryTrackingPagination? = null,
)

/** One Track row. Reuses [DeliveryTrackingPagination] for `meta`. */
@Serializable
data class PackageJourneySummary(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("ard_number")
    @Serializable(with = FlexibleStringSerializer::class)
    val ardNumber: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String? = null,
    /** Preformatted by the server; the client does not compute or convert it. */
    @SerialName("value_usd")
    @Serializable(with = FlexibleStringSerializer::class)
    val valueUsd: String? = null,
    @SerialName("weight_lbs")
    @Serializable(with = FlexibleStringSerializer::class)
    val weightLbs: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val shipper: String? = null,
    /** Numeric warehouse status. REQUIRED, but NOT allow-listed — see below. */
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int? = null,
    @SerialName("status_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val statusName: String? = null,
    @SerialName("status_icon")
    @Serializable(with = FlexibleStringSerializer::class)
    val statusIcon: String? = null,
    /** `pickup` | `delivery` — the flow discriminator. REQUIRED. */
    @Serializable(with = FlexibleStringSerializer::class)
    val fulfilment: String? = null,
    @SerialName("current_stage")
    @Serializable(with = FlexibleStringSerializer::class)
    val currentStage: String? = null,
    /** Server-composed rail. REQUIRED and non-empty — never a silent `[]`. */
    val stages: List<JourneyStageDto> = emptyList(),
)

/**
 * One composed stage of a journey rail. The server owns key, label, icon,
 * state and the display timestamp; `status` is the numeric package status the
 * stage maps to and is absent on a last-mile leg.
 */
@Serializable
data class JourneyStageDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val key: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val label: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val icon: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int? = null,
    /** done | current | pending. Laravel owns this; the client never guesses. */
    @Serializable(with = FlexibleStringSerializer::class)
    val state: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val at: String? = null,
)
