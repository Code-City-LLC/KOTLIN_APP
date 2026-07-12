package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.repo.CustomerTierReader
import com.ga.airdrop.data.repo.TierRepository
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TierCatalogStatus { Loading, Ready, Failed }

/**
 * A full server change offer (available_changes[] entry, normalized). The
 * server owns CTA visibility, target, name and direction — page order and
 * lane math never decide (#22450 contract).
 */
data class TierOffer(
    val code: String,
    val name: String,
    val laneRank: Int,
    val direction: String,
)

data class GoldPriorityUiState(
    /** Pager landing index (confirmed identity code, else legacy-name fallback). */
    val resolvedTierIndex: Int? = null,
    /** UPPERCASE code → benefits_summary rows, verbatim and in server order. */
    val benefitRowsByCode: Map<String, List<String>> = emptyMap(),
    val catalogStatus: TierCatalogStatus = TierCatalogStatus.Loading,
    /** GET /customers/me/tier succeeded with a RESOLVABLE current code. */
    val tierConfirmed: Boolean = false,
    /** Current-tier GET failed or returned a blank/unknown code — Retry only. */
    val tierLoadFailed: Boolean = false,
    /** Confirmed current code (normalized) incl. INACTIVE/CORPORATE identities. */
    val currentTierCode: String? = null,
    val canChange: Boolean = false,
    /** The server's full change offers — the ONLY offer source. */
    val offers: List<TierOffer> = emptyList(),
    /** UPPERCASE code → server display_name (nonblank only). */
    val namesByCode: Map<String, String> = emptyMap(),
    /** Non-null from PATCH start until the authoritative GET settles. */
    val changingToCode: String? = null,
    /** PATCH ok, confirmation GET failed: ONLY Retry-GET and dismiss allowed. */
    val awaitingConfirmation: Boolean = false,
    /** Requested code retained while Retry-GET is the only allowed action. */
    val awaitingCode: String? = null,
    val changeError: String? = null,
    /** GET-confirmed new tier name — drives the sheet's Welcome/Done state. */
    val changeSuccessName: String? = null,
)

fun interface CurrentTierNameReader {
    suspend fun read(): String?
}

/**
 * Customer Tier page brain. Server truth end to end (#22450 contract):
 * benefits_summary rendered verbatim, the FULL
 * available_changes offers own the CTA, and a PATCH stays PROVISIONAL until
 * the authoritative GET is folded.
 */
class GoldPriorityViewModel(
    private val tierReader: CustomerTierReader = TierRepository(ApiClient.service),
    private val fallbackTierNameReader: CurrentTierNameReader = defaultTierNameReader(),
) : ViewModel() {

    private val _state = MutableStateFlow(GoldPriorityUiState())
    val state: StateFlow<GoldPriorityUiState> = _state

    init {
        loadTierData()
    }

    fun retryBenefits() = loadTierData()

    /** Retry surface for a failed current-tier confirmation. */
    fun retryCurrentTier() = loadTierData()

    /** PATCH /customers/me/tier, then confirm via the authoritative GET. */
    fun requestTierChange(code: String) {
        viewModelScope.launch { executeTierChange(_state, tierReader, code) }
    }

    /** Retry the post-PATCH confirmation GET — only a successful GET decides. */
    fun retryChangeConfirmation() {
        if (!_state.value.awaitingConfirmation) return
        viewModelScope.launch { confirmTierState(_state, tierReader) }
    }

    fun dismissChangeError() = _state.update { it.copy(changeError = null) }

    /** The sheet's Done button after the in-sheet Welcome state. */
    fun consumeChangeSuccess() = _state.update { it.copy(changeSuccessName = null) }

    private fun loadTierData() {
        _state.update {
            it.copy(catalogStatus = TierCatalogStatus.Loading, tierLoadFailed = false)
        }
        viewModelScope.launch {
            val catalog = async { tierReader.serviceTiers() }
            val customer = async { tierReader.customerTier() }
            val fallback = async { runCatching { fallbackTierNameReader.read() }.getOrNull() }

            val catalogResult = catalog.await()
            val customerResult = customer.await()
            val fallbackName = fallback.await()

            _state.update { previous ->
                val tiers = catalogResult.getOrNull()
                val afterCatalog = previous.copy(
                    benefitRowsByCode = tiers?.let(::serverBenefitRows).orEmpty(),
                    namesByCode = tiers?.let(::serverDisplayNames).orEmpty(),
                    catalogStatus = if (!tiers.isNullOrEmpty()) {
                        TierCatalogStatus.Ready
                    } else {
                        TierCatalogStatus.Failed
                    },
                )
                val customerTier = customerResult.getOrNull()
                if (customerTier != null) {
                    applyCustomerTier(afterCatalog, customerTier)
                } else {
                    afterCatalog.copy(
                        tierConfirmed = false,
                        tierLoadFailed = true,
                        // Legacy-name fallback keeps a sensible pager landing;
                        // it never confirms and never enables controls.
                        resolvedTierIndex = indexForTier(code = null, name = fallbackName)
                            ?: afterCatalog.resolvedTierIndex,
                    )
                }
            }
        }
    }

    companion object {
        private fun defaultTierNameReader(): CurrentTierNameReader {
            val users = UserRepository(ApiClient.service)
            return CurrentTierNameReader { users.currentUser().getOrNull()?.customerTierName }
        }
    }
}

// ─── Server copy folding (pre-existing main owners, unchanged) ──────────────

internal fun serverBenefitRows(tiers: List<ServiceTier>): Map<String, List<String>> =
    tiers.mapNotNull { tier ->
        val code = tier.code.trim().uppercase()
        if (code.isEmpty()) return@mapNotNull null
        val rows = tier.benefitsSummary.filter(String::isNotBlank)
        code to rows
    }.toMap()

internal fun indexForTier(code: String?, name: String?): Int? {
    val normalizedCode = code?.trim()?.uppercase().orEmpty()
    if (normalizedCode.isNotEmpty()) {
        tierPages.indexOfFirst { it.apiCode == normalizedCode }
            .takeIf { it >= 0 }
            ?.let { return it }
    }
    val normalizedName = name?.trim()?.lowercase().orEmpty()
    if (normalizedName.isEmpty()) return null
    return tierPages.indexOfFirst { tier ->
        val pageName = tier.name.lowercase()
        pageName == normalizedName ||
            pageName.startsWith(normalizedName) ||
            normalizedName.startsWith(pageName.substringBefore(" "))
    }.takeIf { it >= 0 }
}

/** UPPERCASE code → nonblank server display_name overrides. */
internal fun serverDisplayNames(tiers: List<ServiceTier>): Map<String, String> =
    tiers.filter { it.code.isNotBlank() && !it.displayName.isNullOrBlank() }
        .associate { it.code.trim().uppercase() to it.displayName!!.trim() }

// ─── Change flow (suspend, ViewModel-free so tests drive it directly) ───────

/**
 * Single-flight PATCH → authoritative GET. While a change is in flight OR the
 * last change is awaiting its confirmation GET, new PATCHes are rejected:
 * Retry-GET is the only forward path in that state.
 */
internal suspend fun executeTierChange(
    state: MutableStateFlow<GoldPriorityUiState>,
    gateway: CustomerTierReader,
    code: String,
) {
    val started = state.claimChangeSlot(code)
    if (!started) return

    gateway.changeTier(code)
        .onSuccess { confirmTierState(state, gateway) }
        .onFailure { e ->
            state.update {
                it.copy(
                    changingToCode = null,
                    changeError = e.message ?: "Couldn't change your tier. Please try again.",
                )
            }
        }
}

/** The authoritative confirmation GET — shared by the change flow and Retry. */
internal suspend fun confirmTierState(
    state: MutableStateFlow<GoldPriorityUiState>,
    gateway: CustomerTierReader,
) {
    gateway.customerTier()
        .onSuccess { tier -> state.update { applyConfirmedChange(it, tier) } }
        .onFailure { e ->
            state.update {
                it.copy(
                    changingToCode = null,
                    awaitingCode = it.changingToCode ?: it.awaitingCode,
                    awaitingConfirmation = true,
                    changeError = e.message
                        ?: "Change requested — retry to confirm what your tier is now.",
                )
            }
        }
}

/** Atomic in-flight claim; rejected while changing or awaiting confirmation. */
private fun MutableStateFlow<GoldPriorityUiState>.claimChangeSlot(code: String): Boolean {
    var claimed = false
    update { current ->
        if (current.changingToCode != null || current.awaitingConfirmation) {
            claimed = false
            current
        } else {
            claimed = true
            current.copy(changingToCode = code, changeError = null)
        }
    }
    return claimed
}

// ─── Pure state transitions (unit-tested without coroutines) ────────────────

private fun normalizeCode(code: String): String = code.trim().uppercase()

/**
 * Pager index for a CURRENT-tier code. Identity codes cover every page —
 * including INACTIVE/CORPORATE, which are valid current states the customer
 * cannot self-select.
 */
internal fun pageIndexForCurrentCode(code: String?): Int? {
    if (code.isNullOrBlank()) return null
    val normalized = normalizeCode(code)
    return tierPages.indexOfFirst { it.identityCode == normalized }.takeIf { it >= 0 }
}

/** Full normalized offers from available_changes (blank codes dropped). */
internal fun offersFrom(tier: CustomerTier): List<TierOffer> =
    tier.availableChanges.filter { it.code.isNotBlank() }.map {
        TierOffer(
            code = normalizeCode(it.code),
            name = it.name.trim(),
            laneRank = it.laneRank,
            direction = it.direction.trim().lowercase(),
        )
    }

/**
 * The server's UPGRADE offer for a visible page — owns CTA visibility, label
 * and target. Null unless the tier is confirmed, changes are allowed, and the
 * server explicitly offers an upgrade for this page's code.
 */
internal fun upgradeOfferFor(state: GoldPriorityUiState, page: TierPage): TierOffer? {
    if (!state.tierConfirmed || !state.canChange) return null
    val code = page.apiCode ?: return null
    return state.offers.firstOrNull { it.code == code && it.direction == "upgrade" }
}

/**
 * Breakdown targets — the server's nearest offered step in each direction
 * (min laneRank among upgrades, max among downgrades). Non-adjacent offers
 * surface exactly as the server sent them.
 */
internal fun breakdownUpgradeOffer(state: GoldPriorityUiState): TierOffer? =
    if (state.tierConfirmed && state.canChange) {
        state.offers.filter { it.direction == "upgrade" }.minByOrNull { it.laneRank }
    } else null

internal fun breakdownDowngradeOffer(state: GoldPriorityUiState): TierOffer? =
    if (state.tierConfirmed && state.canChange) {
        state.offers.filter { it.direction == "downgrade" }.maxByOrNull { it.laneRank }
    } else null

/** The page rendering a server offer's visuals, when one exists. */
internal fun pageForOffer(offer: TierOffer): TierPage? =
    tierPages.firstOrNull { it.apiCode == offer.code }

/** The page title — server display_name when nonblank, else the design name. */
internal fun tierTitleFor(page: TierPage, state: GoldPriorityUiState): String =
    page.apiCode?.let { state.namesByCode[it] } ?: page.name

/** An offer's display title — server name, display_name, else design name. */
internal fun offerTitle(offer: TierOffer, state: GoldPriorityUiState): String =
    offer.name.ifBlank { state.namesByCode[offer.code] ?: pageForOffer(offer)?.name ?: offer.code }

/**
 * Fold the authoritative GET /customers/me/tier. A blank or unresolvable
 * current code FAILS CLOSED: the page keeps its prior landing but exposes the
 * current-tier retry surface, and no change controls exist.
 */
internal fun applyCustomerTier(
    prev: GoldPriorityUiState,
    tier: CustomerTier,
): GoldPriorityUiState {
    val code = normalizeCode(tier.currentTier)
    val index = pageIndexForCurrentCode(code)
        ?: return prev.copy(tierConfirmed = false, tierLoadFailed = true)
    return prev.copy(
        tierConfirmed = true,
        tierLoadFailed = false,
        currentTierCode = code,
        canChange = tier.canChange,
        resolvedTierIndex = index,
        offers = offersFrom(tier),
    )
}

/**
 * Fold the post-PATCH confirmation GET: whatever the server says is the tier
 * now — including "unchanged" (then no success state). The header chip moves
 * here and only here, strictly after the GET.
 */
internal fun applyConfirmedChange(
    prev: GoldPriorityUiState,
    tier: CustomerTier,
): GoldPriorityUiState {
    val expectedCode = prev.changingToCode ?: prev.awaitingCode
    val returnedCode = normalizeCode(tier.currentTier)
    if (pageIndexForCurrentCode(returnedCode) == null ||
        (expectedCode != null && returnedCode != normalizeCode(expectedCode))
    ) {
        return prev.copy(
            changingToCode = null,
            awaitingConfirmation = true,
            awaitingCode = expectedCode,
            changeSuccessName = null,
            changeError = "Couldn't confirm the requested tier — retry.",
        )
    }
    val folded = applyCustomerTier(prev, tier)
    val code = folded.currentTierCode ?: ""
    val confirmedName = tier.displayName?.takeIf { it.isNotBlank() }
        ?: prev.namesByCode[code]
        ?: tierPages.firstOrNull { it.identityCode == code }?.name
        ?: code
    val changed = code != (prev.currentTierCode ?: "")
    if (changed) {
        SessionStore.update { it.copy(tierName = confirmedName) }
    }
    return folded.copy(
        changingToCode = null,
        awaitingConfirmation = false,
        awaitingCode = null,
        changeError = null,
        changeSuccessName = if (changed) confirmedName else null,
    )
}
