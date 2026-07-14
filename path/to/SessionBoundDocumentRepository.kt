import androidx.lifecycle.MutableStateFlow
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.atomic.AtomicBoolean

class SessionBoundDocumentRepository(private val userRepository: UserRepository, private val documentApi: DocumentApi) : DocumentRepository {

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    override val documents: StateFlow<List<Document>> = _documents

    private val _uploading = MutableStateFlow(false)
    override val uploading: StateFlow<Boolean> = _uploading

    private val _uploadProgress = MutableStateFlow(0)
    override val uploadProgress: StateFlow<Int> = _uploadProgress

    private val _deleteLoading = MutableStateFlow(false)
    override val deleteLoading: StateFlow<Boolean> = _deleteLoading

    private val _deleteSuccess = MutableStateFlow(false)
    override val deleteSuccess: StateFlow<Boolean> = _deleteSuccess

    private val _deleteError = MutableStateFlow(false)
    override val deleteError: StateFlow<Boolean> = _deleteError

    init {
        viewModelScope.launch {
            _documents.value = getDocuments()
        }
    }

    override fun getDocuments(): List<Document> {
        return documentApi.getDocuments(userRepository.getCurrentUser()!!.id)
    }

    override fun uploadDocument(document: Document): UploadResult {
        return documentApi.uploadDocument(document, userRepository.getCurrentUser()!!.id)
    }

    override fun deleteDocument(documentId: String): DeleteResult {
        return documentApi.deleteDocument(documentId, userRepository.getCurrentUser()!!.id)
    }

    override fun isSessionStale(): Boolean {
        return userRepository.getCurrentUser()!!.id != documentApi.getCurrentUser()!!.id
    }

    override fun isUploadStaged(): Boolean {
        return _uploading.value
    }

    override fun isDeleteStaged(): Boolean {
        return _deleteLoading.value
    }

    override fun cancelUpload() {
        _uploading.value = false
    }

    override fun cancelDelete() {
        _deleteLoading.value = false
    }
}