package com.ga.airdrop.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ga.airdrop.core.prefs.SessionPreferences
import com.ga.airdrop.core.session.SessionStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sanctum bearer-token storage. Android equivalent of the Swift
 * AuthTokenStore (Keychain-backed): encrypted at rest, survives restarts,
 * cleared on logout.
 */
object AuthTokenStore {

    data class Snapshot(
        val token: String?,
        val revision: Long,
        val sessionId: String? = null,
        val accountId: Int? = null,
    )

    data class RequestProvenance(
        val revision: Long,
        val sessionId: String,
        val accountId: Int? = null,
    )

    class RequestDispatch internal constructor(
        internal val id: Long,
        private val cancelAction: () -> Unit,
    ) {
        @Volatile
        var isValid: Boolean = true
            private set

        internal fun invalidate() {
            isValid = false
            runCatching(cancelAction)
        }
    }

    private const val PREFS = "airdrop_auth"
    private const val KEY_TOKEN = "api_token"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_ACCOUNT_ID = "account_id"

    private lateinit var prefs: SharedPreferences
    private val transitionLock = Any()
    private val stateLock = Any()
    private val activeRequests = mutableMapOf<Long, RequestDispatch>()
    private var nextRequestId = 0L
    private var revision = 0L
    private var sessionId: String? = null
    private var accountId: Int? = null

    private val _token = MutableStateFlow<String?>(null)
    val tokenFlow: StateFlow<String?> get() = _token
    private val _snapshot = MutableStateFlow(Snapshot(null, 0L, null, null))
    val snapshotFlow: StateFlow<Snapshot> get() = _snapshot

    val token: String? get() = _token.value

    fun init(context: Context) {
        synchronized(transitionLock) {
            val restoredSessionId = synchronized(stateLock) {
                invalidateActiveRequestsLocked()
                prefs = runCatching {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                }.getOrElse {
                    // Keystore corruption fallback: plain prefs beat a hard crash at
                    // launch; the token is re-issued at next login anyway.
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                }
                val storedToken = prefs.getString(KEY_TOKEN, null)
                _token.value = storedToken
                sessionId = if (storedToken == null) {
                    prefs.edit().remove(KEY_SESSION_ID).remove(KEY_ACCOUNT_ID).apply()
                    null
                } else {
                    prefs.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() }
                        ?: newSessionId().also { prefs.edit().putString(KEY_SESSION_ID, it).commit() }
                }
                accountId = if (storedToken == null || !prefs.contains(KEY_ACCOUNT_ID)) {
                    null
                } else {
                    prefs.getInt(KEY_ACCOUNT_ID, 0).takeIf { it > 0 }
                }
                revision += 1
                SessionStore.initializeAuthenticatedSession(sessionId)
                publishSnapshot()
                sessionId
            }
            SessionPreferences.init(context, restoredSessionId)
        }
    }

    fun save(token: String, authenticatedAccountId: Int? = null) {
        synchronized(transitionLock) {
            val replacementSessionId = newSessionId()
            SessionPreferences.replaceSession(replacementSessionId)
            synchronized(stateLock) {
                invalidateActiveRequestsLocked()
                // Update the in-memory flow first, then persist only if prefs is bound.
                // A background service / ContentProvider can run before
                // Application.onCreate() calls init(); touching lateinit prefs then
                // would throw UninitializedPropertyAccessException (BUG_AUDIT C8).
                _token.value = token
                sessionId = replacementSessionId
                accountId = authenticatedAccountId?.takeIf { it > 0 }
                revision += 1
                SessionStore.onAuthenticatedSessionChanged(sessionId)
                if (::prefs.isInitialized) {
                    prefs.edit()
                        .putString(KEY_TOKEN, token)
                        .putString(KEY_SESSION_ID, sessionId)
                        .apply {
                            if (accountId != null) putInt(KEY_ACCOUNT_ID, accountId!!) else remove(KEY_ACCOUNT_ID)
                        }
                        .commit()
                }
                publishSnapshot()
            }
        }
    }

    /** Rotates a bearer only when the exact expected session generation is current. */
    fun rotate(expected: Snapshot, newToken: String): Snapshot? = synchronized(transitionLock) {
        synchronized(stateLock) state@{
            if (newToken.isBlank() || currentSnapshot() != expected || expected.sessionId == null) {
                return@state null
            }
            invalidateActiveRequestsLocked()
            _token.value = newToken
            revision += 1
            if (::prefs.isInitialized) {
                prefs.edit()
                    .putString(KEY_TOKEN, newToken)
                    .putString(KEY_SESSION_ID, sessionId)
                    .commit()
            }
            currentSnapshot().also { _snapshot.value = it }
        }
    }

    fun clear() = synchronized(transitionLock) {
        synchronized(stateLock) { clearLocked() }
        SessionPreferences.replaceSession(null)
    }

    /** Clears only if the exact request generation still owns the session. */
    fun clear(expected: Snapshot): Boolean {
        return synchronized(transitionLock) {
            val cleared = synchronized(stateLock) state@{
                if (currentSnapshot() != expected) return@state false
                clearLocked()
                true
            }
            if (cleared) SessionPreferences.replaceSession(null)
            cleared
        }
    }

    private fun clearLocked() {
        invalidateActiveRequestsLocked()
        _token.value = null
        sessionId = null
        accountId = null
        revision += 1
        SessionStore.onAuthenticatedSessionChanged(null)
        if (::prefs.isInitialized) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_SESSION_ID).remove(KEY_ACCOUNT_ID).commit()
        }
        publishSnapshot()
    }

    fun snapshot(): Snapshot = synchronized(stateLock) {
        currentSnapshot()
    }

    fun currentSessionId(): String? = synchronized(stateLock) {
        sessionId.takeIf { _token.value != null }
    }

    fun bindAccountId(expectedSessionId: String, authenticatedAccountId: Int): Boolean {
        return synchronized(transitionLock) transition@{
            val expectedRevision = synchronized(stateLock) state@{
                if (
                    _token.value == null ||
                    sessionId != expectedSessionId ||
                    authenticatedAccountId <= 0 ||
                    (accountId != null && accountId != authenticatedAccountId)
                ) return@state null
                if (accountId == authenticatedAccountId) return@state revision
                accountId = authenticatedAccountId
                revision
            } ?: return@transition false
            val persisted = !::prefs.isInitialized ||
                prefs.edit().putInt(KEY_ACCOUNT_ID, authenticatedAccountId).commit()
            synchronized(stateLock) {
                val stillOwned =
                    _token.value != null &&
                        revision == expectedRevision &&
                        sessionId == expectedSessionId &&
                        accountId == authenticatedAccountId
                if (!persisted && stillOwned) accountId = null
                if (persisted && stillOwned) publishSnapshot()
                persisted && stillOwned
            }
        }
    }

    fun isCurrentSession(expectedSessionId: String): Boolean = synchronized(stateLock) {
        _token.value != null && sessionId == expectedSessionId
    }

    /**
     * Linearizes a short in-memory result application against login/logout.
     * Callers must never perform network, disk, or other blocking work here.
     */
    fun applyIfCurrentSession(expectedSessionId: String, action: () -> Unit): Boolean =
        synchronized(stateLock) {
            if (_token.value == null || sessionId != expectedSessionId) return false
            action()
            true
        }

    /**
     * Keeps an auth generation stable while performing persistence without
     * holding the short in-memory state lock. Login/logout waits on the outer
     * transition lock, so a disk write cannot land after account replacement.
     */
    fun runWhileCurrentSession(
        expectedSessionId: String,
        expectedAccountId: Int?,
        action: () -> Boolean,
    ): Boolean =
        synchronized(transitionLock) {
            val current = synchronized(stateLock) {
                _token.value != null &&
                    sessionId == expectedSessionId &&
                    accountId == expectedAccountId
            }
            if (!current) return@synchronized false
            val actionSucceeded = action()
            val stillOwned = synchronized(stateLock) {
                _token.value != null &&
                    sessionId == expectedSessionId &&
                    accountId == expectedAccountId
            }
            actionSucceeded && stillOwned
        }

    /** Binds request dispatch to one session without extending into response-body handling. */
    fun acquireRequest(expected: Snapshot, cancel: () -> Unit): RequestDispatch? = synchronized(stateLock) {
        if (currentSnapshot() != expected || expected.token == null) return null
        RequestDispatch(++nextRequestId, cancel).also { activeRequests[it.id] = it }
    }

    fun finishRequest(request: RequestDispatch): Boolean = synchronized(stateLock) {
        activeRequests.remove(request.id)
        request.isValid
    }

    fun abandonRequest(request: RequestDispatch) = synchronized(stateLock) {
        activeRequests.remove(request.id)
        Unit
    }

    private fun invalidateActiveRequestsLocked() {
        val requests = activeRequests.values.toList()
        activeRequests.clear()
        requests.forEach(RequestDispatch::invalidate)
    }

    /** Non-secret session identity used to bind delayed work without exposing a bearer. */
    fun requestProvenance(snapshot: Snapshot): RequestProvenance? {
        if (snapshot.token == null) return null
        val id = snapshot.sessionId?.takeIf { it.isNotBlank() } ?: return null
        return RequestProvenance(snapshot.revision, id, snapshot.accountId)
    }

    fun isSameSession(first: Snapshot, second: Snapshot): Boolean =
        first.sessionId != null && first.sessionId == second.sessionId

    private fun currentSnapshot(): Snapshot = Snapshot(
        token = _token.value,
        revision = revision,
        sessionId = sessionId,
        accountId = accountId,
    )

    private fun publishSnapshot() {
        _snapshot.value = currentSnapshot()
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    const val REQUEST_REVISION_HEADER = "X-Airdrop-Auth-Revision"
    const val REQUEST_SESSION_ID_HEADER = "X-Airdrop-Auth-Session"
}
