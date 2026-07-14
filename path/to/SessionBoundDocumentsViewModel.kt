import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.atomic.AtomicBoolean

class SessionBoundDocumentsViewModel(private val userRepository: UserRepository, private val documentRepository: DocumentRepository) : ViewModel() {

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents

    private val _legacyUserId = MutableStateFlow<String?>(null)
    val legacyUserId: StateFlow<String?> = _legacyUserId

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress

    private val _deleteLoading = MutableStateFlow(false)
    val deleteLoading: StateFlow<Boolean> = _deleteLoading

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess

    private val _deleteError = MutableStateFlow(false)
    val deleteError: StateFlow<Boolean> = _deleteError

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _isSessionStale = MutableStateFlow(false)
    val isSessionStale: StateFlow<Boolean> = _isSessionStale

    private val _isUploadStaged = MutableStateFlow(false)
    val isUploadStaged: StateFlow<Boolean> = _isUploadStaged

    private val _isDeleteStaged = MutableStateFlow(false)
    val isDeleteStaged: StateFlow<Boolean> = _isDeleteStaged

    init {
        viewModelScope.launch {
            _session.value = userRepository.getCurrentUser()
            _documents.value = documentRepository.getDocuments()
            _legacyUserId.value = userRepository.getLegacyUserId()
        }
    }

    fun loadDocuments() {
        viewModelScope.launch {
            _session.value = userRepository.getCurrentUser()
            _documents.value = documentRepository.getDocuments()
        }
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            _session.value = userRepository.getCurrentUser()
            _documents.value = documentRepository.getDocuments()
        }
    }

    fun uploadDocument(document: Document) {
        viewModelScope.launch {
            _session.value = userRepository.getCurrentUser()
            _uploading.value = true
            val uploadResult = documentRepository.uploadDocument(document)
            if (uploadResult.isSuccess) {
                _uploadProgress.value = uploadResult.progress
            } else {
                _uploadProgress.value = 0
            }
            _isUploadStaged.value = false
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            _session.value = userRepository.getCurrentUser()
            _deleteLoading.value = true
            val deleteResult = documentRepository.deleteDocument(documentId)
            if (deleteResult.isSuccess) {
                _deleteSuccess.value = true
            } else {
                _deleteError.value = true
            }
            _isDeleteStaged.value = false
        }
    }

    fun onSessionChanged(session: Session) {
        _session.value = session
        _isSessionStale.value = false
        _isUploadStaged.value = false
        _isDeleteStaged.value = false
        _uploading.value = false
        _deleteLoading.value = false
        _deleteSuccess.value = false
        _deleteError.value = false
    }

    fun isSessionStale(): Boolean {
        return _session.value?.id != userRepository.getCurrentUser()?.id
    }

    fun isUploadStaged(): Boolean {
        return _isUploadStaged.value
    }

    fun isDeleteStaged(): Boolean {
        return _isDeleteStaged.value
    }

    fun cancelUpload() {
        _isUploadStaged.value = false
        _uploading.value = false
    }

    fun cancelDelete() {
        _isDeleteStaged.value = false
        _deleteLoading.value = false
    }
}