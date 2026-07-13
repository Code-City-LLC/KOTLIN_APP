package com.ga.airdrop.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
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
    )

    data class RequestProvenance(val revision: Long, val sessionId: String)

    private const val PREFS = "airdrop_auth"
    private const val KEY_TOKEN = "api_token"
    private const val KEY_SESSION_ID = "session_id"

    private lateinit var prefs: SharedPreferences
    private val stateLock = ReentrantReadWriteLock()
    private var revision = 0L
    private var sessionId: String? = null

    private val _token = MutableStateFlow<String?>(null)
    val tokenFlow: StateFlow<String?> get() = _token
    private val _snapshot = MutableStateFlow(Snapshot(null, 0L, null))
    val snapshotFlow: StateFlow<Snapshot> get() = _snapshot

    val token: String? get() = _token.value

    fun init(context: Context) = stateLock.write {
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
            prefs.edit().remove(KEY_SESSION_ID).apply()
            null
        } else {
            prefs.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() }
                ?: newSessionId().also { prefs.edit().putString(KEY_SESSION_ID, it).commit() }
        }
        revision += 1
        publishSnapshot()
    }

    fun save(token: String) = stateLock.write {
        // Update the in-memory flow first, then persist only if prefs is bound.
        // A background service / ContentProvider can run before
        // Application.onCreate() calls init(); touching lateinit prefs then
        // would throw UninitializedPropertyAccessException (BUG_AUDIT C8).
        _token.value = token
        sessionId = newSessionId()
        revision += 1
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_SESSION_ID, sessionId)
                .commit()
        }
        publishSnapshot()
    }

    /** Rotates a bearer only when the exact expected session generation is current. */
    fun rotate(expected: Snapshot, newToken: String): Snapshot? = stateLock.write {
        if (newToken.isBlank() || currentSnapshot() != expected || expected.sessionId == null) {
            return@write null
        }
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

    fun clear() = stateLock.write {
        clearLocked()
    }

    /** Clears only if the exact request generation still owns the session. */
    fun clear(expected: Snapshot): Boolean = stateLock.write {
        if (currentSnapshot() != expected) return@write false
        clearLocked()
        true
    }

    private fun clearLocked() {
        _token.value = null
        sessionId = null
        revision += 1
        if (::prefs.isInitialized) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_SESSION_ID).commit()
        }
        publishSnapshot()
    }

    fun snapshot(): Snapshot = stateLock.read {
        currentSnapshot()
    }

    /**
     * Revalidates and dispatches under a shared read lock. Concurrent requests
     * remain concurrent, while login/logout/rotation (write operations) cannot
     * replace the session between validation and request dispatch.
     */
    fun <T> withCurrentSnapshot(expected: Snapshot, block: () -> T): T? = stateLock.read {
        if (currentSnapshot() != expected) null else block()
    }

    /** Non-secret session identity used to bind delayed work without exposing a bearer. */
    fun requestProvenance(snapshot: Snapshot): RequestProvenance? {
        if (snapshot.token == null) return null
        val id = snapshot.sessionId?.takeIf { it.isNotBlank() } ?: return null
        return RequestProvenance(snapshot.revision, id)
    }

    fun isSameSession(first: Snapshot, second: Snapshot): Boolean =
        first.sessionId != null && first.sessionId == second.sessionId

    private fun currentSnapshot(): Snapshot = Snapshot(
        token = _token.value,
        revision = revision,
        sessionId = sessionId,
    )

    private fun publishSnapshot() {
        _snapshot.value = currentSnapshot()
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    const val REQUEST_REVISION_HEADER = "X-Airdrop-Auth-Revision"
    const val REQUEST_SESSION_ID_HEADER = "X-Airdrop-Auth-Session"
}
