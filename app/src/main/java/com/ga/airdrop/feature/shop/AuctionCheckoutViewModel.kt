package com.ga.airdrop.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuctionCheckoutUiState(
    val product: ShopProduct? = null,
    val currency: String = "USD",
    // Shared fallback rate (FuchsiaTower Pass-4 C6).
    val exchangeUsdToJmd: Double = com.ga.airdrop.feature.shipments.DEFAULT_USD_TO_JMD,
    val paying: Boolean = false,
    /** Deep-link product resolve in flight (Audit#7 C4). */
    val resolvingProduct: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    /** Stripe hosted checkout URL to open in a Custom Tab (one-shot). */
    val checkoutUrl: String? = null,
)

/**
 * Auction pre-checkout review — behavior from
 * FigmaAuctionProductCheckoutViewController: hero card, Our Promise, USD/JMD
 * picker, sticky total + "Continue to pay" → POST /payments/create-checkout
 * (is_auction=true) → Stripe hosted checkout in a Custom Tab.
 *
 * The route carries no argument (Swift parity) — the product is handed over
 * via [ShopCheckoutStore].
 */
class AuctionCheckoutViewModel(
    private val checkout: ShopCheckoutRepository = ShopRepoProvider.checkout,
    private val products: ShopProductsRepository = ShopRepoProvider.products,
) : ViewModel() {

    val currencyOptions = listOf("USD", "JMD")

    private val _state = MutableStateFlow(
        AuctionCheckoutUiState(
            product = ShopCheckoutStore.product,
            // Seed from the persisted rate like every sibling VM (CartViewModel
            // et al.) — otherwise the JMD total paints at the hardcoded default
            // until /exchange-rates answers, and stays wrong if it fails.
            exchangeUsdToJmd = com.ga.airdrop.core.prefs.ExchangeRateStore.current,
        )
    )
    val state: StateFlow<AuctionCheckoutUiState> = _state

    init {
        // Deep-link fallback (B4): Swift FigmaRouteViewController:727 pushes
        // the checkout VC with product nil + an id/slug ref and the VC
        // resolves it. Mirror: no handed-over product but a pending ref →
        // fetch it before rendering "Product unavailable".
        if (_state.value.product == null) {
            ShopCheckoutStore.pendingRef?.let { pendingRef ->
                ShopCheckoutStore.pendingRef = null
                _state.update { it.copy(resolvingProduct = true) }
                viewModelScope.launch {
                    products.productBySlug(pendingRef, featured = false)
                        .onSuccess { resolved ->
                            _state.update { it.copy(product = resolved, resolvingProduct = false) }
                        }
                        .onFailure {
                            // Without this the screen kept a phantom "$0.00
                            // Auction Product" card forever (Audit#7 C4).
                            _state.update {
                                it.copy(
                                    resolvingProduct = false,
                                    errorTitle = "Product unavailable",
                                    errorMessage = "This product could not be loaded. Please try again from the shop.",
                                )
                            }
                        }
                }
            }
        }
        viewModelScope.launch {
            // RECONCILE: GET /exchange-rates → { usd_to_jmd } (no auth).
            checkout.exchangeRate().onSuccess { rate ->
                if (rate > 0) {
                    com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                    _state.update { it.copy(exchangeUsdToJmd = rate) }
                }
            }
        }
    }

    fun setCurrency(value: String) {
        _state.update { it.copy(currency = value) }
    }

    fun totalLabel(): String {
        val s = _state.value
        val usd = s.product?.priceUsd ?: 0.0
        // Swift renderTotal: USD "$X.XX", JMD " JA$X.XX".
        return if (s.currency == "USD") formatUsd(usd) else " " + formatJmd(usd * s.exchangeUsdToJmd)
    }

    fun pay() {
        val s = _state.value
        val product = s.product
        if (product == null) {
            _state.update {
                it.copy(
                    errorTitle = "Product unavailable",
                    errorMessage = "No product was loaded for this checkout.",
                )
            }
            return
        }
        val packageId = product.packageId
        if (packageId == null) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "This product is missing the package ID required for sale checkout.",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(paying = true) }
            // RECONCILE: POST /payments/create-checkout
            // { package_ids: [packageId], currency, is_auction: true } →
            // data.checkout_url (Stripe hosted checkout).
            checkout.createCheckout(listOf(packageId), s.currency, isAuction = true)
                .onSuccess { url ->
                    _state.update { it.copy(paying = false, checkoutUrl = url) }
                }
                .onFailure { err ->
                    val unauthenticated = err.isUnauthenticatedCheckoutFailure()
                    _state.update {
                        it.copy(
                            paying = false,
                            errorTitle = if (unauthenticated) {
                                "Sign in required"
                            } else {
                                "Checkout failed"
                            },
                            errorMessage = if (unauthenticated) {
                                "Log in to your Airdropja account before checking out."
                            } else {
                                err.toUserMessage().ifBlank { "Stripe did not return a valid checkout URL." }
                            },
                        )
                    }
                }
        }
    }

    fun consumeCheckoutUrl() {
        _state.update { it.copy(checkoutUrl = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }
}
