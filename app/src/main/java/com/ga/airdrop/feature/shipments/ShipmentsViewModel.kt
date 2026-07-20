package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.DataCartServerGateway
import com.ga.airdrop.feature.cart.PackageCartMutationCoordinator
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    // Per-section shortlist failures (Swift 89fbb11): each drives the shared
    // LoadFailureCard only while that section has nothing loaded — cached
    // rows always win over the card.
    val packagesFailed: Boolean = false,
    val paymentsFailed: Boolean = false,
    val ordersFailed: Boolean = false,
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
    sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ShipmentsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<ShipmentsUiState> = _state
    private val _quickTrack = MutableStateFlow(QuickTrackUiState())
    val quickTrack: StateFlow<QuickTrackUiState> = _quickTrack
    private var refreshJob: Job? = null
    private var quickTrackJob: Job? = null
    private val cartMutations = PackageCartMutationCoordinator(cartServer, sessionBoundary)

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    packagesFailed = false,
                    paymentsFailed = false,
                    ordersFailed = false,
                )
            }
            // Speed pass (Kemar "app must be faster"): the five hub calls
            // were a 5×RTT waterfall; they are independent — fetch them
            // concurrently and apply each as it lands.
            kotlinx.coroutines.coroutineScope {
                val rateD = async { repo.exchangeRate() }
                val summaryD = async { repo.summary() }
                val packagesD = async { repo.packagesShortlist() }
                val paymentsD = async { repo.paymentsShortlist() }
                val ordersD = async { repo.ordersShortlist() }

                rateD.await().onSuccess { rate ->
                    com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                    _state.update { it.copy(exchangeRate = rate) }
                }
                summaryD.await().onSuccess { summary ->
                    _state.update { it.copy(summary = summary) }
                }.onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
                packagesD.await().onSuccess { packages ->
                    _state.update { it.copy(packages = packages.take(10), packagesFailed = false) }
                }.onFailure {
                    // Keep any cached rows — the flag only surfaces the
                    // failure card when the section is empty.
                    _state.update { it.copy(packagesFailed = true) }
                }
                paymentsD.await().onSuccess { payments ->
                    _state.update { it.copy(payments = payments.take(4), paymentsFailed = false) }
                }.onFailure {
                    _state.update { it.copy(paymentsFailed = true) }
                }
                ordersD.await().onSuccess { orders ->
                    _state.update { it.copy(orders = orders.take(6), ordersFailed = false) }
                }.onFailure {
                    _state.update { it.copy(ordersFailed = true) }
                }
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
        val code = (overrideCode ?: _quickTrack.value.code).trim().uppercase(Locale.US)
        if (code.isEmpty()) {
            _quickTrack.update { it.copy(error = "Enter a tracking number to continue.") }
            return
        }
        _quickTrack.update { it.copy(code = code, loading = true, error = null) }
        quickTrackJob = viewModelScope.launch {
            runCatching {
                val searchResults = packagesRepo
                    .packages(page = 1, perPage = 20, status = null, search = code, shippingMethod = null)
                    .getOrThrow()
                    .items
                exactQuickTrackMatch(searchResults, code)
                    ?: exactQuickTrackMatch(repo.packagesShortlist().getOrDefault(emptyList()), code)
            }.onSuccess { match ->
                if (match != null) {
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
                } else {
                    _quickTrack.update {
                        it.copy(
                            loading = false,
                            error = "No package found for $code. Check the code and try again.",
                            recents = it.recents.withRecent(code, null),
                        )
                    }
                }
            }.onFailure { error ->
                _quickTrack.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Unable to look up tracking code.",
                    )
                }
            }
        }
    }

    private fun exactQuickTrackMatch(packages: List<ShipmentPackage>, code: String): ShipmentPackage? =
        packages.firstOrNull { pkg ->
            pkg.trackingCode.matchesQuickTrackCode(code) ||
                pkg.courierNumber.matchesQuickTrackCode(code) ||
                pkg.id.toString() == code
        }

    private fun String?.matchesQuickTrackCode(code: String): Boolean =
        this?.trim()?.uppercase(Locale.US) == code

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
}
