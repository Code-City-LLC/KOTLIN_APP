package com.ga.airdrop.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.feature.cart.CartStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductDetailsUiState(
    val product: ShopProduct? = null,
    val related: List<ShopProduct> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val quantity: Int = 1,
    val expanded: Boolean = false,
    /** null = no dialog; true = "Added to cart"; false = "Already in cart". */
    val addedDialog: Boolean? = null,
)

/**
 * Auction/Feature product details — behavior from
 * FigmaAuctionProductDetailsViewController. The Android route carries the
 * slug (+ featured flag) and the product is fetched, unlike Swift which
 * receives the object from the pushing screen.
 */
class AuctionProductDetailsViewModel(
    private val slug: String,
    val featured: Boolean,
    private val products: ShopProductsRepository = ShopRepoProvider.products,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductDetailsUiState())
    val state: StateFlow<ProductDetailsUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // RECONCILE: GET /products/{slug} (featured → /featured-products/{slug});
            // fallback GET /products?slug={slug}&page=1&per_page=1 → first item.
            products.productBySlug(slug, featured)
                .onSuccess { product ->
                    _state.update { it.copy(product = product, loading = false) }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, error = err.message ?: "Product unavailable")
                    }
                }
            if (!featured) loadRelated()
        }
    }

    /** No related endpoint exists (Swift renders skeletons); the closest
     *  real data is the newest auction shortlist, excluding this product. */
    private fun loadRelated() {
        viewModelScope.launch {
            products.auctionProducts(page = 1, perPage = 4).onSuccess { list ->
                _state.update { s ->
                    s.copy(related = list.filter { it.routeSlug != slug }.take(2))
                }
            }
        }
    }

    /** Clamped to 1..inventory like Swift changeQuantity. */
    fun changeQuantity(delta: Int) {
        _state.update {
            val stock = it.product?.inventory ?: Int.MAX_VALUE
            it.copy(quantity = (it.quantity + delta).coerceIn(1, maxOf(1, stock)))
        }
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    fun addToCart() {
        val product = _state.value.product ?: return
        val added = CartStore.add(product.toCartLine(qty = _state.value.quantity))
        _state.update { it.copy(addedDialog = added) }
    }

    fun dismissDialog() {
        _state.update { it.copy(addedDialog = null) }
    }
}
