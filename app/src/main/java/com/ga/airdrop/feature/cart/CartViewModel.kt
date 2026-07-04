package com.ga.airdrop.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import com.ga.airdrop.feature.shop.ShopRepoProvider
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
    val exchangeUsdToJmd: Double = 161.00,
    val loadedFirstName: String = "",
    val form: CartBillingForm = CartBillingForm(),
    val note: String = "",
    val paying: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    val showPaymentMethodDialog: Boolean = false,
    /** Stripe hosted checkout URL to open in a Custom Tab (one-shot). */
    val checkoutUrl: String? = null,
)

/**
 * My Cart — behavior from FigmaCartViewController (RN MyCartView): items
 * from [CartStore], billing form autofilled from the user profile, exchange
 * rate, "Make Payment" → POST /payments/create-checkout (USD, is_auction)
 * → Stripe hosted checkout, then the cart is cleared.
 */
class CartViewModel(
    private val checkout: ShopCheckoutRepository = ShopRepoProvider.checkout,
) : ViewModel() {

    val currencyOptions = listOf("JMD", "USD")
    val countryOptions = listOf("United States", "Jamaica", "Canada", "United Kingdom", "Mexico")

    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state

    /** Live cart lines (Swift re-reads FigmaCartStore on every appearance). */
    val items: StateFlow<List<CartStore.CartLine>> = CartStore.items

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
                if (rate > 0) _state.update { it.copy(exchangeUsdToJmd = rate) }
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

    fun totalUsd(): Double = CartStore.totalUsd()

    fun totalJmd(): Double = totalUsd() * _state.value.exchangeUsdToJmd

    fun setPaymentMethodDialogVisible(visible: Boolean) {
        _state.update { it.copy(showPaymentMethodDialog = visible) }
    }

    /** Swift onPay: needs a package id on every line; pays in USD. */
    fun pay() {
        val lines = items.value
        if (lines.isEmpty()) {
            _state.update {
                it.copy(
                    errorTitle = "Cart empty",
                    errorMessage = "Add at least one item before checkout.",
                )
            }
            return
        }
        val packageIds = lines.mapNotNull { it.packageId }
        if (packageIds.size != lines.size) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "One or more products are missing the package ID required for auction checkout.",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(paying = true) }
            // RECONCILE: POST /payments/create-checkout
            // { package_ids, currency: "USD", is_auction: true } → data.checkout_url.
            checkout.createCheckout(packageIds, currency = "USD", isAuction = true)
                .onSuccess { url ->
                    _state.update { it.copy(paying = false, checkoutUrl = url) }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            paying = false,
                            errorTitle = "Checkout failed",
                            errorMessage = err.message ?: "Stripe did not return a valid checkout URL.",
                        )
                    }
                }
        }
    }

    /** After the hosted checkout opens, RN/Swift clear the shared cart. */
    fun onCheckoutOpened() {
        CartStore.clear()
        _state.update { it.copy(checkoutUrl = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }
}
