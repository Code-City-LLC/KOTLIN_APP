package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.DataCartServerGateway
import com.ga.airdrop.feature.cart.PackageCartMutationCoordinator
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
    val showSortSheet: Boolean = false,
    /** §B.4 user-selected sort, persisted via [PackagesSortStore]. */
    val sort: PackagesSort = PackagesSort.NEWEST_FIRST,
    val error: String? = null,
) {
    /** Rows after client-side search + shipment-type filters, then the §B.4
     *  sort (Swift reapplySearchFilter → applySortedOrder). */
    val visibleItems: List<ShipmentPackage>
        get() {
            val query = searchText.trim().lowercase(Locale.US)
            val filtered = items.filter { pkg ->
                methodFilter.matches(pkg) && (
                    query.isEmpty() ||
                        pkg.trackingCode.orEmpty().lowercase(Locale.US).contains(query) ||
                        pkg.courierNumber.orEmpty().lowercase(Locale.US).contains(query) ||
                        pkg.description.orEmpty().lowercase(Locale.US).contains(query)
                    )
            }
            return sortPackages(filtered, sort)
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
    cartServer: CartServerGateway = DataCartServerGateway(),
    sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    companion object {
        const val PER_PAGE = 15
        /** Swift c108581 — server search fires after a 500ms typing pause. */
        const val SEARCH_DEBOUNCE_MS = 500L
    }

    // §B.4: the saved sort survives launches (Swift currentSort seed).
    private val _state = MutableStateFlow(
        PackagesUiState(sort = PackagesSortStore.read(), exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<PackagesUiState> = _state

    private var currentPage = 1
    private var loadJob: Job? = null
    private var searchDebounceJob: Job? = null
    private val cartMutations = PackageCartMutationCoordinator(cartServer, sessionBoundary)

    init {
        viewModelScope.launch {
            hubRepo.exchangeRate().onSuccess { rate ->
                com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
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
        // Swift c108581 (RN usePagination parity): the instant local filter
        // above stays per-keystroke; a 500ms-debounced SERVER refetch surfaces
        // packages not yet paged in. Each keystroke cancels the prior job.
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            load(reset = true)
        }
    }

    /** Return-key: cancel the pending debounce and re-query immediately. */
    fun onSearchSubmit() {
        searchDebounceJob?.cancel()
        load(reset = true)
    }

    fun openFilterSheet() = _state.update { it.copy(showFilterSheet = true) }

    fun closeFilterSheet() {
        _state.update { it.copy(showFilterSheet = false) }
    }

    fun openSortSheet() = _state.update { it.copy(showSortSheet = true) }

    fun closeSortSheet() = _state.update { it.copy(showSortSheet = false) }

    /** §B.4 applySort: persist the choice and re-sort the visible list. */
    fun applySort(sort: PackagesSort) {
        PackagesSortStore.save(sort)
        _state.update { it.copy(sort = sort, showSortSheet = false) }
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
        cartMutations.toggle(
            line = pkg.toCartLine(),
            scope = viewModelScope,
            onFailure = { message -> _state.update { it.copy(error = message) } },
        )
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
                // Server-side method filter (Swift loadPackages parity) — the
                // client-side visibleItems filter alone paginated wrong pages.
                shippingMethod = s.methodFilter.takeIf { it != ShipmentTypeFilter.All }?.name,
            ).onSuccess { paged ->
                val batch = paged.items
                _state.update { current ->
                    val merged = if (reset) batch else {
                        val known = current.items.map { it.id }.toHashSet()
                        current.items + batch.filter { it.id !in known }
                    }
                    current.copy(
                        items = merged,
                        loading = false,
                        loadingMore = false,
                        hasMorePages = paged.isLastPage?.let { last -> !last }
                            ?: (batch.size >= PER_PAGE),
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
