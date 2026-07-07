package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductPaymentDetailsUiState(
    val loading: Boolean = true,
    val payment: ShipmentPayment? = null,
    val order: ShipmentOrder? = null,
    val exchangeRate: Double = 160.0, // Swift fallback for this VC
    val error: String? = null,
) {
    val effectiveRate: Double
        get() = payment?.exchangeRate ?: order?.exchangeRate ?: exchangeRate

    /** Prefers sale price, then paid amount, then invoice amount (Swift). */
    val totalUsd: Double?
        get() = order?.salePriceUsd ?: payment?.totalAmount ?: order?.invoiceAmountUsd
}

/**
 * Product (auction) payment details — FigmaProductPaymentDetailsViewController:
 * hero product image, product summary, payment summary, total.
 */
class ProductPaymentDetailsViewModel(
    private val paymentId: String,
    private val paymentsRepo: ShipmentsPaymentsRepository = ShipmentsRepoProvider.payments,
    private val ordersRepo: ShipmentsOrdersRepository = ShipmentsRepoProvider.orders,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProductPaymentDetailsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<ProductPaymentDetailsUiState> = _state

    init {
        viewModelScope.launch {
            ordersRepo.exchangeRate().onSuccess { rate ->
                com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val paymentIdInt = paymentId.toIntOrNull()
            if (paymentIdInt == null) {
                _state.update { it.copy(loading = false, error = "Invalid payment id") }
                return@launch
            }
            paymentsRepo.payment(paymentIdInt)
                .onSuccess { payment ->
                    _state.update { it.copy(payment = payment) }
                    // Swift pushes FigmaProductPaymentDetailsViewController(orderID: payment.id).
                    ordersRepo.orderDetails(paymentIdInt)
                        .onSuccess { order ->
                            _state.update { it.copy(order = order) }
                        }
                    _state.update { it.copy(loading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }
}
