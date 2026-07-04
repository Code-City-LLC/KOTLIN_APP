package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailsUiState(
    val loading: Boolean = true,
    val order: ShipmentOrder? = null,
    val exchangeRate: Double = 160.0, // Swift fallback for this VC
    val error: String? = null,
) {
    val effectiveRate: Double get() = order?.exchangeRate ?: exchangeRate
}

/**
 * Order details — FigmaOrderDetailsViewController: GET /orders/{id} +
 * exchange rate for the JMD conversion in the Total box.
 */
class OrderDetailsViewModel(
    private val orderId: String,
    private val repo: ShipmentsOrdersRepository = ShipmentsRepoProvider.orders,
) : ViewModel() {

    private val _state = MutableStateFlow(OrderDetailsUiState())
    val state: StateFlow<OrderDetailsUiState> = _state

    init {
        viewModelScope.launch {
            repo.exchangeRate().onSuccess { rate ->
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val orderIdInt = orderId.toIntOrNull()
            if (orderIdInt == null) {
                _state.update { it.copy(loading = false, error = "Invalid order id") }
                return@launch
            }
            repo.orderDetails(orderIdInt)
                .onSuccess { order ->
                    _state.update { it.copy(loading = false, order = order) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }
}
