package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.designsystem.components.CifRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentPackageDetailsUiState(
    val loading: Boolean = true,
    val payment: ShipmentPayment? = null,
    val detail: ShipmentPackageDetail? = null,
    /** Laravel's canonical journey; the client no longer builds the rail. */
    val timeline: List<com.ga.airdrop.data.model.PackageTimelineEntry> = emptyList(),
    /**
     * True when the timeline read FAILED, so [timeline] is unknown rather than
     * genuinely empty.
     *
     * Its twin screen (PackageDetailsScreen) already branches three ways —
     * failed / loaded-but-unusable / genuinely empty — but this one collapsed a
     * failure into emptyList() and rendered an empty bordered card with no rows,
     * no message and no retry. The customer could not tell whether the package
     * had no journey, the screen was broken, or the read had failed.
     *
     * Kemar's rule is that BOTH package-detail screens show full history, so
     * both must be equally honest when they cannot.
     */
    val timelineLoadFailed: Boolean = false,
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

    /**
     * CIF components for the shared CIF Value sheet (Figma 40001761:29633).
     * Insurance is not exposed on the packages payload, so it stays null and
     * renders as an em-dash rather than a fabricated 0.00 — see CifValueSheet.
     */
    val cifRows: List<CifRow>
        get() = listOf(
            // Mirrors the Invoice Amount row above (amount → original_price).
            CifRow(
                "Cost (Invoice Amount)",
                detail?.amount?.takeIf { it > 0.0 } ?: detail?.originalPrice?.takeIf { it > 0.0 },
            ),
            CifRow("Insurance", null),
            CifRow("Freight", detail?.shippingPrice?.takeIf { it > 0.0 }),
        )
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
    private val tracking: com.ga.airdrop.data.repo.DeliveryTrackingGateway =
        com.ga.airdrop.data.repo.DeliveryTrackingRepository(com.ga.airdrop.core.network.ApiClient.service),
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
                                // A timeline failure must not blank the screen —
                                // still true, so we keep rendering. What was
                                // missing is any record that it FAILED, which
                                // made "no journey" and "we could not load it"
                                // pixel-identical.
                                val timelineResult = tracking.packageTimeline(packageId)
                                _state.update {
                                    it.copy(
                                        detail = detail,
                                        timeline = timelineResult.getOrNull().orEmpty(),
                                        timelineLoadFailed = timelineResult.isFailure,
                                        loading = false,
                                    )
                                }
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
