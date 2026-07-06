package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PackageDetailsUiState(
    val loading: Boolean = true,
    val detail: ShipmentPackageDetail? = null,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val uploading: Boolean = false,
    val error: String? = null,
    val transientMessage: String? = null,
    val showCifInfo: Boolean = false,
    val showAddedToCart: Boolean = false,
    val confirmDeleteInvoiceId: Int? = null,
) {
    val statusInt: Int get() = detail?.status?.toIntOrNull() ?: 0

    /** Charges + Add to Cart unlock at "Ready for Pickup" (status >= 7). */
    val readyForPickup: Boolean get() = statusInt >= 7

    /**
     * Invoice trash gating — parity with Swift FigmaPackageDetailsViewController
     * .canDeleteInvoices(for:) (L1473-1485): the delete/trash action is hidden
     * once a package is Ready for Pickup (numeric status >= 7) or later, with a
     * statusName fallback for when the numeric `status` is missing/non-numeric.
     * Upload stays allowed at every status. UI/action-gating parity only (QC #14710).
     *
     * Deliberately independent of [readyForPickup]/charges (which use a different
     * predicate, `== 7 || == 18`); do not fold these together.
     */
    val canDeleteInvoices: Boolean
        get() {
            val statusCode = detail?.status?.trim()?.toIntOrNull()
            if (statusCode != null && statusCode >= 7) return false
            val name = detail?.statusName?.lowercase().orEmpty()
            if (name.contains("ready") || name.contains("pickup") || name.contains("pick up")) return false
            if (name.contains("delivered") || name.contains("complete")) return false
            return true
        }

    val chargesTotal: Double?
        get() = detail?.additionalChargesTotal
            ?: detail?.additionalCharges?.values?.sum()?.takeIf { detail.additionalCharges.isNotEmpty() }

    val effectiveRate: Double get() = detail?.exchangeRate ?: exchangeRate
}

/**
 * Package details — FigmaPackageDetailsViewController: GET /packages/{id},
 * invoice multipart upload/delete, charges breakdown, add-to-cart.
 */
class PackageDetailsViewModel(
    private val packageId: String,
    private val repo: ShipmentsPackagesRepository = ShipmentsRepoProvider.packages,
    private val hubRepo: ShipmentsHubRepository = ShipmentsRepoProvider.hub,
) : ViewModel() {

    private val _state = MutableStateFlow(PackageDetailsUiState())
    val state: StateFlow<PackageDetailsUiState> = _state

    init {
        viewModelScope.launch {
            hubRepo.exchangeRate().onSuccess { rate ->
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.packageDetails(packageId)
                .onSuccess { detail ->
                    _state.update { it.copy(loading = false, detail = detail) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    /** Multipart POST /packages/{id}/invoices — field "invoices[]", max 3 x 10MB. */
    fun uploadInvoices(files: List<InvoiceUploadFile>) {
        if (files.isEmpty()) return
        val existing = _state.value.detail?.invoices?.size ?: 0
        val allowed = (3 - existing).coerceAtLeast(0)
        if (allowed == 0) {
            _state.update { it.copy(transientMessage = "You're allowed to upload a maximum of 3 files.") }
            return
        }
        val oversize = files.firstOrNull { it.bytes.size > 10 * 1024 * 1024 }
        if (oversize != null) {
            _state.update { it.copy(transientMessage = "Each file must be below 10 MB.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            repo.uploadInvoices(packageId, files.take(allowed))
                .onSuccess {
                    _state.update { it.copy(uploading = false) }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(uploading = false, transientMessage = e.message) }
                }
        }
    }

    fun requestDeleteInvoice(invoiceId: Int) {
        // Parity: Swift onTapDeleteInvoice (L1568-1575) re-checks canDeleteInvoices
        // and shows an explanatory alert instead of opening the confirm dialog once
        // the package is ready for pickup — keeps delete inert even if the trash
        // icon leaks through (belt-and-suspenders with the hidden UI control).
        if (!_state.value.canDeleteInvoices) {
            _state.update {
                it.copy(
                    transientMessage =
                        "Invoices can still be uploaded, but they cannot be deleted " +
                            "once a package is ready for pickup.",
                )
            }
            return
        }
        _state.update { it.copy(confirmDeleteInvoiceId = invoiceId) }
    }

    fun dismissDeleteInvoice() = _state.update { it.copy(confirmDeleteInvoiceId = null) }

    fun confirmDeleteInvoice() {
        val invoiceId = _state.value.confirmDeleteInvoiceId ?: return
        viewModelScope.launch {
            _state.update { it.copy(confirmDeleteInvoiceId = null) }
            repo.deleteInvoice(packageId, invoiceId)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(transientMessage = e.message) } }
        }
    }

    fun showCifInfo(show: Boolean) = _state.update { it.copy(showCifInfo = show) }

    fun addToCart() {
        val detail = _state.value.detail ?: return
        // Swift FigmaPackageDetailsViewController:1186-1224 — add the real
        // line to the shared cart so MyCart renders it.
        com.ga.airdrop.feature.cart.CartStore.add(detail.toCartLine())
        _state.update { it.copy(showAddedToCart = true) }
    }

    fun dismissAddedToCart() = _state.update { it.copy(showAddedToCart = false) }

    fun consumeTransientMessage() = _state.update { it.copy(transientMessage = null) }
}
