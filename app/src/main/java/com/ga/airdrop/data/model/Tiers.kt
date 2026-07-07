package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * Tier system DTOs — mirrors the Laravel Tier API on pre_staging
 * (routes/api.php "Tier / Rate / Insurance / Customs / Returns / AirCoins").
 *
 * RULES (Kemar tier brief + group rulings):
 *  - Laravel is the single source of truth for every number and eligibility
 *    flag here. The app DISPLAYS these values and never recomputes them.
 *  - AirCoins and free returns render only when the API says eligible
 *    (RUBY and SAVR receive neither).
 *  - Insurance requires an explicit select/decline when the API returns
 *    explicit_required=true (SAVR only today).
 *  - Customs: the CUSTOMS FREEZE ruling (#16965) keeps the original
 *    ShippingCalculatorService as the only customer-facing customs number.
 *    CustomsEstimate below is typed for completeness but must NOT be wired
 *    into UI until the group unblocks it.
 */

/** GET /service-tiers — one row of the tier catalogue. */
@Serializable
data class ServiceTier(
    val code: String = "",
    @SerialName("display_name") val displayName: String = "",
    /** Lowercase slug: ruby | sapphire | gold | platinum | diamond. */
    val badge: String? = null,
    @SerialName("lane_rank") val laneRank: Int = 0,
    @SerialName("is_priority") val isPriority: Boolean = false,
    @SerialName("aircoins_eligible") val aircoinsEligible: Boolean = false,
    @SerialName("free_return_lb_cap") val freeReturnLbCap: Double = 0.0,
    @SerialName("processing_copy") val processingCopy: String? = null,
    /**
     * Backend-authored benefit bullets (service_tiers.benefits_summary, cast
     * `array` server-side). Populated live on pre-staging, so this MUST be a
     * string list — an earlier `String?` here threw on the JSON array and broke
     * the whole /service-tiers parse. `coerceInputValues` maps a null/missing
     * value to the empty list; the UI falls back to its own copy when empty.
     */
    @SerialName("benefits_summary") val benefitsSummary: List<String> = emptyList(),
)

/** One entry of `available_changes` on GET /customers/me/tier. */
@Serializable
data class TierChangeOption(
    val code: String = "",
    val name: String = "",
    @SerialName("lane_rank") val laneRank: Int = 0,
    @SerialName("is_current") val isCurrent: Boolean = false,
    /** "same" | "upgrade" | "downgrade" — backend-decided, never derived here. */
    val direction: String = "",
)

/** GET /customers/me/tier. */
@Serializable
data class CustomerTier(
    @SerialName("current_tier") val currentTier: String = "",
    @SerialName("display_name") val displayName: String = "",
    val badge: String? = null,
    @SerialName("effective_at") val effectiveAt: String? = null,
    @SerialName("can_change") val canChange: Boolean = false,
    @SerialName("available_changes") val availableChanges: List<TierChangeOption> = emptyList(),
    @SerialName("aircoins_eligible") val aircoinsEligible: Boolean = false,
    @SerialName("free_return_eligible") val freeReturnEligible: Boolean = false,
    @SerialName("free_return_lb_cap") val freeReturnLbCap: Double = 0.0,
)

/** PATCH /customers/me/tier request. */
@Serializable
data class TierChangeRequest(
    @SerialName("requested_tier_code") val requestedTierCode: String,
)

/** PATCH /customers/me/tier response. */
@Serializable
data class TierChangeResult(
    @SerialName("requested_tier_code") val requestedTierCode: String = "",
    @SerialName("effective_at") val effectiveAt: String? = null,
    val status: String? = null,
    val message: String? = null,
)

/** One weight break of GET /rates (display-only rate sheet, never final math). */
@Serializable
data class RateWeightBreak(
    @SerialName("weight_from") val weightFrom: Double = 0.0,
    @SerialName("weight_to") val weightTo: Double? = null,
    @SerialName("base_rate") val baseRate: Double = 0.0,
    @SerialName("fuel_surcharge") val fuelSurcharge: Double = 0.0,
    @SerialName("handling_fee") val handlingFee: Double = 0.0,
)

/** GET /rates. */
@Serializable
data class RatePreview(
    val method: String? = null,
    @SerialName("tier_code") val tierCode: String? = null,
    val destination: String? = null,
    val currency: String? = null,
    @SerialName("effective_from") val effectiveFrom: String? = null,
    @SerialName("weight_breaks") val weightBreaks: List<RateWeightBreak> = emptyList(),
)

/**
 * One quote/return line item. Rendered exactly as returned — the app never
 * recalculates amounts. Known codes today: base_shipping, fuel_surcharge,
 * handling_fee, insurance, storage, delivery, return_shipping,
 * aircoins_credit, free_return_allowance, return_overage.
 */
@Serializable
data class QuoteLineItem(
    val code: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    /** Heterogeneous per-code details (weights, rates, ids) — display-only. */
    val meta: JsonObject? = null,
)

/** GET /insurance/options — premium is per $100 block, priced by Laravel. */
@Serializable
data class InsuranceOptions(
    @SerialName("insured_value") val insuredValue: Double = 0.0,
    @SerialName("rate_per_100") val ratePer100: Double = 0.0,
    @SerialName("block_size") val blockSize: Int = 100,
    val blocks: Int = 0,
    @SerialName("blocks_used") val blocksUsed: Int = 0,
    val premium: Double = 0.0,
    @SerialName("max_coverage") val maxCoverage: Double? = null,
    @SerialName("covered_value") val coveredValue: Double = 0.0,
    @SerialName("can_decline") val canDecline: Boolean = false,
    val mandatory: Boolean = true,
    /** True → checkout must block until the customer selects or declines. */
    @SerialName("explicit_required") val explicitRequired: Boolean = false,
    @SerialName("rule_version_id") val ruleVersionId: Int? = null,
)

/** POST /shipments/quote request — every field optional except weight. */
@Serializable
data class ShipmentQuoteRequest(
    val weight: Double,
    val method: String? = null,
    val destination: String? = null,
    val currency: String? = null,
    @SerialName("declared_value") val declaredValue: Double? = null,
    @SerialName("insured_value") val insuredValue: Double? = null,
    @SerialName("item_name") val itemName: String? = null,
    @SerialName("insurance_declined") val insuranceDeclined: Boolean? = null,
    @SerialName("return_weight") val returnWeight: Double? = null,
    @SerialName("storage_charge") val storageCharge: Double? = null,
    @SerialName("delivery_charge") val deliveryCharge: Double? = null,
)

/**
 * POST /shipments/quote + GET /shipments/quotes/{reference} — an immutable
 * backend snapshot. When [isExpired] is true the app must fetch a fresh quote
 * before checkout/payment can proceed (never reuse a stale total).
 */
@Serializable
data class ShipmentQuote(
    @SerialName("quote_reference") val quoteReference: String? = null,
    @SerialName("customer_tier") val customerTier: JsonElement? = null,
    val method: String? = null,
    val destination: String? = null,
    val currency: String? = null,
    @SerialName("line_items") val lineItems: List<QuoteLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    @SerialName("total_due") val totalDue: Double = 0.0,
    val status: String? = null,
    @SerialName("is_expired") val isExpired: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("insurance_options") val insuranceOptions: InsuranceOptions? = null,
    @SerialName("insurance_choice_required") val insuranceChoiceRequired: Boolean? = null,
    @SerialName("aircoins_earned") val aircoinsEarned: Double? = null,
    @SerialName("rule_version_ids") val ruleVersionIds: JsonElement? = null,
)

/** POST /packages/{id}/insurance-selection request — select OR decline. */
@Serializable
data class InsuranceSelectionRequest(
    val selected: Boolean? = null,
    val declined: Boolean? = null,
    @SerialName("insured_value") val insuredValue: Double? = null,
)

/** Recorded insurance state for a package. */
@Serializable
data class InsuranceSelection(
    val selected: Boolean = false,
    val declined: Boolean = false,
    @SerialName("insured_value") val insuredValue: Double = 0.0,
    val premium: Double = 0.0,
    val status: String? = null,
    @SerialName("selected_at") val selectedAt: String? = null,
)

/**
 * GET /customs/estimate — typed for completeness only. CUSTOMS FREEZE
 * (#16965): do NOT render this anywhere until the group unblocks it; the
 * original shipping-calculator remains the only customer-facing customs.
 */
@Serializable
data class CustomsEstimate(
    @SerialName("declared_value") val declaredValue: Double = 0.0,
    val threshold: Double = 0.0,
    @SerialName("item_name") val itemName: String? = null,
    @SerialName("duty_percentage") val dutyPercentage: Double = 0.0,
    @SerialName("taxable_amount") val taxableAmount: Double = 0.0,
    @SerialName("estimated_customs") val estimatedCustoms: Double = 0.0,
    @SerialName("estimated_amount") val estimatedAmount: Double = 0.0,
    val basis: String? = null,
    val disclaimer: String? = null,
    @SerialName("rule_version_id") val ruleVersionId: Int? = null,
    @SerialName("duty_rate_id") val dutyRateId: Int? = null,
)

/** GET /returns/eligibility — free returns are GOLD/PLAT/DIAM only, per API. */
@Serializable
data class ReturnEligibility(
    @SerialName("tier_code") val tierCode: String? = null,
    val eligible: Boolean = false,
    @SerialName("free_lb_cap") val freeLbCap: Double = 0.0,
    @SerialName("free_lb_applied") val freeLbApplied: Double = 0.0,
    @SerialName("overage_weight") val overageWeight: Double = 0.0,
    @SerialName("overage_rate_per_lb") val overageRatePerLb: Double = 0.0,
    @SerialName("estimated_return_charge") val estimatedReturnCharge: Double = 0.0,
    @SerialName("rule_version_id") val ruleVersionId: Int? = null,
)

/** POST /returns/quote request. */
@Serializable
data class ReturnQuoteRequest(
    val weight: Double,
    @SerialName("tier_code") val tierCode: String? = null,
)

/** POST /returns/quote. */
@Serializable
data class ReturnQuote(
    @SerialName("tier_code") val tierCode: String? = null,
    @SerialName("free_lb_applied") val freeLbApplied: Double = 0.0,
    @SerialName("overage_weight") val overageWeight: Double = 0.0,
    @SerialName("line_items") val lineItems: List<QuoteLineItem> = emptyList(),
    @SerialName("total_due") val totalDue: Double = 0.0,
)

/** Package flags on GET /packages/{id}/tier. */
@Serializable
data class PackageTierFlags(
    @SerialName("priority_customer") val priorityCustomer: Boolean = false,
    @SerialName("ship_immediately") val shipImmediately: Boolean = false,
    @SerialName("cutoff_eligible") val cutoffEligible: Boolean = false,
    val note: String? = null,
)

/** GET /packages/{id}/tier — the tier snapshot recorded for one package. */
@Serializable
data class PackageTierInfo(
    @SerialName("package_id") val packageId: Int = 0,
    @SerialName("tier_code") val tierCode: String? = null,
    @SerialName("pricing_snapshot") val pricingSnapshot: JsonElement? = null,
    @SerialName("snapshot_at") val snapshotAt: String? = null,
    val insurance: InsuranceSelection? = null,
    val flags: PackageTierFlags? = null,
)
