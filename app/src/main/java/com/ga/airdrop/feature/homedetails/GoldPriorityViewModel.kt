package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.api.ApiErrorCodes
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.repo.CustomerTierGateway
import com.ga.airdrop.data.repo.TierRepository
import com.ga.airdrop.data.repo.UserRepository
import com.ga.airdrop.data.repo.serverErrorCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoldPriorityUiState(
    /** Index into [tierPages] resolved from the user's customer tier, or null until loaded. */
    val resolvedTierIndex: Int? = null,
    /** The customer's live tier code (service_tiers.code) from the tier API, e.g. "GOLD". */
    val currentTierCode: String? = null,
    /** Whether self-service tier changes are offered (GET /customers/me/tier can_change). */
    val canChange: Boolean = false,
    /** code → "upgrade" | "downgrade" | "same" from available_changes. */
    val directionByCode: Map<String, String> = emptyMap(),
    /** Non-null while a change to that code is in flight (drives the sheet spinner). */
    val changingToCode: String? = null,
    /** Set when a change fails, so the sheet can show the backend's message. */
    val changeError: String? = null,
    /** One-shot success name (e.g. "Gold Standard") for the confirmation banner. */
    val justChangedToName: String? = null,
    /**
     * API tier code → backend-authored benefit bullets
     * (service_tiers.benefits_summary). The page renders these when present and
     * falls back to its own copy when the catalogue is empty/unavailable.
     */
    val benefitsByCode: Map<String, List<String>> = emptyMap(),
)

/** Legacy tier-name fallback source (the user payload's customer_tier name). */
fun interface CurrentTierNameProvider {
    suspend fun tierName(): String?
}

/**
 * Backs the Customer Tier page. Resolves the user's current tier for the pager
 * (Swift FigmaGoldPriorityViewController.loadUserTier) AND drives self-service
 * upgrade/downgrade against the Laravel Tier API — the backend validates and
 * owns the change; this only requests it and reflects the result.
 */
class GoldPriorityViewModel(
    private val tierNameProvider: CurrentTierNameProvider = defaultTierNameProvider(),
    private val tierRepository: CustomerTierGateway = TierRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(GoldPriorityUiState())
    val state: StateFlow<GoldPriorityUiState> = _state

    init {
        loadCurrentTier()
    }

    private fun loadCurrentTier() {
        viewModelScope.launch {
            // Legacy fallback: resolve the pager index from the user payload's
            // tier NAME. Keeps the page working if the tier API is unavailable.
            val tierName = runCatching { tierNameProvider.tierName() }.getOrNull()
                ?.trim()?.lowercase().orEmpty()
            if (tierName.isNotEmpty()) {
                indexForTierName(tierName)?.let { idx ->
                    _state.update { if (it.currentTierCode == null) it.copy(resolvedTierIndex = idx) else it }
                }
            }
            // Backend-authored benefit bullets (benefits_summary), so the page
            // shows the live copy instead of hardcoded strings. Best-effort:
            // on failure the page keeps its own fallback copy.
            tierRepository.serviceTiers().onSuccess { tiers ->
                val benefits = benefitsByCodeFrom(tiers)
                if (benefits.isNotEmpty()) {
                    _state.update { it.copy(benefitsByCode = benefits) }
                }
            }
            // Authoritative: the tier API drives the current code + change options.
            tierRepository.customerTier().onSuccess { tier ->
                _state.update { applyCustomerTier(it, tier) }
            }
        }
    }

    /** Request an upgrade/downgrade — backend-validated (PATCH /customers/me/tier). */
    fun requestTierChange(code: String) {
        if (_state.value.changingToCode != null) return
        _state.update { it.copy(changingToCode = code, changeError = null) }
        viewModelScope.launch {
            tierRepository.changeTier(code)
                .onSuccess { result ->
                    val newCode = result.requestedTierCode.ifBlank { code }
                    // Header badge reflects the new tier immediately.
                    tierPages.firstOrNull { it.apiCode == newCode }
                        ?.let { page -> SessionStore.update { it.copy(tierName = page.name) } }
                    _state.update { applyChangeSuccess(it, newCode) }
                    // Old available_changes are stale post-switch; refetch.
                    tierRepository.customerTier().onSuccess { tier ->
                        _state.update {
                            it.copy(
                                canChange = tier.canChange,
                                directionByCode = directionsFrom(tier),
                            )
                        }
                    }
                }
                .onFailure { e ->
                    // Prefer the coded copy for tier errors (error_code pact),
                    // else the backend message, else a clean fallback.
                    val message = ApiErrorCodes.friendlyCopy(e.serverErrorCode())
                        ?: e.message
                        ?: "Couldn't change your tier. Please try again."
                    _state.update { it.copy(changingToCode = null, changeError = message) }
                }
        }
    }

    fun dismissChangeError() = _state.update { it.copy(changeError = null) }

    fun consumeJustChanged() = _state.update { it.copy(justChangedToName = null) }

    private fun indexForTierName(tierName: String): Int? {
        val i = tierPages.indexOfFirst { tier ->
            val lc = tier.name.lowercase()
            lc == tierName ||
                lc.startsWith(tierName) ||
                tierName.startsWith(lc.substringBefore(" "))
        }
        return i.takeIf { it >= 0 }
    }

    companion object {
        private fun defaultTierNameProvider(): CurrentTierNameProvider {
            val users = UserRepository(ApiClient.service)
            return CurrentTierNameProvider { users.currentUser().getOrNull()?.customerTierName }
        }
    }
}

// ─── Pure state transitions (unit-tested without coroutines) ────────────────

/** Resolve a [tierPages] index for a Laravel tier code, or null. */
internal fun indexForTierCode(code: String?): Int? {
    if (code.isNullOrBlank()) return null
    return tierPages.indexOfFirst { it.apiCode.equals(code, ignoreCase = true) }.takeIf { it >= 0 }
}

/** code → direction from the customer tier's available_changes (blank codes dropped). */
internal fun directionsFrom(tier: com.ga.airdrop.data.model.CustomerTier): Map<String, String> =
    tier.availableChanges.filter { it.code.isNotBlank() }.associate { it.code to it.direction }

/**
 * Map the tier catalogue to code → MERGED benefit bullets — the Swift
 * FigmaGoldPriorityViewController.apiBenefits model (Kemar 2026-07-11 "add
 * back everything"), in order:
 *   1. processing_copy (backend)
 *   2. benefits_summary lines (backend, verbatim)
 *   3. flag-derived facts ONLY when the server sent no benefits_summary
 *      (otherwise AirCoins/returns would show twice on GOLD/PLAT/DIAM)
 *   4. restored legacy marketing lines (curated, minus dupes)
 */
internal fun benefitsByCodeFrom(tiers: List<ServiceTier>): Map<String, List<String>> =
    tiers.filter { it.code.isNotBlank() }
        .associate { it.code.uppercase() to mergedBenefits(it) }

private fun mergedBenefits(tier: ServiceTier): List<String> {
    val rows = mutableListOf<String>()
    tier.processingCopy?.takeIf { it.isNotBlank() }?.let(rows::add)
    rows += tier.benefitsSummary
    if (tier.benefitsSummary.isEmpty()) {
        if (tier.isPriority) rows += "Priority processing lane."
        if (tier.aircoinsEligible) rows += "Earns AirCoins on eligible shipping charges."
        if (tier.freeReturnLbCap > 0) {
            rows += "Free returns up to ${formatLb(tier.freeReturnLbCap)} lb per package."
        }
    }
    restoredMarketingBenefits[tier.code.uppercase()].orEmpty()
        .filterNot { it in rows }
        .forEach(rows::add)
    if (rows.isEmpty()) rows += "Standard AirDrop service."
    return rows
}

private fun formatLb(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * Restored original-app benefit copy (Swift restoredMarketingBenefits,
 * Kemar 2026-07-11 "add back everything"), minus: (a) lines excluded by
 * explicit ruling — RUBY and SAVR must never mention AirCoins; (b) exact
 * duplicates of the API rows; (c) the held conflicts pending Kemar's ruling
 * (next-day/unlimited-storage on DIAM, 24h/60-day on PLAT, 2-3-day on RUBY)
 * which must NOT be shown against contradicting backend copy.
 */
private val restoredMarketingBenefits: Map<String, List<String>> = mapOf(
    "DIAM" to listOf(
        "Priority logging, warehouse handling, and customs clearance.",
        "Dedicated WhatsApp VIP line for real-time assistance.",
        "Exclusive discounts and AirCoins multipliers on every shipment.",
        "Early access to clearance events, auctions, and flash sales.",
        "Personalized account concierge for dispute or issue resolution.",
        "Surprise appreciation gifts for milestone achievements (e.g. 100th shipment, 1-year VIP anniversary).",
    ),
    "PLAT" to listOf(
        "Premium customer support queue with faster handling.",
        "Up to 10% shipping discounts and reduced handling fees.",
        "Double AirCoins events and random loyalty gifts.",
        "Priority in pre-auction and sales events.",
        "Access to affiliate and referral bonuses.",
        "Complimentary upgrade offers during seasonal promotions.",
    ),
    "GOLD" to listOf(
        "Free storage for 30 days on all incoming packages.",
        "3-5% discounted shipping rates.",
        "Standard loyalty rewards plus double-points promotions during AirDrop events.",
        "Early notifications for sales, warehouse auctions, and holiday offers.",
        "General support line priority over standard-tier members.",
        "Eligibility for seasonal upgrade offers.",
    ),
    "RUBY" to listOf(
        "No free storage included.",
        "Competitive base shipping rates.",
        "Exclusive partner coupons and limited-time promos.",
        "Access to standard customer support channels during business hours.",
        "Auto-upgrade eligibility after 12 months of consistent activity.",
    ),
    "SAVR" to listOf(
        "Basic processing (3-5 business days).",
        "Standard shipping rates.",
        "No free storage included.",
        "Access to limited promotions and onboarding discounts.",
        "Eligibility for early upgrade upon meeting spend thresholds.",
        "Welcome emails and loyalty guidance to familiarize them with benefits.",
    ),
)

/** Fold GET /customers/me/tier into the page state (the authoritative source). */
internal fun applyCustomerTier(
    prev: GoldPriorityUiState,
    tier: com.ga.airdrop.data.model.CustomerTier,
): GoldPriorityUiState = prev.copy(
    currentTierCode = tier.currentTier,
    canChange = tier.canChange,
    resolvedTierIndex = indexForTierCode(tier.currentTier) ?: prev.resolvedTierIndex,
    directionByCode = directionsFrom(tier),
)

/** Fold a successful PATCH into the page state. */
internal fun applyChangeSuccess(prev: GoldPriorityUiState, newCode: String): GoldPriorityUiState =
    prev.copy(
        changingToCode = null,
        changeError = null,
        currentTierCode = newCode,
        resolvedTierIndex = indexForTierCode(newCode) ?: prev.resolvedTierIndex,
        justChangedToName = tierPages.firstOrNull { it.apiCode == newCode }?.name ?: newCode,
        directionByCode = emptyMap(),
    )
