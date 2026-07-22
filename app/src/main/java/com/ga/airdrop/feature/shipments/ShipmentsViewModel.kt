package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.push.PackageDeepLinkReference
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.DataCartServerGateway
import com.ga.airdrop.feature.cart.PackageCartMutationCoordinator
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Swift default before /exchange-rate resolves. */
const val DEFAULT_USD_TO_JMD = 160.625

data class ShipmentsUiState(
    val loading: Boolean = false,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val summary: ShipmentsSummary = ShipmentsSummary(),
    val packages: List<ShipmentPackage> = emptyList(),
    val payments: List<ShipmentPayment> = emptyList(),
    val orders: List<ShipmentOrder> = emptyList(),
    val error: String? = null,
)

data class QuickTrackRecent(
    val code: String,
    val packageId: Int? = null,
    val trackingCode: String? = null,
    val description: String? = null,
    val statusName: String? = null,
)

data class QuickTrackUiState(
    val visible: Boolean = false,
    val code: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val recents: List<QuickTrackRecent> = emptyList(),
)

/**
 * Shipments hub — FigmaShipmentsViewController: summary tiles, packages
 * shortlist (10), payments shortlist (4), orders shortlist (6).
 */
class ShipmentsViewModel(
    private val repo: ShipmentsHubRepository = ShipmentsRepoProvider.hub,
    private val packagesRepo: ShipmentsPackagesRepository = ShipmentsRepoProvider.packages,
    cartServer: CartServerGateway = DataCartServerGateway(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ShipmentsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<ShipmentsUiState> = _state
    private val _quickTrack = MutableStateFlow(QuickTrackUiState())
    val quickTrack: StateFlow<QuickTrackUiState> = _quickTrack
    private var refreshJob: Job? = null
    private var quickTrackJob: Job? = null
    private var quickTrackGeneration = 0L
    private var observedSessionId = sessionBoundary.capture()?.sessionId
    private val cartMutations = PackageCartMutationCoordinator(cartServer, sessionBoundary)

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                if (changed?.sessionId == observedSessionId) return@collect
                observedSessionId = changed?.sessionId
                quickTrackGeneration += 1
                quickTrackJob?.cancel()
                _quickTrack.value = QuickTrackUiState()
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.exchangeRate().onSuccess { rate ->
                com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                _state.update { it.copy(exchangeRate = rate) }
            }
            repo.summary().onSuccess { summary ->
                _state.update { it.copy(summary = summary) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
            repo.packagesShortlist().onSuccess { packages ->
                _state.update { it.copy(packages = packages.take(10)) }
            }
            repo.paymentsShortlist().onSuccess { payments ->
                _state.update { it.copy(payments = payments.take(4)) }
            }
            repo.ordersShortlist().onSuccess { orders ->
                _state.update { it.copy(orders = orders.take(6)) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun toggleCart(pkg: ShipmentPackage) {
        cartMutations.toggle(
            line = pkg.toCartLine(),
            scope = viewModelScope,
            onFailure = { message -> _state.update { it.copy(error = message) } },
        )
    }

    fun openQuickTrack() {
        _quickTrack.update { it.copy(visible = true, error = null) }
    }

    fun dismissQuickTrack() {
        quickTrackGeneration += 1
        quickTrackJob?.cancel()
        _quickTrack.update { it.copy(visible = false, loading = false, error = null) }
    }

    fun updateQuickTrackCode(code: String) {
        _quickTrack.update {
            it.copy(code = code.uppercase(Locale.US), error = null)
        }
    }

    fun submitQuickTrack(
        overrideCode: String? = null,
        onResolved: (Int) -> Unit,
    ) {
        if (quickTrackJob?.isActive == true) return
        val rawCode = overrideCode ?: _quickTrack.value.code
        val routeReference = PackageDeepLinkReference.routeReference(rawCode)
        if (routeReference == null) {
            _quickTrack.update { it.copy(error = "Enter a tracking number to continue.") }
            return
        }
        if (
            PackageDeepLinkReference.positiveId(routeReference) == null &&
            routeReference.length < MIN_PACKAGE_ALIAS_LENGTH
        ) {
            _quickTrack.update { it.copy(error = "Enter at least 3 characters to track a package.") }
            return
        }
        val owner = sessionBoundary.capture()?.takeIf(sessionBoundary::isCurrent)
        if (owner == null || owner.sessionId != observedSessionId) {
            _quickTrack.update {
                it.copy(loading = false, error = "Sign in to look up tracking codes against your account.")
            }
            return
        }
        val code = routeReference.uppercase(Locale.US)
        val generation = ++quickTrackGeneration
        PackageDeepLinkReference.positiveId(routeReference)?.let { directPackageId ->
            applyQuickTrackRequest(owner, generation) {
                _quickTrack.update {
                    it.copy(
                        visible = false,
                        code = "",
                        loading = false,
                        error = null,
                        recents = it.recents.withResolvedPackageId(code, directPackageId),
                    )
                }
                onResolved(directPackageId)
            }
            return
        }
        _quickTrack.update { it.copy(code = code, loading = true, error = null) }
        quickTrackJob = viewModelScope.launch {
            runCatching {
                val searchResults = packageReferenceSearchRows(
                    repository = packagesRepo,
                    alias = code,
                    isRequestCurrent = { isQuickTrackRequestCurrent(owner, generation) },
                )
                    .getOrThrow()
                if (!isQuickTrackRequestCurrent(owner, generation)) return@runCatching null
                val shortlist = repo.packagesShortlist().getOrDefault(emptyList())
                if (!isQuickTrackRequestCurrent(owner, generation)) return@runCatching null
                exactPackageReferenceMatch(searchResults + shortlist, code)
            }.onSuccess { match ->
                if (!isQuickTrackRequestCurrent(owner, generation)) return@onSuccess
                if (match != null) {
                    applyQuickTrackRequest(owner, generation) {
                        _quickTrack.update {
                            it.copy(
                                visible = false,
                                code = "",
                                loading = false,
                                error = null,
                                recents = it.recents.withRecent(code, match),
                            )
                        }
                        onResolved(match.id)
                    }
                } else {
                    applyQuickTrackRequest(owner, generation) {
                        _quickTrack.update {
                            it.copy(
                                loading = false,
                                error = "No package found for $code. Check the code and try again.",
                                recents = it.recents.withRecent(code, null),
                            )
                        }
                    }
                }
            }.onFailure { error ->
                applyQuickTrackRequest(owner, generation) {
                    _quickTrack.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "Unable to look up tracking code.",
                        )
                    }
                }
            }
        }
    }

    private fun isQuickTrackRequestCurrent(
        owner: AuthenticatedSessionOwner,
        generation: Long,
    ): Boolean =
        generation == quickTrackGeneration &&
            owner.sessionId == observedSessionId &&
            sessionBoundary.isCurrent(owner)

    private fun applyQuickTrackRequest(
        owner: AuthenticatedSessionOwner,
        generation: Long,
        action: () -> Unit,
    ): Boolean {
        if (!isQuickTrackRequestCurrent(owner, generation)) return false
        var ran = false
        val accepted = sessionBoundary.apply(owner) {
            if (generation == quickTrackGeneration && owner.sessionId == observedSessionId) {
                action()
                ran = true
            }
        }
        return accepted && ran
    }

    private fun List<QuickTrackRecent>.withRecent(
        code: String,
        pkg: ShipmentPackage?,
    ): List<QuickTrackRecent> {
        val recent = QuickTrackRecent(
            code = code,
            packageId = pkg?.id,
            trackingCode = pkg?.trackingCode,
            description = pkg?.description,
            statusName = pkg?.statusName,
        )
        return (listOf(recent) + filterNot { it.code == code }).take(5)
    }

    private fun List<QuickTrackRecent>.withResolvedPackageId(
        code: String,
        packageId: Int,
    ): List<QuickTrackRecent> {
        val recent = QuickTrackRecent(code = code, packageId = packageId)
        return (listOf(recent) + filterNot { it.code == code }).take(5)
    }
}
