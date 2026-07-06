package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Client-side shipment-type filter values (FigmaPackagesViewController). */
enum class ShipmentTypeFilter(val label: String) {
    All("All"), Standard("Standard"), Seadrop("Seadrop"), Express("Express");

    fun matches(pkg: ShipmentPackage): Boolean {
        if (this == All) return true
        val method = pkg.shippingMethod.orEmpty().lowercase(Locale.US).replace(" ", "")
        return method.contains(name.lowercase(Locale.US))
    }
}

data class PackagesUiState(
    val items: List<ShipmentPackage> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val searchText: String = "",
    val statusFilter: Int = 0, // 0 = All
    val methodFilter: ShipmentTypeFilter = ShipmentTypeFilter.All,
    val statuses: List<PackageStatusInfo> = ShipmentStatusCatalog.defaults,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val showFilterSheet: Boolean = false,
    val error: String? = null,
) {
    /** Rows after client-side search + shipment-type filters (Swift parity). */
    val visibleItems: List<ShipmentPackage>
        get() {
            val query = searchText.trim().lowercase(Locale.US)
            return items.filter { pkg ->
                methodFilter.matches(pkg) && (
                    query.isEmpty() ||
                        pkg.trackingCode.orEmpty().lowercase(Locale.US).contains(query) ||
                        pkg.courierNumber.orEmpty().lowercase(Locale.US).contains(query) ||
                        pkg.description.orEmpty().lowercase(Locale.US).contains(query)
                    )
            }
        }
}

/**
 * Packages list — FigmaPackagesViewController: GET /packages paginated
 * (perPage 15), server status filter + search, client-side shipment-type
 * filter, infinite scroll, cart toggles.
 */
class PackagesViewModel(
    private val repo: ShipmentsPackagesRepository = ShipmentsRepoProvider.packages,
    private val hubRepo: ShipmentsHubRepository = ShipmentsRepoProvider.hub,
) : ViewModel() {

    companion object {
        const val PER_PAGE = 15
    }

    private val _state = MutableStateFlow(PackagesUiState())
    val state: StateFlow<PackagesUiState> = _state

    private var currentPage = 1
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            hubRepo.exchangeRate().onSuccess { rate ->
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        viewModelScope.launch {
            repo.packageStatuses().onSuccess { statuses ->
                if (statuses.isNotEmpty()) {
                    _state.update { it.copy(statuses = statuses.sortedBy { s -> s.order }) }
                }
            }
        }
        load(reset = true)
    }

    fun onSearchTextChange(text: String) {
        _state.update { it.copy(searchText = text) }
    }

    /** Return-key: re-query the server with the search term (Swift parity). */
    fun onSearchSubmit() = load(reset = true)

    fun openFilterSheet() = _state.update { it.copy(showFilterSheet = true) }

    fun closeFilterSheet() {
        _state.update { it.copy(showFilterSheet = false) }
    }

    /** Filters apply immediately on tap; tapping the active one clears it. */
    fun selectStatus(statusId: Int) {
        val next = if (_state.value.statusFilter == statusId) 0 else statusId
        _state.update { it.copy(statusFilter = next) }
        load(reset = true)
    }

    fun selectMethod(filter: ShipmentTypeFilter) {
        val next = if (_state.value.methodFilter == filter) ShipmentTypeFilter.All else filter
        _state.update { it.copy(methodFilter = next) }
        load(reset = true)
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMorePages) return
        load(reset = false)
    }

    fun refresh() = load(reset = true)

    fun toggleCart(pkg: ShipmentPackage) {
        // Swift FigmaPackagesViewController:715-731 — shared cart toggle;
        // the screen observes CartStore.items, so no state write is needed.
        if (!packageCanAddToCart(pkg)) return
        com.ga.airdrop.feature.cart.CartStore.toggle(pkg.toCartLine())
    }

    private fun load(reset: Boolean) {
        if (reset) {
            currentPage = 1
            loadJob?.cancel()
        }
        val requestedPage = currentPage
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    hasMorePages = if (reset) true else it.hasMorePages,
                    error = null,
                )
            }
            val s = _state.value
            repo.packages(
                page = requestedPage,
                perPage = PER_PAGE,
                status = s.statusFilter.takeIf { it != 0 },
                search = s.searchText.trim().takeIf { it.isNotEmpty() },
            ).onSuccess { batch ->
                _state.update { current ->
                    val merged = if (reset) batch else {
                        val known = current.items.map { it.id }.toHashSet()
                        current.items + batch.filter { it.id !in known }
                    }
                    current.copy(
                        items = merged,
                        loading = false,
                        loadingMore = false,
                        hasMorePages = batch.size >= PER_PAGE,
                    )
                }
                currentPage = requestedPage + 1
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = e.message)
                }
            }
        }
    }
}
