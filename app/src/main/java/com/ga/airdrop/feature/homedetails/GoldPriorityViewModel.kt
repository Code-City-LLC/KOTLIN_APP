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
 * Map the tier catalogue to code → benefit bullets, dropping tiers with a blank
 * code or no bullets so the page only overrides its fallback copy when the
 * backend actually supplied benefits_summary for that tier.
 */
internal fun benefitsByCodeFrom(tiers: List<ServiceTier>): Map<String, List<String>> =
    tiers.filter { it.code.isNotBlank() && it.benefitsSummary.isNotEmpty() }
        .associate { it.code.uppercase() to it.benefitsSummary }

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
