package com.ga.airdrop.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopUiState(
    val query: String = "",
    val auction: List<ShopProduct> = emptyList(),
    val featured: List<ShopProduct> = emptyList(),
    val auctionLoading: Boolean = true,
    val featuredLoading: Boolean = true,
    val sort: ShopSort = ShopSort.ALL,
    val showSortSheet: Boolean = false,
    /** Swift §C.7 — the last 5 explicitly-submitted queries, newest first. */
    val recentSearches: List<String> = emptyList(),
)

/**
 * Shop tab root — behavior from FigmaShopViewController + RN ShopView:
 * auction shortlist (4) + featured shortlist (4), search with a 500 ms
 * debounce (RN parity — the known Swift gap, implemented properly here),
 * local sort via the filter sheet.
 */
class ShopViewModel(
    private val products: ShopProductsRepository = ShopRepoProvider.products,
) : ViewModel() {

    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state

    private var searchJob: Job? = null

    /** Unsorted server order kept so ShopSort.ALL can restore it. */
    private var auctionOriginal: List<ShopProduct> = emptyList()
    private var featuredOriginal: List<ShopProduct> = emptyList()

    init {
        _state.update { it.copy(recentSearches = ShopRecentSearches.read()) }
        refresh()
    }

    fun refresh() {
        loadAuction()
        loadFeatured()
    }

    /** RN parity: 500 ms debounce; fires when cleared or >= 3 chars. */
    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && trimmed.length < 3) return
        searchJob = viewModelScope.launch {
            delay(500)
            refresh()
        }
    }

    fun onSearchSubmit() {
        searchJob?.cancel()
        // Swift textFieldShouldReturn §C.7: persist the explicitly-submitted
        // query (>= 3 chars — same floor as searchQuery()) into the ring so
        // the recents chips can offer it next time.
        val q = _state.value.query.trim()
        if (q.length >= 3) {
            ShopRecentSearches.save(q)
            _state.update { it.copy(recentSearches = ShopRecentSearches.read()) }
        }
        refresh()
    }

    /** Chip tap — Swift accessory chip action: set the query and re-search. */
    fun onRecentSearchSelected(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(query = query) }
        refresh()
    }

    fun setSortSheetVisible(visible: Boolean) {
        _state.update { it.copy(showSortSheet = visible) }
    }

    /** Swift applyProductSort: re-sorts both lists locally. */
    fun applySort(sort: ShopSort) {
        _state.update {
            it.copy(
                sort = sort,
                showSortSheet = false,
                auction = sortList(auctionOriginal, sort),
                featured = sortList(featuredOriginal, sort),
            )
        }
    }

    private fun searchQuery(): String? =
        _state.value.query.trim().takeIf { it.length >= 3 }

    private fun loadAuction() {
        viewModelScope.launch {
            _state.update { it.copy(auctionLoading = true) }
            // RECONCILE: GET /products?page=1&per_page=4&order=created_at&
            // direction=desc&in_stock=1[&search=] (Swift auctionProductsShortlist)
            val result = products.auctionProducts(page = 1, perPage = 4, search = searchQuery())
            val items = result.getOrElse { emptyList() }
            auctionOriginal = items
            _state.update {
                it.copy(auction = sortList(items, it.sort), auctionLoading = false)
            }
        }
    }

    private fun loadFeatured() {
        viewModelScope.launch {
            _state.update { it.copy(featuredLoading = true) }
            // RECONCILE: GET /featured-products?page=1&per_page=4&order=created_at&
            // direction=desc[&search=&in_stock=1&on_sale=1] (Swift featuredProductsShortlist)
            val result = products.featuredProducts(page = 1, perPage = 4, search = searchQuery())
            val items = result.getOrElse { emptyList() }
            featuredOriginal = items
            _state.update {
                it.copy(featured = sortList(items, it.sort).take(10), featuredLoading = false)
            }
        }
    }

    private fun sortList(list: List<ShopProduct>, sort: ShopSort): List<ShopProduct> = when (sort) {
        ShopSort.ALL -> list
        ShopSort.PRICE_ASC -> list.sortedBy { it.priceUsd }
        ShopSort.PRICE_DESC -> list.sortedByDescending { it.priceUsd }
        ShopSort.NEWEST -> list.sortedByDescending { it.createdAt.orEmpty() }
    }
}
