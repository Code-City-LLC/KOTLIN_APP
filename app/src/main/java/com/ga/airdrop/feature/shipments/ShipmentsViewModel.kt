package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Swift default before /exchange-rate resolves. */
const val DEFAULT_USD_TO_JMD = 160.625

data class ShipmentsUiState(
    val loading: Boolean = false,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val summary: ShipmentsSummary = ShipmentsSummary(),
    val packages: List<ShipmentPackage> = emptyList(),
    val payments: List<ShipmentPayment> = emptyList(),
    val orders: List<ShipmentOrder> = emptyList(),
    val error: String? = null,
)

/**
 * Shipments hub — FigmaShipmentsViewController: summary tiles, packages
 * shortlist (10), payments shortlist (4), orders shortlist (6).
 */
class ShipmentsViewModel(
    private val repo: ShipmentsHubRepository = ShipmentsRepoProvider.hub,
) : ViewModel() {

    private val _state = MutableStateFlow(ShipmentsUiState())
    val state: StateFlow<ShipmentsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.exchangeRate().onSuccess { rate ->
                _state.update { it.copy(exchangeRate = rate) }
            }
            repo.summary().onSuccess { summary ->
                _state.update { it.copy(summary = summary) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
            repo.packagesShortlist().onSuccess { packages ->
                _state.update { it.copy(packages = packages.take(10)) }
            }
            repo.paymentsShortlist().onSuccess { payments ->
                _state.update { it.copy(payments = payments.take(4)) }
            }
            repo.ordersShortlist().onSuccess { orders ->
                _state.update { it.copy(orders = orders.take(6)) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun toggleCart(pkg: ShipmentPackage) {
        // Swift FigmaShipmentsViewController.onTapAddPackageToCart — the ONE
        // shared cart; membership is observed via CartStore.items so the UI
        // recomposes without a fake state write.
        com.ga.airdrop.feature.cart.CartStore.toggle(pkg.toCartLine())
    }
}
