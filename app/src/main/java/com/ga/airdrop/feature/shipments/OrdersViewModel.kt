package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val items: List<ShipmentOrder> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val searchText: String = "",
    /** Last load failed — shared failure card shows only when items empty. */
    val error: String? = null,
)

/**
 * Orders list — FigmaOrdersViewController: GET /orders paginated (perPage 10),
 * 300ms debounced search (min 3 chars), infinite scroll.
 */
class OrdersViewModel(
    private val repo: ShipmentsOrdersRepository = ShipmentsRepoProvider.orders,
) : ViewModel() {

    companion object {
        const val PER_PAGE = 10
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_MIN_CHARS = 3
    }

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state

    private var currentPage = 1
    private var loadJob: kotlinx.coroutines.Job? = null
    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    fun onSearchTextChange(text: String) {
        _state.update { it.copy(searchText = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(reset = true)
        }
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMorePages) return
        load(reset = false)
    }

    fun refresh() = load(reset = true)

    private fun load(reset: Boolean) {
        if (reset) { currentPage = 1; loadJob?.cancel() }
        val requestedPage = currentPage
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    // Retry re-enters loading cleanly — no stale failure card.
                    error = null,
                    // A failed reset load must not leave a stale end-of-list
                    // gate (FuchsiaTower Pass-3b C1; matches PackagesViewModel).
                    hasMorePages = if (reset) true else it.hasMorePages,
                )
            }
            val search = _state.value.searchText.trim().takeIf { it.length >= SEARCH_MIN_CHARS }
            repo.orders(page = requestedPage, perPage = PER_PAGE, search = search)
                .onSuccess { paged ->
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
                            // Prefer the server's page verdict; fall back to the
                            // batch-size heuristic on metadata-less envelopes.
                            hasMorePages = paged.isLastPage?.let { last -> !last }
                                ?: (batch.size >= PER_PAGE),
                        )
                    }
                    currentPage = requestedPage + 1
                }
                .onFailure { err ->
                    // Swift 89fbb11: record the failure so OrdersScreen can
                    // show the shared LoadFailureCard — but only when no rows
                    // are loaded; cached rows always win over the card.
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            error = err.message ?: "Unable to load orders.",
                        )
                    }
                }
        }
    }
}
