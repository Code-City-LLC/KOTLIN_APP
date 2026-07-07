package com.ga.airdrop.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

interface DocumentsRepository {
    suspend fun userDocuments(): Result<Map<String, MoreDocumentFile>>

    suspend fun uploadUserDocument(
        docType: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Result<Unit>

    suspend fun deleteUserDocument(identifier: String): Result<Unit>
}

/**
 * FigmaDocumentsViewController behavior: GET /user/documents to fill the
 * five RN-defined slots, multipart upload per slot, delete by doc_type
 * (falling back to the slot raw type) with confirm + reload.
 */
class DocumentsViewModel(
    private val repository: DocumentsRepository = MoreRepository(),
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state

    init {
        // Swift refreshLegacyDownloadUserID: the legacy Contract/1583/Custom
        // form downloads need the session user id. Non-fatal — on failure the
        // legacy fallback simply stays unavailable (uploaded files still open).
        viewModelScope.launch {
            userRepository.currentUser().onSuccess { user ->
                _state.update { it.copy(legacyUserId = user.id) }
            }
        }
    }

    fun load() {
        if (_state.value.loading || _state.value.refreshing) return
        _state.update { it.copy(loading = true, refreshing = false) }
        fetchDocuments()
    }

    fun refresh() {
        if (_state.value.loading || _state.value.refreshing) return
        _state.update { it.copy(loading = false, refreshing = true) }
        fetchDocuments()
    }

    private fun fetchDocuments() {
        viewModelScope.launch {
            repository.userDocuments()
                .onSuccess { files ->
                    _state.update {
                        it.copy(
                            files = files,
                            loading = false,
                            refreshing = false,
                        )
                    }
                }
                // Render slots without files so Upload still works (RN parity on 401).
                .onFailure {
                    _state.update { it.copy(loading = false, refreshing = false) }
                }
        }
    }

    fun stageUpload(slot: DocumentSlot, fileName: String, mimeType: String, bytes: ByteArray) {
        _state.update {
            it.copy(
                pendingUploads = it.pendingUploads + (
                    slot.docType to PendingDocumentUpload(
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes,
                    )
                    ),
            )
        }
    }

    fun clearPendingUpload(slot: DocumentSlot) {
        _state.update { it.copy(pendingUploads = it.pendingUploads - slot.docType) }
    }

    fun commitPendingUpload(slot: DocumentSlot) {
        val upload = _state.value.pendingUploads[slot.docType] ?: return
        uploadBytes(
            slot = slot,
            fileName = upload.fileName,
            mimeType = upload.mimeType,
            bytes = upload.bytes,
            clearPending = true,
        )
    }

    private fun uploadBytes(
        slot: DocumentSlot,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        clearPending: Boolean,
    ) {
        _state.update { it.copy(uploadingType = slot.docType) }
        viewModelScope.launch {
            repository.uploadUserDocument(slot.docType, fileName, mimeType, bytes)
                .onSuccess {
                    _state.update {
                        it.copy(
                            pendingUploads = if (clearPending) {
                                it.pendingUploads - slot.docType
                            } else {
                                it.pendingUploads
                            },
                            uploadingType = null,
                            alert = "Uploaded" to "${slot.title} was uploaded.",
                        )
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            uploadingType = null,
                            alert = "Upload failed" to (e.message ?: "Please try again."),
                        )
                    }
                }
        }
    }

    fun delete(slot: DocumentSlot) {
        val file = _state.value.files[slot.docType] ?: return
        val identifier = file.docType?.takeIf { it.isNotEmpty() } ?: slot.docType
        viewModelScope.launch {
            repository.deleteUserDocument(identifier)
                .onSuccess {
                    _state.update { it.copy(alert = "Deleted" to "${slot.title} was removed.") }
                    load()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(alert = "Delete failed" to (e.message ?: "Please try again."))
                    }
                }
        }
    }

    fun showAlert(title: String, message: String) =
        _state.update { it.copy(alert = title to message) }

    fun dismissAlert() = _state.update { it.copy(alert = null) }
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
