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
    val error: String? = null,
)

/**
 * FigmaShippingRatesViewController: GET /shipping-rates.
 *
 * ⚠️ On failure this used to silently render a hardcoded fallback table whose
 * prices were roughly HALF the real ones (it quoted $6.00 at 2 lbs where the
 * server says $9.00), with no error and no retry — indistinguishable from a
 * successful load. Quoting a customer a price we made up is worse than telling
 * them the rates could not load, so the failure is now surfaced.
 */
class ShippingRatesViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ShippingRatesUiState(loading = true))
    val state: StateFlow<ShippingRatesUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.shippingRates()
                .onSuccess { rates -> _state.update { it.copy(rates = rates, loading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message?.takeIf(String::isNotBlank)
                                ?: "Couldn't load shipping rates.",
                        )
                    }
                }
        }
    }
}
