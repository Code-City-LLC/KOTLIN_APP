package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.ShippingRates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShippingRatesUiState(
    val rates: ShippingRates? = null,
    val loading: Boolean = false,
)

/**
 * FigmaShippingRatesViewController: GET /shipping-rates; on failure the
 * screen renders the documented fallback table (Swift does the same).
 */
class ShippingRatesViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ShippingRatesUiState(loading = true))
    val state: StateFlow<ShippingRatesUiState> = _state

    init {
        viewModelScope.launch {
            repository.shippingRates()
                .onSuccess { rates -> _state.update { it.copy(rates = rates, loading = false) } }
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }
}
