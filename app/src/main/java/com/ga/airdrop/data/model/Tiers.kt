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
    /**
     * Backend authorization for tier changes — the OFFER LIST is authoritative
     * (Swift: "direction here is authoritative for the change sheet; index
     * math is only the fallback"). No offer / can_change=false ⇒ the client
     * must not PATCH (CoralCove #22805).
     */
    @SerialName("can_change") val canChange: Boolean = false,
    @SerialName("available_changes") val availableChanges: List<TierChangeOption> = emptyList(),
)

@Serializable
data class TierChangeOption(
    val code: String = "",
    val name: String? = null,
    @SerialName("lane_rank") val laneRank: Int? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
    /** "upgrade" | "downgrade" | "same" — backend-declared, authoritative. */
    val direction: String? = null,
)

/** PATCH /customers/me/tier body — Swift AirdropAPI.TierChangeRequest. */
@Serializable
data class TierChangeRequest(
    @SerialName("requested_tier_code") val requestedTierCode: String,
)

/**
 * PATCH /customers/me/tier RESPONSE — Laravel returns a change RESULT
 * (requested_tier_code/effective_at/status/message), NOT the CustomerTier
 * shape (gate #22836-2: decoding it as CustomerTier silently produced an
 * empty tier under lenient JSON). Success is still only claimed after the
 * authoritative GET confirmation.
 */
@Serializable
data class TierChangeResult(
    @SerialName("requested_tier_code") val requestedTierCode: String? = null,
    @SerialName("effective_at") val effectiveAt: String? = null,
    val status: String? = null,
    val message: String? = null,
)
