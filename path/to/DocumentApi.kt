import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.atomic.AtomicBoolean

class DocumentApi(private val api: Api) {

    fun getDocuments(userId: String): List<Document> {
        return api.getDocuments(userId)
    }

    fun uploadDocument(document: Document, userId: String): UploadResult {
        return api.uploadDocument(document, userId)
    }

    fun deleteDocument(documentId: String, userId: String): DeleteResult {
        return api.deleteDocument(documentId, userId)
    }

    fun getCurrentUser(): Session {
        return api.getCurrentUser()
    }
}