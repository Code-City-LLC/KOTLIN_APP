package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.data.api.ApiErrorCodes
import com.ga.airdrop.data.model.InsuranceOptions
import com.ga.airdrop.data.model.InsuranceSelection
import com.ga.airdrop.data.model.PackageTierInfo
import com.ga.airdrop.data.repo.TierRepository
import com.ga.airdrop.data.repo.serverErrorCode
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
    /** Contextual alert title for [transientMessage] (Audit#5 C4). */
    val transientTitle: String? = null,
    /** Invoice DELETE in flight — gates re-entry (Audit#5 C3). */
    val deletingInvoiceId: Int? = null,
    val showCifInfo: Boolean = false,
    val showAddedToCart: Boolean = false,
    val confirmDeleteInvoiceId: Int? = null,
    val showReportDamageSheet: Boolean = false,
    val damageReportDescription: String = "",
    val damageReportPhotos: List<DamageReportUploadFile> = emptyList(),
    val submittingDamageReport: Boolean = false,
    val damageReportError: String? = null,
    val showDamageReportSubmitted: Boolean = false,
    // ── Package insurance (Tier API — joint Swift/Kotlin flow) ──
    /** GET /packages/{id}/tier — tier snapshot + recorded insurance choice. */
    val packageTierInfo: PackageTierInfo? = null,
    val showInsuranceSheet: Boolean = false,
    /** GET /insurance/options quote for this package's declared value. */
    val insuranceQuote: InsuranceOptions? = null,
    val insuranceQuoteError: String? = null,
    /** A select/decline POST is in flight — gates re-entry + dismissal. */
    val insuranceBusy: Boolean = false,
    val insuranceError: String? = null,
    /**
     * Backend refused a decline with INSURANCE_MANDATORY — the sheet snaps
     * back to the covered option and stops offering decline (error_code pact).
     */
    val insuranceDeclineRefused: Boolean = false,
) {
    /** The recorded insurance choice for this package, if any. */
    val insuranceSelection: InsuranceSelection? get() = packageTierInfo?.insurance

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
    private val tierRepo: TierRepository = TierRepository(com.ga.airdrop.core.network.ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(
        PackageDetailsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<PackageDetailsUiState> = _state

    init {
        viewModelScope.launch {
            hubRepo.exchangeRate().onSuccess { rate ->
                com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        refresh()
        loadPackageTier()
    }

    /**
     * Best-effort tier/insurance snapshot (GET /packages/{id}/tier). Failure
     * simply hides the Insurance row — the rest of the page is unaffected.
     */
    private fun loadPackageTier() {
        val id = packageId.toIntOrNull() ?: return
        viewModelScope.launch {
            tierRepo.packageTier(id).onSuccess { info ->
                _state.update { it.copy(packageTierInfo = info) }
            }
        }
    }

    // ── Package insurance: explicit select/decline (Tier API pact) ──

    fun openInsuranceSheet() {
        _state.update {
            it.copy(showInsuranceSheet = true, insuranceError = null, insuranceDeclineRefused = false)
        }
        loadInsuranceQuote()
    }

    fun dismissInsuranceSheet() {
        if (_state.value.insuranceBusy) return
        _state.update { it.copy(showInsuranceSheet = false, insuranceError = null) }
    }

    /** GET /insurance/options for the package's declared value — backend math only. */
    private fun loadInsuranceQuote() {
        val declared = _state.value.detail?.amount ?: 0.0
        viewModelScope.launch {
            _state.update { it.copy(insuranceQuote = null, insuranceQuoteError = null) }
            tierRepo.insuranceOptions(
                insuredValue = declared,
                tierCode = _state.value.packageTierInfo?.tierCode,
            )
                .onSuccess { quote -> _state.update { it.copy(insuranceQuote = quote) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(insuranceQuoteError = e.message ?: "Couldn't load insurance options.")
                    }
                }
        }
    }

    /** POST select — insure the declared value at the backend-quoted premium. */
    fun confirmInsurance() {
        val id = packageId.toIntOrNull() ?: return
        val quote = _state.value.insuranceQuote ?: return
        if (_state.value.insuranceBusy) return
        viewModelScope.launch {
            _state.update { it.copy(insuranceBusy = true, insuranceError = null) }
            tierRepo.selectInsurance(id, quote.insuredValue)
                .onSuccess { recorded -> _state.update { applyInsuranceRecorded(it, recorded) } }
                .onFailure { e ->
                    _state.update { applyInsuranceFailure(it, e.serverErrorCode(), e.message) }
                }
        }
    }

    /**
     * POST decline — only SAVR may decline. A non-SAVR decline comes back
     * INSURANCE_MANDATORY and the sheet snaps back to the covered option.
     */
    fun declineInsurance() {
        val id = packageId.toIntOrNull() ?: return
        if (_state.value.insuranceBusy) return
        viewModelScope.launch {
            _state.update { it.copy(insuranceBusy = true, insuranceError = null) }
            tierRepo.declineInsurance(id)
                .onSuccess { recorded -> _state.update { applyInsuranceRecorded(it, recorded) } }
                .onFailure { e ->
                    _state.update { applyInsuranceFailure(it, e.serverErrorCode(), e.message) }
                }
        }
    }

    /**
     * @param silent post-mutation reloads keep the current detail on screen
     * instead of flashing the full-page spinner (Audit#5 C1).
     */
    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = if (silent) it.loading else true, error = null) }
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
        // In-flight guard: overlapping picks must not issue parallel POSTs
        // (Audit#5 C2 — mirrors the submitDamageReport guard).
        if (_state.value.uploading) return
        val existing = _state.value.detail?.invoices?.size ?: 0
        val allowed = (3 - existing).coerceAtLeast(0)
        if (allowed == 0) {
            _state.update {
                it.copy(
                    transientTitle = "Upload Invoice",
                    transientMessage = "You're allowed to upload a maximum of 3 files.",
                )
            }
            return
        }
        val oversize = files.firstOrNull { it.bytes.size > 10 * 1024 * 1024 }
        if (oversize != null) {
            _state.update {
                it.copy(
                    transientTitle = "Upload Invoice",
                    transientMessage = "Each file must be below 10 MB.",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            repo.uploadInvoices(packageId, files.take(allowed))
                .onSuccess {
                    _state.update { it.copy(uploading = false) }
                    refresh(silent = true)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            uploading = false,
                            transientTitle = "Upload Invoice",
                            transientMessage = e.message,
                        )
                    }
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
                    transientTitle = "Delete Invoice",
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
        // In-flight guard (Audit#5 C3): one DELETE at a time.
        if (_state.value.deletingInvoiceId != null) return
        viewModelScope.launch {
            _state.update { it.copy(confirmDeleteInvoiceId = null, deletingInvoiceId = invoiceId) }
            repo.deleteInvoice(packageId, invoiceId)
                .onSuccess {
                    _state.update { it.copy(deletingInvoiceId = null) }
                    refresh(silent = true)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            deletingInvoiceId = null,
                            transientTitle = "Delete Invoice",
                            transientMessage = e.message,
                        )
                    }
                }
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

    fun consumeTransientMessage() =
        _state.update { it.copy(transientMessage = null, transientTitle = null) }

    private companion object {
        const val MAX_DAMAGE_DESCRIPTION_LENGTH = 5_000
        const val MAX_DAMAGE_PHOTOS = 5
        const val MAX_DAMAGE_PHOTO_BYTES = 10 * 1024 * 1024
        val DAMAGE_PHOTO_MIME_TYPES = setOf("image/png", "image/jpeg")
    }
}

// ─── Insurance pure state transitions (unit-tested without coroutines) ──────

/** Fold a recorded select/decline into the page: close the sheet, show the row state. */
internal fun applyInsuranceRecorded(
    prev: PackageDetailsUiState,
    recorded: InsuranceSelection,
): PackageDetailsUiState = prev.copy(
    insuranceBusy = false,
    insuranceError = null,
    insuranceDeclineRefused = false,
    showInsuranceSheet = false,
    packageTierInfo = (prev.packageTierInfo ?: PackageTierInfo()).copy(insurance = recorded),
)

/**
 * Fold a failed select/decline. INSURANCE_MANDATORY (a non-SAVR decline) snaps
 * the sheet back to the covered option: decline disappears and the coded copy
 * explains why — the joint Swift/Kotlin error_code behaviour.
 */
internal fun applyInsuranceFailure(
    prev: PackageDetailsUiState,
    errorCode: String?,
    message: String?,
): PackageDetailsUiState {
    val copy = ApiErrorCodes.friendlyCopy(errorCode)
        ?: message
        ?: "Couldn't record your insurance choice. Please try again."
    return prev.copy(
        insuranceBusy = false,
        insuranceError = copy,
        insuranceDeclineRefused = prev.insuranceDeclineRefused ||
            errorCode == ApiErrorCodes.INSURANCE_MANDATORY,
    )
}

/** The Insurance row's trailing status — mirrors what the backend recorded. */
internal fun insuranceStatusLabel(selection: InsuranceSelection?): String = when {
    selection == null -> "Choose"
    selection.declined -> "Declined"
    selection.selected -> "Covered"
    else -> "Choose"
}
