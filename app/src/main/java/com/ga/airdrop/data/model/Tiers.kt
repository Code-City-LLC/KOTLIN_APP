package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceTier(
    val code: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("processing_copy") val processingCopy: String? = null,
    @SerialName("benefits_summary") val benefitsSummary: List<String> = emptyList(),
)

@Serializable
data class CustomerTier(
    @SerialName("current_tier") val currentTier: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("effective_at") val effectiveAt: String? = null,
    @SerialName("can_change") val canChange: Boolean = false,
    @SerialName("available_changes") val availableChanges: List<TierChangeOption> = emptyList(),
)

/** One server change offer — the ONLY source of upgrade/downgrade targets. */
@Serializable
data class TierChangeOption(
    val code: String = "",
    val name: String = "",
    @SerialName("lane_rank") val laneRank: Int = 0,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val direction: String = "",
)

@Serializable
data class TierChangeRequest(
    @SerialName("requested_tier_code") val requestedTierCode: String,
)

/**
 * PATCH /customers/me/tier response — PROVISIONAL by contract (#22450): it
 * acknowledges the request only; the follow-up GET /customers/me/tier is the
 * sole authoritative source for the customer's tier.
 */
@Serializable
data class TierChangeResult(
    @SerialName("requested_tier_code") val requestedTierCode: String = "",
    @SerialName("effective_at") val effectiveAt: String? = null,
    val status: String? = null,
    val message: String? = null,
)
