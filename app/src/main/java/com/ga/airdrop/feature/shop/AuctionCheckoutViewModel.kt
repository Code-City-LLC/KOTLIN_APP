package com.ga.airdrop.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.feature.cart.CheckoutCurrency
import com.ga.airdrop.feature.cart.CheckoutFlowStore
import com.ga.airdrop.feature.cart.CheckoutPhase
import com.ga.airdrop.feature.cart.parseCheckoutCurrency
import com.ga.airdrop.feature.cart.validatedHostedCheckoutUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
    /** Kept with the URL until dispatch; never persisted. */
    val checkoutSessionId: String? = null,
    val checkoutLaunchAttempt: Long = 0L,
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
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
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
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        if (_state.value.paying) {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    checkoutUrl = null,
                                    checkoutSessionId = null,
                                    errorTitle = "Checkout interrupted",
                                    errorMessage = "Your account finished loading. Tap Pay again to continue safely.",
                                )
                            }
                        }
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionOwner = changed
                _state.update {
                    it.copy(
                        paying = false,
                        checkoutUrl = null,
                        checkoutSessionId = null,
                        errorTitle = if (changed == null) "Sign in required" else null,
                        errorMessage = if (changed == null) {
                            "Log in to your Airdropja account before checking out."
                        } else {
                            null
                        },
                    )
                }
            }
        }
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
        if (s.paying) return
        if (s.checkoutUrl != null || s.checkoutSessionId != null) {
            val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
            val url = validatedHostedCheckoutUrl(s.checkoutUrl)
            val sessionId = s.checkoutSessionId?.trim()?.takeIf(String::isNotEmpty)
            if (owner != null && url != null && sessionId != null &&
                CheckoutFlowStore.pending(sessionId, owner) != null
            ) {
                _state.update { it.copy(checkoutLaunchAttempt = it.checkoutLaunchAttempt + 1) }
            } else {
                _state.update {
                    it.copy(
                        errorTitle = "Checkout unavailable",
                        errorMessage = "The saved checkout session is invalid. Check Shipments before retrying.",
                    )
                }
            }
            return
        }
        val currentOwner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
        if (currentOwner != null && CheckoutFlowStore.pending(currentOwner) != null) {
            _state.update {
                it.copy(
                    errorTitle = "Payment still pending",
                    errorMessage = "A checkout is already pending. Check Shipments before paying again.",
                )
            }
            return
        }
        if (currentOwner != null && CheckoutFlowStore.creating(currentOwner) != null) {
            _state.update {
                it.copy(
                    errorTitle = "Payment status unknown",
                    errorMessage = "A checkout request may already have reached Stripe. It cannot be retried safely; check Shipments.",
                )
            }
            return
        }
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
        val packageId = product.packageId?.takeIf { it > 0 }
        if (packageId == null || product.id <= 0) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "This product is missing the package ID required for sale checkout.",
                )
            }
            return
        }
        when (parseCheckoutCurrency(s.currency)) {
            CheckoutCurrency.JMD -> {
                _state.update {
                    it.copy(
                        errorTitle = "JMD payment unavailable",
                        errorMessage = "JMD checkout requires NCB PowerTranz, which is not available yet. " +
                            "Choose USD to continue.",
                    )
                }
                return
            }
            null -> {
                _state.update {
                    it.copy(
                        errorTitle = "Checkout unavailable",
                        errorMessage = "Choose USD to continue to secure payment.",
                    )
                }
                return
            }
            CheckoutCurrency.USD -> Unit
        }
        val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
        if (owner == null) {
            _state.update {
                it.copy(
                    errorTitle = "Sign in required",
                    errorMessage = "Log in to your Airdropja account before checking out.",
                )
            }
            return
        }
        _state.update { it.copy(paying = true) }
        var requestOwner: com.ga.airdrop.core.session.AuthenticatedRequestOwner? = null
        var summaryFlow: com.ga.airdrop.feature.cart.CheckoutFlow? = null
        var creation: com.ga.airdrop.feature.cart.PendingCheckoutCreation? = null
        var dispatchNote: String? = null
        val bound = sessionBoundary.runWhileCurrent(owner) {
            requestOwner = sessionBoundary.requestOwner(owner) ?: return@runWhileCurrent false
            val flow = CheckoutFlowStore.start(owner, listOf(product.toCartLine()))
                ?: return@runWhileCurrent false
            summaryFlow = CheckoutFlowStore.update(owner, expectedFlowId = flow.id) { current ->
                current.copy(
                    currency = CheckoutCurrency.USD.wireValue,
                    phase = CheckoutPhase.ORDER_SUMMARY,
                )
            }
            dispatchNote = com.ga.airdrop.feature.cart.CartNoteStore.note(owner)
                .trim()
                .takeIf(String::isNotEmpty)
            if (summaryFlow?.id != flow.id) return@runWhileCurrent false
            creation = CheckoutFlowStore.beginHostedCheckoutCreation(owner)
            creation != null
        }
        val request = requestOwner
        val creationId = creation?.id
        if (!bound || summaryFlow == null || request == null || creationId == null) {
            val blockedByUnknownCreation = CheckoutFlowStore.creating(owner) != null
            _state.update {
                it.copy(
                    paying = false,
                    errorTitle = if (blockedByUnknownCreation) "Payment status unknown" else "Checkout unavailable",
                    errorMessage = if (blockedByUnknownCreation) {
                        "A checkout request may already have reached Stripe. It cannot be retried safely; check Shipments."
                    } else {
                        "The checkout could not be saved safely, so no payment request was sent."
                    },
                )
            }
            return
        }
        viewModelScope.launch {
            // RECONCILE: POST /payments/create-checkout
            // { package_ids: [packageId], currency, is_auction: true } →
            // data.checkout_url (Stripe hosted checkout).
            checkout.createCheckout(
                packageIds = listOf(packageId),
                currency = CheckoutCurrency.USD.wireValue,
                isAuction = true,
                userNote = dispatchNote,
                expectedSession = request.provenance,
            )
                .onSuccess { response ->
                    applyCurrentOwner(owner) {
                        val url = validatedHostedCheckoutUrl(response.checkoutUrl)
                        val sessionId = response.sessionId?.trim()?.takeIf(String::isNotEmpty)
                        val pending = if (sessionId != null) {
                            CheckoutFlowStore.recordHostedCheckout(owner, creationId, sessionId)
                        } else {
                            null
                        }
                        if (sessionId == null || pending == null) {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    errorTitle = "Payment status unknown",
                                    errorMessage = "Stripe did not return a checkout session ID. It cannot be retried safely; check Shipments.",
                                )
                            }
                        } else if (url == null) {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    checkoutUrl = null,
                                    checkoutSessionId = null,
                                    errorTitle = "Payment still pending",
                                    errorMessage = "Stripe started checkout but did not return a secure URL. Check Shipments; do not pay again.",
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    checkoutUrl = url,
                                    checkoutSessionId = sessionId,
                                    checkoutLaunchAttempt = it.checkoutLaunchAttempt + 1,
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    applyCurrentOwner(owner) {
                        val unauthenticated = err.isUnauthenticatedCheckoutFailure()
                        _state.update {
                            it.copy(
                                paying = false,
                                errorTitle = if (unauthenticated) {
                                    "Sign in required"
                                } else {
                                    "Payment status unknown"
                                },
                                errorMessage = if (unauthenticated) {
                                    "Your session changed after checkout started. Check Shipments before paying again."
                                } else {
                                    "The checkout request may have reached Stripe. It cannot be retried safely; check Shipments."
                                },
                            )
                        }
                    }
                }
        }
    }

    fun consumeCheckoutUrl() {
        _state.update { it.copy(checkoutUrl = null, checkoutSessionId = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }

    private fun applyCurrentOwner(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean =
        sessionBoundary.runWhileCurrent(owner) {
            action()
            true
        }
}
