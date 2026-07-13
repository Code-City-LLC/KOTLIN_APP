package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
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
            applyWhileOwned(owner) {
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
            !applyWhileOwned(owner) {
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
            val dispatchOwner = continuedRequestOwner(owner)
            if (dispatchOwner?.provenance != requestOwner.provenance) {
                // A token refresh can rotate request provenance without
                // replacing the logical session. The session collector does
                // not reset for that event, so a silent return here would
                // strand the non-dismissible sheet in Working forever.
                dispatchOwner?.session?.let {
                    finishWorkingWithError(
                        it,
                        "Your session refreshed before the change started. Please try again.",
                    )
                }
                return@launch
            }
            val patchResult = tierChanger.changeTier(
                requestedTierCode = requestedTierCode,
                expectedSession = requestOwner.provenance,
            )
            if (patchResult.isFailure) {
                continuedRequestOwner(owner)?.session?.let {
                    finishWorkingWithError(
                        it,
                        patchResult.exceptionOrNull()?.message
                            ?: "Unable to change tier. Please try again.",
                    )
                }
                return@launch
            }

            // A successful PATCH must still be confirmed when only the token
            // revision rotated inside the same logical session. The GET uses
            // the current token at dispatch. Account/session replacement is
            // still rejected before any confirmation read or publication.
            val confirmationOwner = continuedRequestOwner(owner)?.session ?: return@launch

            val confirmation = tierReader.customerTier()
            // The confirmation response belongs only to the same logical
            // account/session. Revision-only rotations remain valid.
            val publicationOwner = continuedRequestOwner(confirmationOwner)?.session ?: return@launch
            val confirmed = confirmation.getOrNull()
            if (confirmed?.currentTier?.equals(requestedTierCode, ignoreCase = true) == true) {
                loadTierDataNow(
                    owner = publicationOwner,
                    prefetchedTier = confirmed,
                    afterLoad = {
                        it.copy(
                            changePhase = TierChangePhase.Success,
                            changeSuccessName = targetName,
                            changeError = null,
                        )
                    },
                )
            } else {
                applyWhileOwned(publicationOwner) {
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

    private fun finishWorkingWithError(
        owner: AuthenticatedSessionOwner,
        message: String,
    ) {
        applyWhileOwned(owner) {
            _state.update { current ->
                if (current.changePhase != TierChangePhase.Working) current
                else current.copy(
                    changePhase = TierChangePhase.Error,
                    changeSuccessName = null,
                    changeError = message,
                )
            }
        }
    }

    /**
     * Account identity may refine from unknown to bound without replacing the
     * authenticated session. A non-null captured account is immutable for
     * that session, while null may safely adopt the current bound account.
     */
    private fun continuedRequestOwner(
        captured: AuthenticatedSessionOwner,
    ): AuthenticatedRequestOwner? {
        // Validate the originally captured owner first. If a null -> bound
        // bind is still persisting, runWhileCurrent waits for transitionLock:
        // a failed bind rolls back to null and this succeeds, while a committed
        // bind makes the exact-null check fail. Only that committed refinement
        // may be captured and validated once; there is no retry-count race
        // across unrelated provisional bind attempts.
        stableRequestOwner(captured)?.let { return it }
        if (captured.accountId != null) return null

        val refined = sessionBoundary.capture() ?: return null
        if (refined.sessionId != captured.sessionId || refined.accountId == null) return null
        return stableRequestOwner(refined)
    }

    private fun stableRequestOwner(
        owner: AuthenticatedSessionOwner,
    ): AuthenticatedRequestOwner? {
        var requestOwner: AuthenticatedRequestOwner? = null
        val stable = sessionBoundary.runWhileCurrent(owner) {
            requestOwner = sessionBoundary.requestOwner(owner)
            requestOwner != null
        }
        return requestOwner.takeIf { stable }
    }

    private fun applyWhileOwned(
        owner: AuthenticatedSessionOwner,
        action: () -> Unit,
    ): Boolean {
        val ownedAction = {
            action()
            true
        }
        if (
            sessionBoundary.runWhileCurrent(owner, ownedAction)
        ) return true
        // An exact apply can lose only the legal null -> bound refinement
        // between owner resolution and runWhileCurrent(). Retry once on the
        // newly resolved exact owner. Non-null mismatch and replacement
        // sessions are never retried, and the action executes at most once.
        if (owner.accountId != null) return false
        val boundOwner = continuedRequestOwner(owner)?.session ?: return false
        if (boundOwner.accountId == null) return false
        return sessionBoundary.runWhileCurrent(boundOwner, ownedAction)
    }

    fun resetChangeFlow() {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        applyWhileOwned(owner) {
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
        afterLoad: (GoldPriorityUiState) -> GoldPriorityUiState = { it },
    ): Boolean = coroutineScope {
        val loadingOwner = continuedRequestOwner(owner)?.session ?: return@coroutineScope false
        if (
            !applyWhileOwned(loadingOwner) {
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

        val publicationOwner = continuedRequestOwner(loadingOwner)?.session
            ?: return@coroutineScope false
        applyWhileOwned(publicationOwner) {
            _state.update { previous ->
                afterLoad(
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
                    ),
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
