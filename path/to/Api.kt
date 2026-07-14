import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.atomic.AtomicBoolean

class Api(private val gson: Gson) {

    fun getCurrentUser(): Session {
        // Implement API call to get current user
        return Session()
    }

    fun getDocuments(userId: String): List<Document> {
        // Implement API call to get documents
        return listOf(Document())
    }

    fun uploadDocument(document: Document, userId: String): UploadResult {
        // Implement API call to upload document
        return UploadResult()
    }

    fun deleteDocument(documentId: String, userId: String): DeleteResult {
        // Implement API call to delete document
        return DeleteResult()
    }

    fun getLegacyUserId(): String? {
        // Implement API call to get legacy user ID
        return null
    }
}