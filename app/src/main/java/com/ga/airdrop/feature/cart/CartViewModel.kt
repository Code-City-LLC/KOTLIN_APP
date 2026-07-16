package com.ga.airdrop.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import com.ga.airdrop.feature.shop.ShopRepoProvider
import com.ga.airdrop.feature.shop.isUnauthenticatedCheckoutFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartBillingForm(
    val firstName: String = "",
    val lastName: String = "",
    val currency: String = "JMD",
    val address1: String = "",
    val address2: String = "",
    val state: String = "",
    val city: String = "",
    val country: String = "United States",
    val postal: String = "",
)

data class CartUiState(
    // Swift moved off the hardcoded 161.00 to the shared last-known rate;
    // seed with the same shipments default (FuchsiaTower Pass-4 C6).
    val exchangeUsdToJmd: Double = com.ga.airdrop.feature.shipments.DEFAULT_USD_TO_JMD,
    val loadedFirstName: String = "",
    val form: CartBillingForm = CartBillingForm(),
    val note: String = "",
    val paying: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    val showPaymentMethodDialog: Boolean = false,
    /** Stripe hosted checkout URL to open in a Custom Tab (one-shot). */
    val checkoutUrl: String? = null,
    /**
     * One-shot: "Make Payment" now routes to the Delivery Method screen
     * (Swift cart → FigmaDeliveryMethodViewController parity,
     * docs/PARITY_GAP_SPECS.md §4) instead of creating the Stripe checkout
     * directly. The original checkout path lives on in [CartViewModel.payWithCurrency].
     */
    val navToDeliveryMethod: Boolean = false,
)

/**
 * My Cart — behavior from FigmaCartViewController (RN MyCartView): items
 * from [CartStore], billing form autofilled from the user profile, exchange
 * rate. "Make Payment" → Delivery Method screen (Swift parity), whose
 * currency popup runs POST /payments/create-checkout (chosen currency,
 * is_auction) → Stripe hosted checkout; [payWithCurrency] keeps the original
 * direct-checkout path.
 */
class CartViewModel(
    private val checkout: ShopCheckoutRepository = ShopRepoProvider.checkout,
) : ViewModel() {

    val currencyOptions = listOf("JMD", "USD")
    val countryOptions = listOf("United States", "Jamaica", "Canada", "United Kingdom", "Mexico")

    private val _state = MutableStateFlow(
        CartUiState(exchangeUsdToJmd = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<CartUiState> = _state

    /** Live cart lines (Swift re-reads FigmaCartStore on every appearance). */
    val items: StateFlow<List<CartStore.CartLine>> = CartStore.items

    /** Live saved-for-later lines, separate from the active checkout cart. */
    val savedItems: StateFlow<List<CartStore.CartLine>> = SavedForLaterStore.items

    init {
        loadUserAndRate()
    }

    private fun loadUserAndRate() {
        viewModelScope.launch {
            // RECONCILE: GET /user → first_name, last_name, address, state,
            // city, country (billing autofill — Swift applyUser).
            checkout.billingProfile().onSuccess { user ->
                _state.update {
                    it.copy(
                        loadedFirstName = user.firstName,
                        form = it.form.copy(
                            firstName = user.firstName,
                            lastName = user.lastName,
                            address1 = user.address1,
                            state = user.state,
                            city = user.city,
                            country = user.country.ifBlank { it.form.country },
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            // RECONCILE: GET /exchange-rates → { usd_to_jmd }.
            checkout.exchangeRate().onSuccess { rate ->
                if (rate > 0) {
                    com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                    _state.update { it.copy(exchangeUsdToJmd = rate) }
                }
            }
        }
    }

    fun updateForm(transform: (CartBillingForm) -> CartBillingForm) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    fun updateNote(value: String) {
        _state.update { it.copy(note = value) }
    }

    fun removeItem(id: Int) = CartStore.remove(id)

    fun saveForLater(line: CartStore.CartLine) {
        if (SavedForLaterStore.save(line)) {
            CartStore.remove(line.id)
        }
    }

    fun moveSavedToCart(line: CartStore.CartLine) {
        CartStore.add(line)
        SavedForLaterStore.remove(line.id)
    }

    fun removeSaved(id: Int) = SavedForLaterStore.remove(id)

    fun totalUsd(): Double = CartStore.totalUsd()

    fun totalJmd(): Double = totalUsd() * _state.value.exchangeUsdToJmd

    fun setPaymentMethodDialogVisible(visible: Boolean) {
        _state.update { it.copy(showPaymentMethodDialog = visible) }
    }

    /**
     * Shared Swift-onPay guards: non-empty cart and a package id on every
     * line. Returns null (after surfacing the error) when checkout can't
     * proceed. Error copy is load-bearing — mirrored by
     * DeliveryMethodViewModel.onCurrencyChosen.
     */
    private fun guardedPackageIds(): List<Int>? {
        val lines = items.value
        if (lines.isEmpty()) {
            _state.update {
                it.copy(
                    errorTitle = "Cart empty",
                    errorMessage = "Add at least one item before checkout.",
                )
            }
            return null
        }
        val packageIds = lines.mapNotNull { it.packageId }
        if (packageIds.size != lines.size) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "One or more products are missing the package ID required for sale checkout.",
                )
            }
            return null
        }
        return packageIds
    }

    /**
     * Swift onPay: needs a package id on every line. The cart no longer goes
     * straight to Stripe — after the guards pass it hands off to the Delivery
     * Method screen (one-shot [CartUiState.navToDeliveryMethod]), which owns
     * the pickup/delivery choice + currency popup and then runs the original
     * checkout path (docs/PARITY_GAP_SPECS.md §4).
     */
    fun pay() {
        if (_state.value.paying) return
        guardedPackageIds() ?: return
        _state.update { it.copy(navToDeliveryMethod = true) }
    }

    /** The screen navigated — clear the one-shot flag. */
    fun consumeDeliveryNav() {
        _state.update { it.copy(navToDeliveryMethod = false) }
    }

    /**
     * The ORIGINAL "Make Payment" body — Stripe hosted checkout with the
     * chosen currency (default "USD" preserves the pre-Delivery-Method
     * behavior byte-for-byte).
     */
    fun payWithCurrency(currency: String = "USD") {
        if (_state.value.paying) return
        val packageIds = guardedPackageIds() ?: return
        // Declare the cart honestly: auction/e-commerce items present ⇒
        // is_auction true; a regular-package-only cart ⇒ false. The server
        // derives this itself too, but sending the truth keeps the contract
        // correct (Swift parity).
        val cartIsAuction = items.value.any { it.isAuction }
        viewModelScope.launch {
            _state.update { it.copy(paying = true) }
            // RECONCILE: POST /payments/create-checkout
            // { package_ids, currency, is_auction } → data.checkout_url.
            checkout.createCheckout(packageIds, currency = currency, isAuction = cartIsAuction)
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

    /**
     * The hosted checkout tab just opened. Swift keeps the cart intact until
     * checkoutSessionStatus confirms the payment (SceneDelegate.swift:604 —
     * Kemar 2026-05-27 stuck-cart ruling: verify-then-clear). Clearing here
     * destroyed the cart whenever the user cancelled or abandoned Stripe
     * (WORK ORDER R3). The verified-paid clear now lives in the payment-return
     * flow: the Stripe redirect (airdrop://payment-success?session_id=…) lands
     * on Routes.PAYMENT_RETURN, which verifies the session and clears the cart
     * ONLY on paid (AppRoot.mainGraph + PaymentReturnViewModel).
     */
    fun onCheckoutOpened() {
        _state.update { it.copy(checkoutUrl = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }
}
