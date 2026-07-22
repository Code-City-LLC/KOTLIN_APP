package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.push.PackageDeepLinkReference
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.cart.CartServerGateway
import com.ga.airdrop.feature.cart.DataCartServerGateway
import com.ga.airdrop.feature.cart.PackageCartMutationCoordinator
import com.ga.airdrop.feature.cart.isPackageCartEligibleStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PackageDetailsUiState(
    val loading: Boolean = true,
    val detail: ShipmentPackageDetail? = null,
    /** Positive ID confirmed by a matching GET /packages/{id} response. */
    val authoritativePackageId: Int? = null,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val uploading: Boolean = false,
    val deletingInvoiceId: Int? = null,
    val error: String? = null,
    val canRetry: Boolean = true,
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
    val hasAuthoritativeDetail: Boolean
        get() = authoritativePackageId?.let { it > 0 && detail?.id == it } == true

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
    val showChargesAndCart: Boolean
        get() = hasAuthoritativeDetail && (statusInt == 7 || statusInt == 18)

    val canAddToCart: Boolean
        get() = hasAuthoritativeDetail &&
            isPackageCartEligibleStatus(detail?.status?.trim()?.toIntOrNull())

    /**
     * Swift FigmaPackageDetailsViewController.updateReportDamageCTA:
     * visible only for Delivered packages (status 8), with production release
     * gated off until the backend rollout is promoted.
     */
    val showReportDamageCta: Boolean
        get() = hasAuthoritativeDetail && statusInt == 8 && reportDamageFeatureEnabled()

    /**
     * Invoice trash gating — parity with Swift FigmaPackageDetailsViewController
     * .canDeleteInvoices(for:) (L1473-1485): the delete/trash action is hidden
     * once a package reaches one of Swift's explicit terminal/locked status IDs,
     * with a status-name fallback for missing, stale, or non-numeric values.
     * Upload stays allowed at every status. UI/action-gating parity only (QC #14710).
     *
     * Deliberately independent of [showChargesAndCart]. Do not fold these together.
     */
    val canDeleteInvoices: Boolean
        get() {
            if (!hasAuthoritativeDetail) return false
            // Swift 5496ed0 tolerance: numeric lock is checked on BOTH the
            // status and statusName fields (either may carry the code), with
            // comma-decimal and floating values accepted.
            val values = listOfNotNull(detail?.status, detail?.statusName).map { it.trim() }
            if (values.any(::statusLocksInvoiceDeletion)) return false
            return true
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
    cartServer: CartServerGateway = DataCartServerGateway(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PackageDetailsUiState(exchangeRate = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<PackageDetailsUiState> = _state
    private val cartMutations = PackageCartMutationCoordinator(cartServer, sessionBoundary)
    private var observedOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var routeRetired = false
    private var loadGeneration = 0L
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                val previousSessionId = observedOwner?.sessionId
                observedOwner = changed
                if (previousSessionId == changed?.sessionId) return@collect
                routeRetired = true
                loadGeneration += 1
                detailJob?.cancel()
                _state.value = PackageDetailsUiState(
                    loading = false,
                    exchangeRate = _state.value.exchangeRate,
                    error = SESSION_RETIRED_MESSAGE,
                    canRetry = false,
                )
            }
        }
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
        val owner = currentScreenOwner()
        if (owner == null) {
            loadGeneration += 1
            detailJob?.cancel()
            _state.update {
                it.copy(
                    loading = false,
                    detail = null,
                    authoritativePackageId = null,
                    error = if (routeRetired) SESSION_RETIRED_MESSAGE else SIGN_IN_MESSAGE,
                    canRetry = false,
                )
            }
            return
        }
        if (!silent) detailJob?.cancel()
        val knownId = _state.value.authoritativePackageId.takeIf { silent }
        val generation = ++loadGeneration
        val job = viewModelScope.launch {
            loadDetails(
                showLoading = !silent,
                owner = owner,
                generation = generation,
                knownPackageId = knownId,
            )
        }
        if (!silent) detailJob = job
    }

    /** Multipart POST /packages/{id}/invoices — field "invoices[]", max 3 x 10MB. */
    fun uploadInvoices(files: List<InvoiceUploadFile>) {
        if (files.isEmpty()) return
        val (owner, authoritativeId) = currentAuthoritativeRequest() ?: return
        val state = _state.value
        if (state.loading || state.uploading || state.deletingInvoiceId != null) return
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
        if (!sessionBoundary.apply(owner) { _state.update { it.copy(uploading = true) } }) return
        viewModelScope.launch {
            repo.uploadInvoices(authoritativeId.toString(), files.take(allowed))
                .onSuccess {
                    if (!sessionBoundary.apply(owner) {
                            _state.update { it.copy(uploading = false) }
                        }
                    ) return@onSuccess
                    reloadAuthoritative(authoritativeId, owner)
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
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
    }

    fun requestDeleteInvoice(invoiceId: Int) {
        val state = _state.value
        if (!state.hasAuthoritativeDetail || currentAuthoritativeRequest() == null) return
        // Parity: Swift onTapDeleteInvoice (L1568-1575) re-checks canDeleteInvoices
        // and shows an explanatory alert instead of opening the confirm dialog once
        // the package is ready for pickup — keeps delete inert even if the trash
        // icon leaks through (belt-and-suspenders with the hidden UI control).
        if (!state.canDeleteInvoices) {
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
        val (owner, authoritativeId) = currentAuthoritativeRequest() ?: return
        val state = _state.value
        if (state.uploading || state.deletingInvoiceId != null) return
        if (!sessionBoundary.apply(owner) {
                _state.update {
                    it.copy(confirmDeleteInvoiceId = null, deletingInvoiceId = invoiceId)
                }
            }
        ) return
        viewModelScope.launch {
            repo.deleteInvoice(authoritativeId.toString(), invoiceId)
                .onSuccess {
                    if (!sessionBoundary.apply(owner) {
                            _state.update { it.copy(deletingInvoiceId = null) }
                        }
                    ) return@onSuccess
                    reloadAuthoritative(authoritativeId, owner)
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
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
    }

    fun showCifInfo(show: Boolean) {
        if (show && currentAuthoritativeRequest() == null) return
        _state.update { it.copy(showCifInfo = show) }
    }

    fun showTransientMessage(message: String) =
        showTransient(title = "Upload Invoice", message = message)

    fun showReportDamageSheet(show: Boolean) {
        if (show && (!_state.value.showReportDamageCta || currentAuthoritativeRequest() == null)) return
        _state.update { it.copy(showReportDamageSheet = show, damageReportError = null) }
    }

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
        val (owner, authoritativeId) = currentAuthoritativeRequest() ?: return
        if (state.submittingDamageReport || !state.showReportDamageCta) return
        viewModelScope.launch {
            val description = state.damageReportDescription.trim()
            if (!sessionBoundary.apply(owner) {
                    _state.update {
                        it.copy(submittingDamageReport = true, damageReportError = null)
                    }
                }
            ) return@launch
            repo.reportDamage(authoritativeId.toString(), description, state.damageReportPhotos)
                .onSuccess {
                    sessionBoundary.apply(owner) {
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
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(
                                submittingDamageReport = false,
                                damageReportError = e.message ?: "Failed to submit damage report.",
                            )
                        }
                    }
                }
        }
    }

    fun dismissDamageReportSubmitted() =
        _state.update { it.copy(showDamageReportSubmitted = false) }

    fun addToCart() {
        val state = _state.value
        if (!state.canAddToCart || currentAuthoritativeRequest() == null) return
        val detail = state.detail ?: return
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

    private suspend fun reloadAuthoritative(
        authoritativeId: Int,
        owner: AuthenticatedSessionOwner,
    ) {
        if (!isOwnerCurrent(owner)) return
        val generation = ++loadGeneration
        loadDetails(
            showLoading = false,
            owner = owner,
            generation = generation,
            knownPackageId = authoritativeId,
        )
    }

    private suspend fun loadDetails(
        showLoading: Boolean,
        owner: AuthenticatedSessionOwner,
        generation: Long,
        knownPackageId: Int?,
    ) {
        if (showLoading && !applyRequest(owner, generation) {
                _state.update {
                    it.copy(
                        loading = true,
                        detail = null,
                        authoritativePackageId = null,
                        error = null,
                        canRetry = true,
                    )
                }
            }
        ) return

        val resolvedId = knownPackageId ?: resolvePackageId(owner, generation).getOrElse { error ->
            applyLoadFailure(owner, generation, showLoading, error)
            return
        }
        if (resolvedId <= 0 || !isRequestCurrent(owner, generation)) return

        val detail = repo.packageDetails(resolvedId.toString()).getOrElse { error ->
            applyLoadFailure(owner, generation, showLoading, error)
            return
        }
        if (detail.id <= 0 || detail.id != resolvedId) {
            applyLoadFailure(
                owner,
                generation,
                showLoading,
                IllegalStateException("Package details could not be verified. Please retry."),
            )
            return
        }
        applyRequest(owner, generation) {
            _state.update {
                it.copy(
                    loading = false,
                    detail = detail,
                    authoritativePackageId = resolvedId,
                    error = null,
                    canRetry = true,
                )
            }
        }
    }

    private suspend fun resolvePackageId(
        owner: AuthenticatedSessionOwner,
        generation: Long,
    ): Result<Int> {
        PackageDeepLinkReference.positiveId(packageId)?.let { return Result.success(it) }
        val alias = packageAliasSearchTerm(packageId)
            ?: return Result.failure(
                IllegalArgumentException(
                    "Package reference missing or invalid. Go back and open the package again.",
                ),
            )
        return packageReferenceSearchRows(
            repository = repo,
            alias = alias,
            isRequestCurrent = { isRequestCurrent(owner, generation) },
        ).mapCatching { rows ->
            exactPackageReferenceMatch(rows, alias)?.id
                ?: error("No package found for $alias. Check the tracking code and try again.")
        }
    }

    private fun applyLoadFailure(
        owner: AuthenticatedSessionOwner,
        generation: Long,
        showLoading: Boolean,
        error: Throwable,
    ) {
        val message = error.message?.takeIf(String::isNotBlank)
            ?: "Unable to load package details. Check your connection and try again."
        applyRequest(owner, generation) {
            if (showLoading) {
                _state.update {
                    it.copy(
                        loading = false,
                        detail = null,
                        authoritativePackageId = null,
                        error = message,
                        canRetry = true,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        transientTitle = "Invoice",
                        transientMessage =
                            "$message Invoice updated, but package details could not refresh.",
                    )
                }
            }
        }
    }

    private fun currentScreenOwner(): AuthenticatedSessionOwner? {
        if (routeRetired) return null
        val captured = sessionBoundary.capture() ?: return null
        val observed = observedOwner ?: return null
        return captured.takeIf {
            it.sessionId == observed.sessionId && sessionBoundary.isCurrent(it)
        }
    }

    private fun currentAuthoritativeRequest(): Pair<AuthenticatedSessionOwner, Int>? {
        val current = _state.value
        if (!current.hasAuthoritativeDetail) return null
        val id = current.authoritativePackageId ?: return null
        val owner = currentScreenOwner() ?: return null
        return owner to id
    }

    private fun isOwnerCurrent(owner: AuthenticatedSessionOwner): Boolean =
        !routeRetired &&
            observedOwner?.sessionId == owner.sessionId &&
            sessionBoundary.isCurrent(owner)

    private fun isRequestCurrent(
        owner: AuthenticatedSessionOwner,
        generation: Long,
    ): Boolean = generation == loadGeneration && isOwnerCurrent(owner)

    private fun applyRequest(
        owner: AuthenticatedSessionOwner,
        generation: Long,
        action: () -> Unit,
    ): Boolean {
        if (!isRequestCurrent(owner, generation)) return false
        var ran = false
        val accepted = sessionBoundary.apply(owner) {
            if (generation == loadGeneration && observedOwner?.sessionId == owner.sessionId) {
                action()
                ran = true
            }
        }
        return accepted && ran
    }

    private fun showTransient(title: String, message: String) {
        _state.update { it.copy(transientTitle = title, transientMessage = message) }
    }

    private companion object {
        const val SIGN_IN_MESSAGE = "Sign in to load package details."
        const val SESSION_RETIRED_MESSAGE =
            "Package details are no longer available for this session. Go back and open the package again."
        const val MAX_DAMAGE_DESCRIPTION_LENGTH = 5_000
        const val MAX_DAMAGE_PHOTOS = 5
        const val MAX_DAMAGE_PHOTO_BYTES = 10 * 1024 * 1024
        val DAMAGE_PHOTO_MIME_TYPES = setOf("image/png", "image/jpeg")
    }
}
