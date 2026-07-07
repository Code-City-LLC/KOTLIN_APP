package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.BuildConfig
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
    val showReportDamageSheet: Boolean = false,
    val damageReportDescription: String = "",
    val damageReportPhotos: List<DamageReportUploadFile> = emptyList(),
    val submittingDamageReport: Boolean = false,
    val damageReportError: String? = null,
    val showDamageReportSubmitted: Boolean = false,
) {
    val statusInt: Int get() = detail?.status?.toIntOrNull() ?: 0

    /**
     * Charges breakdown + Add-to-Cart section — parity with Swift `showCharges`
     * (FigmaPackageDetailsViewController.swift L1265: `statusInt == 7 || statusInt == 18`).
     * 7 = Ready for Pickup, 18 = Paid and Ready for Pick Up. `>= 7` was wrong because
     * status codes are non-contiguous (Swift L1258-1261) — it leaked Delivered (8) and
     * in-transit/customs (9/10/12). The Add-to-Cart CTA lives inside the same Swift
     * `totalContainer`, so it shares this gate.
     */
    val showChargesAndCart: Boolean get() = statusInt == 7 || statusInt == 18

    /**
     * Swift FigmaPackageDetailsViewController.updateReportDamageCTA:
     * visible only for Delivered packages (status 8), with production release
     * gated off until the backend rollout is promoted.
     */
    val showReportDamageCta: Boolean get() = statusInt == 8 && reportDamageFeatureEnabled()

    /**
     * Invoice trash gating — parity with Swift FigmaPackageDetailsViewController
     * .canDeleteInvoices(for:) (L1473-1485): the delete/trash action is hidden
     * once a package is Ready for Pickup (numeric status >= 7) or later, with a
     * statusName fallback for when the numeric `status` is missing/non-numeric.
     * Upload stays allowed at every status. UI/action-gating parity only (QC #14710).
     *
     * Deliberately independent of [showChargesAndCart] (which uses `== 7 || == 18`);
     * delete keeps Swift's numeric `>= 7`. Do not fold these together.
     */
    val canDeleteInvoices: Boolean
        get() {
            // Swift 5496ed0 tolerance: numeric lock is checked on BOTH the
            // status and statusName fields (either may carry the code), with
            // comma-decimal and floating values accepted.
            val values = listOfNotNull(detail?.status, detail?.statusName).map { it.trim() }
            if (values.any(::statusLocksInvoiceDeletion)) return false
            val lower = values.joinToString(" ") { it.lowercase() }
            if (lower.contains("ready") || lower.contains("pickup") || lower.contains("pick up")) return false
            if (lower.contains("delivered") || lower.contains("complete")) return false
            return true
        }

    val chargesTotal: Double?
        get() = detail?.additionalChargesTotal
            ?: detail?.additionalCharges?.values?.sum()?.takeIf { detail.additionalCharges.isNotEmpty() }

    val effectiveRate: Double get() = detail?.exchangeRate ?: exchangeRate
}

/**
 * Swift FigmaPackageDetailsViewController.statusLocksInvoiceDeletion
 * (5496ed0): a status value locks invoice deletion when it parses to a
 * number >= 7 — integer or floating, comma decimals normalized.
 */
internal fun statusLocksInvoiceDeletion(value: String): Boolean {
    val normalized = value.replace(",", ".")
    normalized.toIntOrNull()?.let { return it >= 7 }
    normalized.toDoubleOrNull()?.let { return it >= 7.0 }
    return false
}

internal fun reportDamageFeatureEnabled(): Boolean =
    BuildConfig.DEBUG || !BuildConfig.ENV_NAME.equals("Production", ignoreCase = true)

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

    fun showTransientMessage(message: String) =
        _state.update { it.copy(transientMessage = message) }

    fun showReportDamageSheet(show: Boolean) =
        _state.update { it.copy(showReportDamageSheet = show, damageReportError = null) }

    fun onDamageReportDescription(value: String) =
        _state.update {
            it.copy(
                damageReportDescription = value.take(MAX_DAMAGE_DESCRIPTION_LENGTH),
                damageReportError = null,
            )
        }

    fun addDamageReportPhotos(files: List<DamageReportUploadFile>) {
        if (files.isEmpty()) return
        val unsupported = files.firstOrNull { it.mimeType.lowercase() !in DAMAGE_PHOTO_MIME_TYPES }
        if (unsupported != null) {
            _state.update { it.copy(damageReportError = "Photos must be PNG or JPG/JPEG images.") }
            return
        }
        val oversize = files.firstOrNull { it.bytes.size > MAX_DAMAGE_PHOTO_BYTES }
        if (oversize != null) {
            _state.update { it.copy(damageReportError = "Each photo cannot exceed 10 MB.") }
            return
        }
        _state.update { state ->
            val available = (MAX_DAMAGE_PHOTOS - state.damageReportPhotos.size).coerceAtLeast(0)
            if (available == 0) {
                state.copy(damageReportError = "You can attach at most 5 photos.")
            } else {
                val selected = files.take(available)
                state.copy(
                    damageReportPhotos = state.damageReportPhotos + selected,
                    damageReportError = if (files.size > available) "You can attach at most 5 photos." else null,
                )
            }
        }
    }

    fun removeDamageReportPhoto(index: Int) {
        _state.update { state ->
            state.copy(
                damageReportPhotos = state.damageReportPhotos.filterIndexed { i, _ -> i != index },
                damageReportError = null,
            )
        }
    }

    fun submitDamageReport() {
        val state = _state.value
        if (state.submittingDamageReport) return
        viewModelScope.launch {
            val description = state.damageReportDescription.trim()
            _state.update { it.copy(submittingDamageReport = true, damageReportError = null) }
            repo.reportDamage(packageId, description, state.damageReportPhotos)
                .onSuccess {
                    _state.update {
                        it.copy(
                            showReportDamageSheet = false,
                            damageReportDescription = "",
                            damageReportPhotos = emptyList(),
                            submittingDamageReport = false,
                            damageReportError = null,
                            showDamageReportSubmitted = true,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            submittingDamageReport = false,
                            damageReportError = e.message ?: "Failed to submit damage report.",
                        )
                    }
                }
        }
    }

    fun dismissDamageReportSubmitted() =
        _state.update { it.copy(showDamageReportSubmitted = false) }

    fun addToCart() {
        val detail = _state.value.detail ?: return
        // Swift FigmaPackageDetailsViewController:1186-1224 — add the real
        // line to the shared cart so MyCart renders it.
        com.ga.airdrop.feature.cart.CartStore.add(detail.toCartLine())
        _state.update { it.copy(showAddedToCart = true) }
    }

    fun dismissAddedToCart() = _state.update { it.copy(showAddedToCart = false) }

    fun consumeTransientMessage() = _state.update { it.copy(transientMessage = null) }

    private companion object {
        const val MAX_DAMAGE_DESCRIPTION_LENGTH = 5_000
        const val MAX_DAMAGE_PHOTOS = 5
        const val MAX_DAMAGE_PHOTO_BYTES = 10 * 1024 * 1024
        val DAMAGE_PHOTO_MIME_TYPES = setOf("image/png", "image/jpeg")
    }
}
