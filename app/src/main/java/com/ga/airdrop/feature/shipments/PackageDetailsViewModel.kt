package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.designsystem.components.CifRow
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.DataCartServerGateway
import com.ga.airdrop.feature.cart.PackageCartMutationCoordinator
import com.ga.airdrop.feature.cart.isPackageCartEligibleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Did the canonical timeline load, and can its emptiness be trusted? */
enum class TimelineOutcome { LOADING, LOADED, FAILED }

data class PackageDetailsUiState(
    val loading: Boolean = true,
    val detail: ShipmentPackageDetail? = null,
    /**
     * Laravel's canonical journey for this package. The client no longer
     * derives the rail from `history` — see TrackJourney.
     */
    val timeline: List<com.ga.airdrop.data.model.PackageTimelineEntry> = emptyList(),
    /**
     * Whether the canonical timeline request actually SUCCEEDED.
     *
     * ⚠️ Load-bearing. `packageTimeline(...).getOrNull().orEmpty()` maps every
     * failure — network, 401, 5xx, a decode error — onto the same empty list a
     * genuinely event-less package produces. Without this flag the screen
     * cannot tell "this package has no recorded history" from "we could not
     * read its history", and a package with a full journey would silently
     * collapse to a single current-status row on a dropped request.
     *
     * Caught by BrightHarbor in review of #178 before it merged.
     */
    val timelineOutcome: TimelineOutcome = TimelineOutcome.LOADING,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val uploading: Boolean = false,
    val deletingInvoiceId: Int? = null,
    val error: String? = null,
    /** Contextual alert title for [transientMessage] (Audit#5 C4). */
    val transientTitle: String? = null,
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
    /**
     * CIF components for the CIF Value sheet (Figma 40001761:29633).
     * Cost = the declared/invoice amount; Freight = the shipping price.
     * Insurance is NOT yet returned by the API (raised with Laravel), so it
     * renders as an em-dash rather than a fabricated number.
     */
    internal val cifRows: List<CifRow>
        get() = listOf(
            // Same amount → originalPrice fallback the Invoice Amount row on
            // screen uses, so the CIF breakdown can't say "—" while the row
            // right above it shows a figure.
            CifRow("Cost (Invoice Amount)", declaredValueUsd),
            // Insurance amount is not returned by the API yet (raised with
            // Laravel) — render an em-dash rather than fabricate a number.
            CifRow("Insurance", null),
            CifRow("Freight", detail?.shippingPrice?.takeIf { it > 0.0 }),
        )

    /** Declared/invoice value: `amount`, else `original_price`, else absent. */
    private val declaredValueUsd: Double?
        get() = detail?.amount?.takeIf { it > 0.0 }
            ?: detail?.originalPrice?.takeIf { it > 0.0 }

    val statusInt: Int get() = detail?.status?.toIntOrNull() ?: 0

    /**
     * Charges breakdown section — parity with Swift `showCharges`
     * (FigmaPackageDetailsViewController.swift L1265: `statusInt == 7 || statusInt == 18`).
     * 7 = Ready for Pickup, 18 = Paid and Ready for Pick Up. `>= 7` was wrong because
     * status codes are non-contiguous (Swift L1258-1261) — it leaked Delivered (8) and
     * in-transit/customs (9/10/12). The Add-to-Cart CTA lives inside the same Swift
     * `totalContainer`. The Laravel add contract is narrower; [canAddToCart]
     * independently gates that CTA to exact status 7.
     */
    val showChargesAndCart: Boolean get() = statusInt == 7 || statusInt == 18

    val canAddToCart: Boolean
        get() = isPackageCartEligibleStatus(detail?.status?.trim()?.toIntOrNull())

    /**
     * Swift FigmaPackageDetailsViewController.updateReportDamageCTA:
     * visible only for Delivered packages (status 8), with production release
     * gated off until the backend rollout is promoted.
     */
    val showReportDamageCta: Boolean get() = statusInt == 8 && reportDamageFeatureEnabled()

    /**
     * Invoice trash gating — parity with Swift FigmaPackageDetailsViewController
     * .canDeleteInvoices(for:) (L1473-1485): the delete/trash action is hidden
     * once a package reaches one of Swift's explicit terminal/locked status IDs,
     * with a status-name fallback for missing, stale, or non-numeric values.
     * UI/action-gating parity only (QC #14710).
     *
     * ⚠️ Upload is gated too — see [canUploadInvoices] — but the two are NOT the
     * same rule. They share this recognized locked-status predicate and nothing
     * more: upload additionally requires a recognized status and fails CLOSED on
     * absent/unknown, while delete stays independent and fail-OPEN there (an
     * absent detail or unreadable status leaves delete available, deliberately,
     * because hiding a delete affordance is the lesser harm).
     *
     * Deliberately independent of [showChargesAndCart]. Do not fold these together.
     */
    val canDeleteInvoices: Boolean
        get() {
            // Swift 5496ed0 tolerance: numeric lock is checked on BOTH the
            // status and statusName fields (either may carry the code), with
            // comma-decimal and floating values accepted.
            val values = listOfNotNull(detail?.status, detail?.statusName).map { it.trim() }
            if (values.any(::statusLocksInvoiceDeletion)) return false
            return true
        }

    /**
     * Invoice UPLOAD eligibility — **fail CLOSED**. Upload is a mutation, and it
     * is offered only when the package is PROVEN to be in a known pre-collection
     * state. Unknown, missing or unreadable ⇒ BLOCKED, with zero repository POST
     * (ORC 95620/95648).
     *
     * ## Why fail closed, when delete deliberately fails open
     *
     * **Laravel does not currently reject the unsafe upload**, so there is no
     * server-side backstop — this predicate is the only gate. An unrecognised
     * status is therefore not a wasted tap; it is an attachment written onto a
     * shipment that may already be closed. (Swift's fail-open is not
     * authoritative for this decision.)
     *
     * ⚠️ **This is NOT the inverse of [canDeleteInvoices], and must never be
     * simplified into one.** The two share the recognized locked-status
     * predicate and nothing more. `canDeleteInvoices` returns true when `detail`
     * or the status is absent — deliberately, because hiding a delete affordance
     * is the lesser harm. Aliasing them would re-open upload for exactly the
     * absent/unknown cases this exists to close.
     *
     * ## The rule, in order
     *
     *  1. `detail` must be present.
     *  2. At least one of `status` / `statusName` must resolve to a **recognized**
     *     catalog status — see [invoiceStatusIsRecognized]. Eligibility must be
     *     PROVEN, not merely un-disproven.
     *  3. **No** value may lock via [statusLocksInvoiceDeletion]. Lock precedence
     *     wins: on conflicting fields, the lock decides.
     *
     * Known pre-collection states stay open (6, and the non-sequential 9, 10, 12
     * — never a numeric `< 7` test, which would wrongly block those three).
     * Locked IDs {7, 8, 14-20} and terminal-sounding names are blocked.
     */
    val canUploadInvoices: Boolean
        get() {
            val current = detail ?: return false
            val values = listOfNotNull(current.status, current.statusName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (values.isEmpty()) return false
            // Lock precedence FIRST: a recognized locked value beats any other
            // field that happens to look eligible.
            if (values.any(::statusLocksInvoiceDeletion)) return false
            return values.any(::invoiceStatusIsRecognized)
        }

    val chargesTotal: Double?
        get() = detail?.additionalChargesTotal
            ?: detail?.additionalCharges?.values?.sum()?.takeIf { detail.additionalCharges.isNotEmpty() }

    val effectiveRate: Double get() = detail?.exchangeRate ?: exchangeRate
}

/**
 * Swift FigmaPackageDetailsViewController.statusLocksInvoiceDeletion
 * Package status IDs are non-contiguous. Mirror Swift's explicit lock set,
 * integer-valued decimal/comma normalization, catalog lookup, and name fallback.
 */
internal fun statusLocksInvoiceDeletion(value: String): Boolean {
    val code = normalizedInvoiceStatusCode(value)
    if (code != null) {
        if (code in INVOICE_DELETION_LOCKED_STATUS_IDS) return true
        ShipmentStatusCatalog.defaults.firstOrNull { it.id == code }?.name?.let { name ->
            if (statusNameLocksInvoiceDeletion(name)) return true
        }
    }
    return statusNameLocksInvoiceDeletion(value)
}

private val INVOICE_DELETION_LOCKED_STATUS_IDS = setOf(7, 8, 14, 15, 16, 17, 18, 19, 20)

/**
 * Is this value a status we actually RECOGNISE? Used only by [PackageDetailsUiState
 * .canUploadInvoices], where eligibility for a mutation must be proven rather than
 * assumed. Local to this file on purpose (ORC 95658) — the shared catalog is not
 * changed by this task.
 *
 * ⚠️ **[ShipmentStatusCatalog.idFor] is deliberately NOT used here**, and calling it
 * would silently reopen the hole this closes. Two reasons, both real:
 *
 *  1. It falls back to `contains` matching in BOTH directions, so generic text like
 *     "Processing" or "Shipment" would be accepted as a proven stage.
 *  2. Its `normalize` strips every non-alphanumeric character, so `"???"` normalises
 *     to the EMPTY string — and every catalog name contains the empty string. An
 *     unguarded `idFor("???")` returns status **1**, which would mark the most
 *     obviously unreadable status in the codebase as eligible. (ORC 95657.)
 *
 * So: numeric codes are checked against catalog membership, names must match a
 * catalog name EXACTLY once normalised, and a candidate with no letter or digit is
 * unknown by definition.
 */
internal fun invoiceStatusIsRecognized(value: String): Boolean {
    normalizedInvoiceStatusCode(value)?.let { code ->
        return ShipmentStatusCatalog.defaults.any { it.id == code }
    }
    val target = normalizedCatalogName(value)
    // Punctuation-only / blank: nothing to recognise. Guarded BEFORE any name
    // comparison, which is the exact `"???"` trap above.
    if (target.isEmpty()) return false
    // "Auction" is the backend alias of catalog id 17 "Sale"; handled explicitly
    // because it is a real status name that is not in `defaults` under that word.
    if (target == "auction") return true
    return ShipmentStatusCatalog.defaults.any { normalizedCatalogName(it.name) == target }
}

/** Same normalisation shape the catalog uses, kept local so this gate owns its rule. */
private fun normalizedCatalogName(value: String): String =
    value.lowercase(java.util.Locale.US).filter(Char::isLetterOrDigit)

private fun normalizedInvoiceStatusCode(value: String): Int? {
    val normalized = value.trim().replace(",", ".")
    normalized.toIntOrNull()?.let { return it }
    val number = normalized.toDoubleOrNull() ?: return null
    if (!number.isFinite() || number % 1.0 != 0.0) return null
    if (number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble()) return null
    return number.toInt()
}

private fun statusNameLocksInvoiceDeletion(value: String): Boolean {
    val lower = value.lowercase()
    return lower.contains("ready") || lower.contains("pickup") || lower.contains("pick up") ||
        lower.contains("delivered") || lower.contains("delivery") || lower.contains("complete") ||
        lower.contains("returned") || lower.contains("uncollected") ||
        lower.contains("dangerous") || lower.contains("auction") || lower.contains("sale")
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
    /** Laravel's canonical journey — the client no longer builds the rail. */
    private val tracking: com.ga.airdrop.data.repo.DeliveryTrackingGateway =
        com.ga.airdrop.data.repo.DeliveryTrackingRepository(com.ga.airdrop.core.network.ApiClient.service),
    cartServer: CartServerGateway = DataCartServerGateway(),
    sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PackageDetailsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<PackageDetailsUiState> = _state
    private val cartMutations = PackageCartMutationCoordinator(cartServer, sessionBoundary)
    // Route references are intentionally unresolved at construction time.
    // A courier value may be all digits, so even a numeric-looking reference
    // must pass through the exact tracking/courier search before ID fallback.
    private var resolvedPackageId: String? = null

    init {
        viewModelScope.launch {
            hubRepo.exchangeRate().onSuccess { rate ->
                com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                _state.update { it.copy(exchangeRate = rate) }
            }
        }
        refresh()
    }

    /**
     * @param silent post-mutation reloads keep the current detail on screen
     * instead of flashing the full-page spinner (Audit#5 C1).
     */
    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            loadDetails(showLoading = !silent)
        }
    }

    /** Multipart POST /packages/{id}/invoices — field "invoices[]", max 3 x 10MB. */
    fun uploadInvoices(files: List<InvoiceUploadFile>) {
        if (files.isEmpty()) return
        val state = _state.value
        if (state.loading || state.uploading || state.deletingInvoiceId != null) return
        // ⚠️ THE BOUNDARY IS ENFORCED HERE, NOT ONLY IN THE UI.
        //
        // Hiding the drop zone stops the ordinary path, and nothing else. A
        // source sheet opened a moment before the status flipped is still on
        // screen holding a live callback into this function; so is any future
        // caller. If the gate lived only in the composable, that sheet would
        // POST an invoice onto a package the customer has already collected —
        // the exact write the gate exists to prevent, arriving through the one
        // route nobody was watching.
        //
        // Same audited predicate as the UI, so the two cannot disagree.
        if (!state.canUploadInvoices) return
        val existing = state.detail?.invoices?.size ?: 0
        val allowed = (3 - existing).coerceAtLeast(0)
        if (allowed == 0) {
            showTransient(title = "Upload Invoice", message = "You're allowed to upload a maximum of 3 files.")
            return
        }
        val oversize = files.firstOrNull { it.bytes.size > 10 * 1024 * 1024 }
        if (oversize != null) {
            showTransient(title = "Upload Invoice", message = "Each file must be below 10 MB.")
            return
        }
        val apiPackageId = resolvedPackageId ?: run {
            showTransient(title = "Upload Invoice", message = "Package details are not ready. Try again.")
            return
        }
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            repo.uploadInvoices(apiPackageId, files.take(allowed))
                .onSuccess {
                    _state.update { it.copy(uploading = false) }
                    loadDetails(showLoading = false)
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
                    transientTitle = "Delete invoice",
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
        val state = _state.value
        if (state.uploading || state.deletingInvoiceId != null) return
        val apiPackageId = resolvedPackageId ?: run {
            _state.update {
                it.copy(
                    confirmDeleteInvoiceId = null,
                    transientTitle = "Delete invoice",
                    transientMessage = "Package details are not ready. Try again.",
                )
            }
            return
        }
        _state.update { it.copy(confirmDeleteInvoiceId = null, deletingInvoiceId = invoiceId) }
        viewModelScope.launch {
            repo.deleteInvoice(apiPackageId, invoiceId)
                .onSuccess {
                    _state.update { it.copy(deletingInvoiceId = null) }
                    loadDetails(showLoading = false)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            deletingInvoiceId = null,
                            transientTitle = "Delete invoice",
                            transientMessage = e.message,
                        )
                    }
                }
        }
    }

    fun showCifInfo(show: Boolean) = _state.update { it.copy(showCifInfo = show) }

    fun showTransientMessage(message: String) =
        showTransient(title = "Upload Invoice", message = message)

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
        val apiPackageId = resolvedPackageId ?: run {
            _state.update { it.copy(damageReportError = "Package details are not ready. Try again.") }
            return
        }
        viewModelScope.launch {
            val description = state.damageReportDescription.trim()
            _state.update { it.copy(submittingDamageReport = true, damageReportError = null) }
            repo.reportDamage(apiPackageId, description, state.damageReportPhotos)
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
        cartMutations.add(
            line = detail.toCartLine(),
            scope = viewModelScope,
            onSuccess = { _state.update { it.copy(showAddedToCart = true) } },
            onFailure = { message ->
                _state.update {
                    it.copy(
                        transientTitle = "Cart update failed",
                        transientMessage = message,
                    )
                }
            },
        )
    }

    fun dismissAddedToCart() = _state.update { it.copy(showAddedToCart = false) }

    fun consumeTransientMessage() =
        _state.update { it.copy(transientTitle = null, transientMessage = null) }

    private suspend fun loadDetails(showLoading: Boolean) {
        if (showLoading) {
            _state.update { it.copy(loading = true, error = null) }
        }
        val apiPackageId = resolveApiPackageId().getOrElse { error ->
            _state.update {
                it.copy(
                    loading = false,
                    error = error.message ?: "Unable to resolve this package reference.",
                )
            }
            return
        }
        repo.packageDetails(apiPackageId)
            .mapCatching { detail ->
                check(detail.id.toString() == apiPackageId) {
                    "Package details did not match the requested package."
                }
                detail
            }
            .onSuccess { detail ->
                resolvedPackageId = detail.id.toString()
                // A timeline failure must not blank the whole screen; the rest
                // of the package detail is still real. But it must also not be
                // reported as an empty journey — those are different facts.
                val fetched = tracking.packageTimeline(detail.id)
                val ok = fetched?.isSuccess == true
                _state.update {
                    it.copy(
                        loading = false,
                        detail = detail,
                        // On failure keep whatever was already loaded rather
                        // than discarding a journey we successfully read before.
                        timeline = if (ok) fetched.getOrNull().orEmpty() else it.timeline,
                        timelineOutcome = if (ok) TimelineOutcome.LOADED else TimelineOutcome.FAILED,
                    )
                }
            }
            .onFailure { e ->
                if (showLoading) {
                    _state.update { it.copy(loading = false, error = e.message) }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            transientTitle = "Invoice",
                            transientMessage = e.message ?: "Invoice updated, but package details could not refresh.",
                        )
                    }
                }
            }
    }

    private suspend fun resolveApiPackageId(): Result<String> {
        resolvedPackageId?.let { return Result.success(it) }
        return resolvePackageReference(
            reference = packageId,
            packagesRepo = repo,
            shortlist = hubRepo::packagesShortlist,
            numericIdIsCanonical = true,
        ).map { resolved ->
            resolved.packageId.toString().also { resolvedPackageId = it }
        }
    }

    private fun showTransient(title: String, message: String) {
        _state.update { it.copy(transientTitle = title, transientMessage = message) }
    }

    private companion object {
        const val MAX_DAMAGE_DESCRIPTION_LENGTH = 5_000
        const val MAX_DAMAGE_PHOTOS = 5
        const val MAX_DAMAGE_PHOTO_BYTES = 10 * 1024 * 1024
        val DAMAGE_PHOTO_MIME_TYPES = setOf("image/png", "image/jpeg")
    }
}
