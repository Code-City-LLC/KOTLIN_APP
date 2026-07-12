package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.repo.CustomerTierReader
import com.ga.airdrop.data.repo.TierChanger
import com.ga.airdrop.data.repo.TierRepository
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TierCatalogStatus { Loading, Ready, Failed }

/** Phase of the PATCH→GET tier-change flow driven from the change sheet. */
enum class TierChangePhase { Idle, Working, Success, Error }

data class GoldPriorityUiState(
    val resolvedTierIndex: Int? = null,
    val benefitRowsByCode: Map<String, List<String>> = emptyMap(),
    val catalogStatus: TierCatalogStatus = TierCatalogStatus.Loading,
    /** Backend change authorization — the offer list is authoritative. */
    val canChange: Boolean = false,
    val changeOffers: List<com.ga.airdrop.data.model.TierChangeOption> = emptyList(),
    val changePhase: TierChangePhase = TierChangePhase.Idle,
    /** Display name of the tier the customer just changed to (Success only). */
    val changeSuccessName: String? = null,
    val changeError: String? = null,
)

fun interface CurrentTierNameReader {
    suspend fun read(): String?
}

class GoldPriorityViewModel(
    private val tierReader: CustomerTierReader = TierRepository(ApiClient.service),
    private val fallbackTierNameReader: CurrentTierNameReader = defaultTierNameReader(),
    private val tierChanger: TierChanger = TierRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(GoldPriorityUiState())
    val state: StateFlow<GoldPriorityUiState> = _state

    init {
        loadTierData()
    }

    fun retryBenefits() = loadTierData()

    /**
     * PATCH /customers/me/tier → GET confirmation → pager/benefits refresh
     * (Swift onConfirmTierChange: PATCH applied, then the tier resolvers
     * re-run so the pager lands on the new tier). The backend owns the
     * change rules; its error message surfaces verbatim.
     */
    fun changeTier(requestedTierCode: String, targetName: String) {
        if (_state.value.changePhase == TierChangePhase.Working) return
        // Offer-gated (CoralCove #22805): PATCH only what the backend offered.
        // Never trust page order for authorization. Same predicate the sheets
        // use for target selection (single source of truth).
        val snapshot = _state.value
        if (!isOfferedChange(snapshot.changeOffers, snapshot.canChange, requestedTierCode)) {
            _state.update {
                it.copy(
                    changePhase = TierChangePhase.Error,
                    changeError = "This tier change isn't available for your account right now.",
                )
            }
            return
        }
        _state.update { it.copy(changePhase = TierChangePhase.Working, changeError = null) }
        viewModelScope.launch {
            tierChanger.changeTier(requestedTierCode)
                .onSuccess {
                    _state.update {
                        it.copy(
                            changePhase = TierChangePhase.Success,
                            changeSuccessName = targetName,
                        )
                    }
                    // GET confirmation: authoritative re-read of tier + catalog.
                    loadTierData()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            changePhase = TierChangePhase.Error,
                            changeError = e.message ?: "Unable to change tier. Please try again.",
                        )
                    }
                }
        }
    }

    /** Change sheet dismissed — back to Idle so it can run again. */
    fun resetChangeFlow() {
        _state.update {
            it.copy(
                changePhase = TierChangePhase.Idle,
                changeSuccessName = null,
                changeError = null,
            )
        }
    }

    private fun loadTierData() {
        _state.update { it.copy(catalogStatus = TierCatalogStatus.Loading) }
        viewModelScope.launch {
            val catalog = async { tierReader.serviceTiers() }
            val customer = async { tierReader.customerTier() }
            val fallback = async { runCatching { fallbackTierNameReader.read() }.getOrNull() }

            val catalogResult = catalog.await()
            val customerResult = customer.await()
            val fallbackName = fallback.await()
            val resolvedIndex = indexForTier(
                code = customerResult.getOrNull()?.currentTier,
                name = fallbackName,
            )

            val customerTier = customerResult.getOrNull()
            _state.update { previous ->
                previous.copy(
                    resolvedTierIndex = resolvedIndex ?: previous.resolvedTierIndex,
                    benefitRowsByCode = catalogResult.getOrNull()
                        ?.let(::serverBenefitRows)
                        .orEmpty(),
                    catalogStatus = if (
                        catalogResult.isSuccess &&
                        !catalogResult.getOrNull().isNullOrEmpty()
                    ) TierCatalogStatus.Ready else TierCatalogStatus.Failed,
                    // Fail-closed: a failed tier fetch leaves canChange=false
                    // and no offers — the sheets then present nothing to PATCH.
                    canChange = customerTier?.canChange ?: false,
                    changeOffers = customerTier?.availableChanges.orEmpty(),
                )
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

internal fun serverBenefitRows(tiers: List<ServiceTier>): Map<String, List<String>> =
    tiers.mapNotNull { tier ->
        val code = tier.code.trim().uppercase()
        if (code.isEmpty()) return@mapNotNull null
        val rows = buildList {
            tier.processingCopy?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
            addAll(tier.benefitsSummary.map(String::trim).filter(String::isNotEmpty))
        }.distinctBy(::normalizedBenefit)
        code to rows
    }.toMap()

private fun normalizedBenefit(value: String): String =
    value.lowercase().trim().removeSuffix(".").replace(Regex("\\s+"), " ")

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
