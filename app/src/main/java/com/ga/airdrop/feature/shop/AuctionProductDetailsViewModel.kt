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
    /** null = no dialog; otherwise the add-to-cart outcome to surface. */
    val cartOutcome: CartAddOutcome? = null,
)

/** Swift onAddToCart outcomes — distinguishes unavailable from already-added. */
enum class CartAddOutcome { ADDED, ALREADY_IN_CART, UNAVAILABLE }

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
        // Swift parity (VERIFICATION_LEDGER P1): the pushing list hands the
        // tapped product over — no featured show endpoint exists, so the slug
        // re-fetch 404s for every featured product. Deep links (no hand-off)
        // keep the network path below.
        ShopProductHandoffStore.consume(slug)?.let { handed ->
            _state.update { it.copy(product = handed, loading = false, error = null) }
            if (!featured) loadRelated()
            return
        }
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
        // Swift adds qty 1 — Stripe hosted checkout charges a single unit,
        // so storing the stepper qty inflated the cart's Order Total.
        val line = product.toCartLine(qty = 1)
        // Swift canAddProductToCart: a product with no linked package can't be
        // added — say so instead of the misleading "Already in cart".
        val outcome = when {
            !line.isEligibleForNewCartAdd() -> CartAddOutcome.UNAVAILABLE
            CartStore.add(line) -> CartAddOutcome.ADDED
            else -> CartAddOutcome.ALREADY_IN_CART
        }
        _state.update { it.copy(cartOutcome = outcome) }
    }

    fun dismissDialog() {
        _state.update { it.copy(cartOutcome = null) }
    }
}
