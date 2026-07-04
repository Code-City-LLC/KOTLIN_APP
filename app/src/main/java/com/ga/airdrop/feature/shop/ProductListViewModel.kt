package com.ga.airdrop.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductListUiState(
    val query: String = "",
    val products: List<ShopProduct> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasMore: Boolean = true,
    val sort: ShopSort = ShopSort.ALL,
    val showSortSheet: Boolean = false,
)

/**
 * Shared behavior of FigmaAuctionViewController /
 * FigmaFeatureProductsViewController: paginated 2-column grid (page size 10),
 * pull-to-refresh, infinite scroll (trigger 4 rows from the end), debounced
 * search (400 ms, >= 3 chars or cleared), local sort sheet.
 */
open class ProductListViewModel(
    private val featured: Boolean,
    private val products: ShopProductsRepository = ShopRepoProvider.products,
) : ViewModel() {

    private companion object {
        const val PER_PAGE = 10
    }

    private val _state = MutableStateFlow(ProductListUiState())
    val state: StateFlow<ProductListUiState> = _state

    private var currentPage = 1
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage(refreshing: Boolean = false) {
        loadJob?.cancel()
        currentPage = 1
        fetchPage(page = 1, append = false, refreshing = refreshing)
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.loading || s.refreshing || !s.hasMore || s.products.isEmpty()) return
        fetchPage(page = currentPage + 1, append = true, refreshing = false)
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && trimmed.length < 3) return
        searchJob = viewModelScope.launch {
            delay(400) // Swift auction/featured debounce
            loadFirstPage()
        }
    }

    fun onSearchSubmit() {
        searchJob?.cancel()
        loadFirstPage()
    }

    fun setSortSheetVisible(visible: Boolean) {
        _state.update { it.copy(showSortSheet = visible) }
    }

    /** Swift sortProducts: local re-sort of the loaded pages. */
    fun applySort(sort: ShopSort) {
        _state.update {
            it.copy(
                sort = sort,
                showSortSheet = false,
                products = when (sort) {
                    ShopSort.ALL -> it.products
                    ShopSort.PRICE_ASC -> it.products.sortedBy { p -> p.priceUsd }
                    ShopSort.PRICE_DESC -> it.products.sortedByDescending { p -> p.priceUsd }
                    ShopSort.NEWEST -> it.products.sortedByDescending { p -> p.createdAt.orEmpty() }
                },
            )
        }
    }

    private fun searchQuery(): String? =
        _state.value.query.trim().takeIf { it.length >= 3 }

    private fun fetchPage(page: Int, append: Boolean, refreshing: Boolean) {
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, refreshing = refreshing) }
            // RECONCILE: GET /products (auction) or GET /featured-products
            // (featured) — page, per_page=10, search?, order=created_at,
            // direction=desc, in_stock=1 (Swift AuctionView ProductFilters).
            val result = if (featured) {
                products.featuredProducts(page = page, perPage = PER_PAGE, search = searchQuery())
            } else {
                products.auctionProducts(page = page, perPage = PER_PAGE, search = searchQuery())
            }
            val fetched = result.getOrElse { emptyList() }
            currentPage = page
            _state.update {
                it.copy(
                    // Dedupe by id so repeated server rows can't break
                    // LazyGrid keys across pages.
                    products = if (append) {
                        (it.products + fetched).distinctBy { p -> p.id }
                    } else {
                        fetched.distinctBy { p -> p.id }
                    },
                    loading = false,
                    refreshing = false,
                    // Swift: no more pages when a fetch returns < perPage.
                    hasMore = fetched.size >= PER_PAGE,
                )
            }
        }
    }
}

class AuctionViewModel : ProductListViewModel(featured = false)

class FeaturedProductsViewModel : ProductListViewModel(featured = true)
