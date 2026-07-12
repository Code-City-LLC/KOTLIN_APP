package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentPackageDetailsUiState(
    val loading: Boolean = true,
    val payment: ShipmentPayment? = null,
    val detail: ShipmentPackageDetail? = null,
    val exchangeRate: Double = 160.0, // Swift fallback for this VC
    val showHistory: Boolean = false,
    val showCifInfo: Boolean = false,
    val error: String? = null,
) {
    /** payment rate → package rate → fetched global rate (Swift order). */
    val effectiveRate: Double
        get() = payment?.exchangeRate ?: detail?.exchangeRate ?: exchangeRate

    val totalUsd: Double?
        get() = payment?.totalAmount
            ?: detail?.additionalChargesTotal
            ?: detail?.additionalCharges?.values?.sum()?.takeIf { detail.additionalCharges.isNotEmpty() }
}

/**
 * Payment package details — FigmaPaymentPackageDetailsViewController:
 * summary + payment summary + CIF + charges + total, "View History" timeline.
 */
class PaymentPackageDetailsViewModel(
    private val paymentId: String,
    private val paymentsRepo: ShipmentsPaymentsRepository = ShipmentsRepoProvider.payments,
    private val packagesRepo: ShipmentsPackagesRepository = ShipmentsRepoProvider.packages,
    private val hubRepo: ShipmentsHubRepository = ShipmentsRepoProvider.hub,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PaymentPackageDetailsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<PaymentPackageDetailsUiState> = _state

    init {
        viewModelScope.launch {
            hubRepo.exchangeRate().onSuccess { rate ->
                com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val showFullLoader = _state.value.payment == null && _state.value.detail == null
            _state.update { it.copy(loading = showFullLoader, error = null) }
            val paymentIdInt = paymentId.toIntOrNull()
            if (paymentIdInt == null) {
                _state.update { it.copy(loading = false, error = "Invalid payment id") }
                return@launch
            }
            paymentsRepo.payment(paymentIdInt, refresh = true)
                .onSuccess { payment ->
                    _state.update { it.copy(payment = payment) }
                    val packageId = payment.packageId
                    if (packageId != null) {
                        packagesRepo.packageDetails(packageId.toString())
                            .onSuccess { detail ->
                                _state.update { it.copy(detail = detail, loading = false) }
                            }
                            .onFailure { e ->
                                _state.update {
                                    it.copy(
                                        loading = false,
                                        error = e.message ?: "Package details not found",
                                    )
                                }
                            }
                    } else {
                        _state.update { it.copy(loading = false) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun showHistory(show: Boolean) = _state.update { it.copy(showHistory = show) }

    fun showCifInfo(show: Boolean) = _state.update { it.copy(showCifInfo = show) }
}
