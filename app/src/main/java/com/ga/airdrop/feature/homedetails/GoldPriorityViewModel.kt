package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedRequest
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
import com.ga.airdrop.data.model.TierChangeOption
import com.ga.airdrop.data.repo.CustomerTierReader
import com.ga.airdrop.data.repo.TierChanger
import com.ga.airdrop.data.repo.TierRepository
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TierCatalogStatus { Loading, Ready, Failed }

enum class TierResolutionStatus { Loading, Resolved, Failed }

enum class TierChangePhase { Idle, Working, Success, Error }

data class GoldPriorityUiState(
    val resolvedTierIndex: Int? = null,
    val benefitRowsByCode: Map<String, List<String>> = emptyMap(),
    val catalogStatus: TierCatalogStatus = TierCatalogStatus.Loading,
    val resolutionStatus: TierResolutionStatus = TierResolutionStatus.Loading,
    val canChange: Boolean = false,
    val changeOffers: List<TierChangeOption> = emptyList(),
    val changePhase: TierChangePhase = TierChangePhase.Idle,
    val changeSuccessName: String? = null,
    val changeError: String? = null,
)

fun interface CurrentTierNameReader {
    suspend fun read(): String?
}

class GoldPriorityViewModel(
    private val tierReader: CustomerTierReader = TierRepository(ApiClient.service),
    private val fallbackTierNameReader: CurrentTierNameReader = defaultTierNameReader(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
    private val tierChanger: TierChanger = TierRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(GoldPriorityUiState())
    val state: StateFlow<GoldPriorityUiState> = _state
    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var loadJob: Job? = null
    private var changeJob: Job? = null

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionJobs.replaceSession()
                loadJob = null
                changeJob = null
                sessionOwner = changed
                _state.value = GoldPriorityUiState()
                if (changed != null) loadTierData()
            }
        }
        loadTierData()
    }

    fun retryBenefits() = loadTierData()

    fun changeTier(requestedTierCode: String, targetName: String) {
        if (changeJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        val snapshot = _state.value
        if (!isOfferedChange(snapshot.changeOffers, snapshot.canChange, requestedTierCode)) {
            sessionBoundary.apply(owner) {
                _state.update {
                    it.copy(
                        changePhase = TierChangePhase.Error,
                        changeError = "This tier change isn't available for your account right now.",
                    )
                }
            }
            return
        }
        if (
            !sessionBoundary.apply(owner) {
                _state.update {
                    it.copy(
                        changePhase = TierChangePhase.Working,
                        changeSuccessName = null,
                        changeError = null,
                    )
                }
            }
        ) return

        changeJob = sessionJobs.launch {
            // Revalidate immediately before handing the mutation to Retrofit.
            // AuthInterceptor repeats this check at dispatch and cancels an
            // in-flight request if the authenticated generation changes.
            if (sessionBoundary.requestOwner(owner)?.provenance != requestOwner.provenance) {
                return@launch
            }
            val patchResult = tierChanger.changeTier(
                requestedTierCode = requestedTierCode,
                expectedSession = requestOwner.provenance,
            )
            if (patchResult.isFailure) {
                sessionBoundary.apply(owner) {
                    _state.update {
                        it.copy(
                            changePhase = TierChangePhase.Error,
                            changeError = patchResult.exceptionOrNull()?.message
                                ?: "Unable to change tier. Please try again.",
                        )
                    }
                }
                return@launch
            }

            // Never issue the confirmation read for a replacement session,
            // and never let an old request publish success into the new one.
            if (sessionBoundary.requestOwner(owner)?.provenance != requestOwner.provenance) {
                return@launch
            }

            val confirmation = tierReader.customerTier()
            val confirmed = confirmation.getOrNull()
            if (confirmed?.currentTier?.equals(requestedTierCode, ignoreCase = true) == true) {
                if (loadTierDataNow(owner, prefetchedTier = confirmed)) {
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(
                                changePhase = TierChangePhase.Success,
                                changeSuccessName = targetName,
                                changeError = null,
                            )
                        }
                    }
                }
            } else {
                sessionBoundary.apply(owner) {
                    _state.update {
                        it.copy(
                            changePhase = TierChangePhase.Error,
                            changeError = confirmation.exceptionOrNull()?.message
                                ?: "We couldn't confirm the change — your tier is unchanged. Please try again.",
                        )
                    }
                }
            }
        }
    }

    fun resetChangeFlow() {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        sessionBoundary.apply(owner) {
            _state.update {
                it.copy(
                    changePhase = TierChangePhase.Idle,
                    changeSuccessName = null,
                    changeError = null,
                )
            }
        }
    }

    private fun loadTierData() {
        if (loadJob?.isActive == true) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob = sessionJobs.launch {
            loadTierDataNow(owner)
        }
    }

    private suspend fun loadTierDataNow(
        owner: AuthenticatedSessionOwner,
        prefetchedTier: CustomerTier? = null,
    ): Boolean = coroutineScope {
        if (
            !sessionBoundary.apply(owner) {
                _state.update {
                    it.copy(
                        catalogStatus = TierCatalogStatus.Loading,
                        resolutionStatus = TierResolutionStatus.Loading,
                    )
                }
            }
        ) return@coroutineScope false

        val catalog = async { tierReader.serviceTiers() }
        val customer = async {
            prefetchedTier?.let { Result.success(it) } ?: tierReader.customerTier()
        }
        val fallback = async { runCatching { fallbackTierNameReader.read() }.getOrNull() }

        val catalogResult = catalog.await()
        val customerResult = customer.await()
        val fallbackName = fallback.await()
        val resolvedIndex = indexForTier(
            code = customerResult.getOrNull()?.currentTier,
            name = fallbackName,
        )
        val customerTier = customerResult.getOrNull()

        sessionBoundary.apply(owner) {
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
                    resolutionStatus = if (resolvedIndex != null) {
                        TierResolutionStatus.Resolved
                    } else {
                        TierResolutionStatus.Failed
                    },
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
        }.filterNot { row ->
            if (code != "RUBY") return@filterNot false
            val key = normalizedBenefit(row)
            key == "no free storage included" || key.contains("3-5 business day")
        }.distinctBy(::normalizedBenefit)
        code to rows
    }.toMap()

private fun normalizedBenefit(value: String): String =
    value.lowercase()
        .replace('–', '-')
        .replace('—', '-')
        .trim()
        .replace(Regex("\\s+"), " ")
        .removeSuffix(".")

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
