package com.ga.airdrop.feature.more

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** Figma 40000975:7748 scroll order: Contract → 1583 → ID Card → TRN → Custom. */
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
    DocumentSlot(
        docType = "authorization_form",
        title = "Custom Form",
        description = "Authorizes AirDrop to handle customs clearance and duties on your behalf.",
        detailDescription = "The Customs Form authorizes AirDrop Limited and/or its licensed customs agents to act on your behalf during customs clearance.\nThis includes submitting import documents, declaring shipment values, and paying duties or taxes as required by local customs authorities.\nIt ensures your shipments are cleared efficiently in compliance with Jamaica Customs and international trade regulations.",
    ),
)

data class DocumentsUiState(
    val files: Map<String, MoreDocumentFile> = emptyMap(),
    val loading: Boolean = false,
    val uploadingType: String? = null,
    val alert: Pair<String, String>? = null,
)

/**
 * FigmaDocumentsViewController behavior: GET /user/documents to fill the
 * five RN-defined slots, multipart upload per slot, delete by doc_type
 * (falling back to the slot raw type) with confirm + reload.
 */
class DocumentsViewModel(
    private val repository: MoreRepository = MoreRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state

    init {
        load()
    }

    fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            repository.userDocuments()
                .onSuccess { files -> _state.update { it.copy(files = files, loading = false) } }
                // Render slots without files so Upload still works (RN parity on 401).
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }

    /**
     * SAF-backed upload — reads the selected file's bytes and display name off
     * the main thread (avoids ANR), then delegates to the byte-based upload.
     */
    fun upload(slot: DocumentSlot, uri: Uri, resolver: ContentResolver) {
        _state.update { it.copy(uploadingType = slot.docType) }
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext null
                var fileName = "document"
                runCatching {
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) fileName = cursor.getString(index)
                    }
                }
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                Triple(fileName, mime, bytes)
            }
            if (read == null) {
                _state.update {
                    it.copy(
                        uploadingType = null,
                        alert = "Upload failed" to "Could not read the selected file.",
                    )
                }
                return@launch
            }
            val (fileName, mime, bytes) = read
            repository.uploadUserDocument(slot.docType, fileName, mime, bytes)
                .onSuccess {
                    _state.update {
                        it.copy(
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

    fun upload(slot: DocumentSlot, fileName: String, mimeType: String, bytes: ByteArray) {
        _state.update { it.copy(uploadingType = slot.docType) }
        viewModelScope.launch {
            repository.uploadUserDocument(slot.docType, fileName, mimeType, bytes)
                .onSuccess {
                    _state.update {
                        it.copy(
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
