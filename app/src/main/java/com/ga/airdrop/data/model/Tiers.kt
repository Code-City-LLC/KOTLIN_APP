package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceTier(
    val code: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("processing_copy") val processingCopy: String? = null,
    @SerialName("benefits_summary") val benefitsSummary: List<String> = emptyList(),
    /**
     * Upgrade pricing (2026-07-19): null/0 = free self-serve switch; > 0 means
     * the backend billing gate requires payment (PATCH answers 402 with its
     * own message until the tier checkout flow ships).
     */
    @SerialName("upgrade_price_usd") val upgradePriceUsd: Double? = null,
    @SerialName("upgrade_price_jmd") val upgradePriceJmd: Double? = null,
    /** "one_time" | "monthly" | "yearly" */
    @SerialName("billing_period") val billingPeriod: String? = null,
    @SerialName("requires_payment") val requiresPayment: Boolean = false,
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
    /** Upgrade pricing — mirrors ServiceTier so the change sheet can price a
     *  switch without a second catalog fetch (2026-07-19). */
    @SerialName("upgrade_price_usd") val upgradePriceUsd: Double? = null,
    @SerialName("upgrade_price_jmd") val upgradePriceJmd: Double? = null,
    @SerialName("billing_period") val billingPeriod: String? = null,
    @SerialName("requires_payment") val requiresPayment: Boolean = false,
) {
    /** "$9.99 USD/month" display label; null when the switch is free. */
    val priceLabel: String?
        get() {
            val usd = upgradePriceUsd ?: 0.0
            val jmd = upgradePriceJmd ?: 0.0
            if (usd <= 0.0 && jmd <= 0.0) return null
            val amount = if (usd > 0.0) {
                "$" + String.format("%.2f", usd) + " USD"
            } else {
                "J$" + String.format("%.2f", jmd)
            }
            return when (billingPeriod) {
                "monthly" -> "$amount/month"
                "yearly" -> "$amount/year"
                else -> amount
            }
        }
}

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
