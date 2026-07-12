package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
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

data class GoldPriorityUiState(
    val resolvedTierIndex: Int? = null,
    val benefitRowsByCode: Map<String, List<String>> = emptyMap(),
    val catalogStatus: TierCatalogStatus = TierCatalogStatus.Loading,
)

fun interface CurrentTierNameReader {
    suspend fun read(): String?
}

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
