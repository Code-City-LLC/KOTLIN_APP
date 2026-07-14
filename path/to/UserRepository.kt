import androidx.lifecycle.MutableStateFlow
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.atomic.AtomicBoolean

class UserRepository(private val api: Api) {

    private val _currentUser = MutableStateFlow<Session?>(null)
    val currentUser: StateFlow<Session?> = _currentUser

    private val _legacyUserId = MutableStateFlow<String?>(null)
    val legacyUserId: StateFlow<String?> = _legacyUserId

    init {
        viewModelScope.launch {
            _currentUser.value = getCurrentUser()
            _legacyUserId.value = getLegacyUserId()
        }
    }

    fun getCurrentUser(): Session? {
        return api.getCurrentUser()
    }

    fun getLegacyUserId(): String? {
        return api.getLegacyUserId()
    }

    fun onSessionChanged(session: Session) {
        _currentUser.value = session
    }
}