package com.ga.airdrop.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedRequest
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.changeTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Document slot descriptor — RN DocumentsView DOCUMENTS const, verbatim.
 * `docType` is the API field/doc_type string (Swift UserDocumentType raw).
 */
data class DocumentSlot(
    val docType: String,
    val title: String,
    val description: String,
    val detailDescription: String,
)

/** Swift/RN canonical order: Contract → 1583 → Custom Form → ID Card → TRN. */
val DOCUMENT_SLOTS = listOf(
    DocumentSlot(
        docType = "airdrop_contract",
        title = "AirDrop Contract",
        description = "Terms and conditions for using AirDrop’s services.",
        detailDescription = "The AirDrop Contract is a service agreement between you and AirDrop Limited that outlines the terms, conditions, and responsibilities of both parties.\n\nIt establishes your authorization for AirDrop to ship, handle, and deliver your packages while defining how fees, liabilities, and services are managed.\n\nThis document protects both the customer and the company by ensuring transparency and compliance with shipping policies.",
    ),
    DocumentSlot(
        docType = "file_1583",
        title = "1583 Form",
        description = "USPS form allowing AirDrop to receive your packages.",
        detailDescription = "USPS Form 1583 is a U.S. Postal Service authorization form that allows a Commercial Mail Receiving Agency (CMRA)—such as a shipping or mailbox company—to receive mail on your behalf.\n\nIt’s a mandatory federal requirement for anyone opening a virtual mailbox, mail forwarding, or package receiving service in the United States.",
    ),
    DocumentSlot(
        docType = "authorization_form",
        title = "Custom Form",
        description = "Authorizes AirDrop to handle customs clearance and duties on your behalf.",
        detailDescription = "The Customs Form authorizes AirDrop Limited and/or its licensed customs agents to act on your behalf during customs clearance.\nThis includes submitting import documents, declaring shipment values, and paying duties or taxes as required by local customs authorities.\nIt ensures your shipments are cleared efficiently in compliance with Jamaica Customs and international trade regulations.",
    ),
    DocumentSlot(
        docType = "id_card_form",
        title = "ID Card",
        description = "Photo ID used to verify your identity.",
        detailDescription = "A government-issued photo ID is required to verify your identity for customs, USPS, and AirDrop account compliance.\nThis helps confirm that the name on your shipping account matches official identification and prevents unauthorized access to your packages.",
    ),
    DocumentSlot(
        docType = "trn",
        title = "TRN",
        description = "Upload your Tax Registration Number (TRN) for customs and tax processing.",
        detailDescription = "Your TRN serves as your official identification number with the Jamaica Customs Agency and is used to identify you as the importer.\n\nAirDrop cannot make customs declarations or clear shipments on your behalf without a valid TRN on file.",
    ),
)

data class DocumentsUiState(
    /** Non-secret owner generation used to discard account-A UI intents. */
    val ownerSessionId: String? = null,
    val files: Map<String, MoreDocumentFile> = emptyMap(),
    /** Session user id for the legacy server-generated form downloads. */
    val legacyUserId: Int? = null,
    val pendingUploads: Map<String, PendingDocumentUpload> = emptyMap(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val uploadingType: String? = null,
    val alert: Pair<String, String>? = null,
)

data class PendingDocumentUpload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val ownerSessionId: String,
)

class DocumentUploadClaim internal constructor(
    val slot: DocumentSlot,
    internal val ownerSessionId: String,
)

class DocumentDeleteClaim internal constructor(
    val slot: DocumentSlot,
    internal val ownerSessionId: String,
    internal val identifier: String,
)

interface DocumentsRepository {
    suspend fun currentUserId(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Int?>

    suspend fun userDocuments(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Map<String, MoreDocumentFile>>

    suspend fun uploadUserDocument(
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit>

    suspend fun deleteUserDocument(
        identifier: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit>
}

/**
 * FigmaDocumentsViewController behavior: GET /user/documents to fill the
 * five RN-defined slots, multipart upload per slot, delete by doc_type
 * (falling back to the slot raw type) with confirm + reload.
 */
class DocumentsViewModel(
    private val repository: DocumentsRepository = MoreRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private val _state = MutableStateFlow(
        DocumentsUiState(ownerSessionId = sessionOwner?.sessionId),
    )
    val state: StateFlow<DocumentsUiState> = _state

    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var legacyUserJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionJobs.replaceSession()
                legacyUserJob = null
                loadJob = null
                sessionOwner = changed
                resetOwnedState()
                if (changed != null) {
                    loadLegacyUserId()
                    load()
                }
            }
        }
        loadLegacyUserId()
    }

    fun load() {
        fetchDocuments(refreshing = false)
    }

    fun refresh() {
        fetchDocuments(refreshing = true)
    }

    private fun loadLegacyUserId() {
        if (legacyUserJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        legacyUserJob = sessionJobs.launch {
            repository.currentUserId(requestOwner.provenance)
                .onSuccess { userId ->
                    if (userId != null && !sessionBoundary.bindAccountId(owner, userId)) {
                        return@onSuccess
                    }
                    sessionBoundary.apply(owner) {
                        _state.update { it.copy(legacyUserId = userId) }
                    }
                }
        }
    }

    private fun fetchDocuments(refreshing: Boolean) {
        if (loadJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        if (!sessionBoundary.apply(owner) {
                _state.update { it.copy(loading = !refreshing, refreshing = refreshing) }
            }
        ) return
        loadJob = sessionJobs.launch {
            repository.userDocuments(requestOwner.provenance)
                .onSuccess { files ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(
                                files = files,
                                loading = false,
                                refreshing = false,
                            )
                        }
                    }
                }
                // Render slots without files so Upload still works (RN parity on 401).
                .onFailure {
                    sessionBoundary.apply(owner) {
                        _state.update { it.copy(loading = false, refreshing = false) }
                    }
                }
        }
    }

    fun claimUpload(slot: DocumentSlot): DocumentUploadClaim? {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return null
        return DocumentUploadClaim(slot, owner.sessionId)
    }

    fun stageUpload(
        claim: DocumentUploadClaim,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        updateOwnedState(claim.ownerSessionId) {
            it.copy(
                pendingUploads = it.pendingUploads + (
                    claim.slot.docType to PendingDocumentUpload(
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes.copyOf(),
                        ownerSessionId = claim.ownerSessionId,
                    )
                ),
            )
        }
    }

    fun clearPendingUpload(slot: DocumentSlot) {
        updateOwnedState { it.copy(pendingUploads = it.pendingUploads - slot.docType) }
    }

    fun commitPendingUpload(slot: DocumentSlot) {
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        var upload: PendingDocumentUpload? = null
        if (!sessionBoundary.apply(owner) {
                val candidate = _state.value.pendingUploads[slot.docType]
                if (candidate?.ownerSessionId == owner.sessionId) {
                    upload = candidate
                    _state.update { it.copy(uploadingType = slot.docType) }
                } else if (candidate != null) {
                    _state.update { it.copy(pendingUploads = it.pendingUploads - slot.docType) }
                }
            }
        ) return
        val claimedUpload = upload ?: return
        sessionJobs.launch {
            repository.uploadUserDocument(
                docType = slot.docType,
                fileName = claimedUpload.fileName,
                mimeType = claimedUpload.mimeType,
                bytes = claimedUpload.bytes,
                expectedSession = requestOwner.provenance,
            )
                .onSuccess {
                    val applied = sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(
                                pendingUploads = it.pendingUploads - slot.docType,
                                uploadingType = null,
                                alert = "Uploaded" to "${slot.title} was uploaded.",
                            )
                        }
                    }
                    if (applied) load()
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(
                                uploadingType = null,
                                alert = "Upload failed" to (e.message ?: "Please try again."),
                            )
                        }
                    }
                }
        }
    }

    fun claimDelete(slot: DocumentSlot): DocumentDeleteClaim? {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return null
        var identifier: String? = null
        if (!sessionBoundary.apply(owner) {
                val file = _state.value.files[slot.docType] ?: return@apply
                identifier = file.docType?.takeIf { it.isNotEmpty() } ?: slot.docType
            }
        ) return null
        return identifier?.let { DocumentDeleteClaim(slot, owner.sessionId, it) }
    }

    fun delete(claim: DocumentDeleteClaim) {
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        if (owner.sessionId != claim.ownerSessionId) return
        sessionJobs.launch {
            repository.deleteUserDocument(claim.identifier, requestOwner.provenance)
                .onSuccess {
                    val applied = sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(alert = "Deleted" to "${claim.slot.title} was removed.")
                        }
                    }
                    if (applied) load()
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(alert = "Delete failed" to (e.message ?: "Please try again."))
                        }
                    }
                }
        }
    }

    fun showAlert(title: String, message: String) =
        updateOwnedState { it.copy(alert = title to message) }

    fun showUploadFailure(claim: DocumentUploadClaim, message: String) =
        updateOwnedState(claim.ownerSessionId) {
            it.copy(alert = "Upload failed" to message)
        }

    fun dismissAlert() = updateOwnedState { it.copy(alert = null) }

    fun openDocument(
        slot: DocumentSlot,
        legacyBase: String,
        onOpen: (url: String, title: String) -> Unit,
    ) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        sessionBoundary.apply(owner) {
            val current = _state.value
            val url = (
                current.files[slot.docType]?.fileUrl
                    ?: legacyDownloadUrl(
                        docType = slot.docType,
                        userId = current.legacyUserId?.toString(),
                        legacyBase = legacyBase,
                    )
                )?.replaceFirst("http://", "https://")
            if (url.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        alert = "Not available" to
                            "No download link is available for ${slot.title} yet.",
                    )
                }
            } else {
                onOpen(url, slot.title)
            }
        }
    }

    private fun updateOwnedState(
        expectedSessionId: String? = null,
        transform: (DocumentsUiState) -> DocumentsUiState,
    ) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        if (expectedSessionId != null && owner.sessionId != expectedSessionId) return
        sessionBoundary.apply(owner) { _state.update(transform) }
    }

    private fun resetOwnedState() {
        _state.value = DocumentsUiState(ownerSessionId = sessionOwner?.sessionId)
    }
}

/**
 * Legacy server-generated form download URL — Swift
 * FigmaDocumentsViewController.legacyDownloadURL(for:) (:770). Only the first
 * three slots have server-generated legacy forms; ID Card and TRN return null.
 * A blank/absent user id returns null (download stays disabled).
 */
internal fun legacyDownloadUrl(docType: String, userId: String?, legacyBase: String): String? {
    val id = userId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val base = legacyBase.trimEnd('/')
    return when (docType) {
        "airdrop_contract" -> "$base/api_download-contract-form.php?user_documenttype=$id"
        "file_1583" -> "$base/api_download_file_1583.php?user_id=$id"
        "authorization_form" -> "$base/api_form_authorization.php?user_id=$id"
        else -> null
    }
}
