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

    /**
     * False when [init] fell back to plain prefs because the encrypted store
     * would not open. Diagnostic only — no caller may branch behaviour on it.
     */
    @Volatile
    internal var usedEncryptedStore: Boolean = true
        private set

    /** Retained so a failed encrypted open can be retried before a later write. */
    private var appContext: Context? = null

    private fun openEncryptedOnce(context: Context): SharedPreferences? = runCatching {
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
    }.getOrNull()

    /**
     * Opens the encrypted store, and — if it cannot be opened — DISCARDS the
     * unreadable file once and tries again.
     *
     * ⚠️ THIS RECOVERS A PERMANENT SIGN-OUT LOOP. A device restore used to copy
     * this file across (see res/xml/backup_rules.xml, now excluded) while the
     * AndroidKeyStore key that wraps its Tink keyset stayed behind, because
     * keystore keys are device-bound. The result was a keyset that can never be
     * decrypted on the new phone:
     *
     *   create() throws -> plain-prefs read fallback -> at login
     *   ensureEncryptedForWrite() reopens THE SAME dead file -> still fails ->
     *   the #182/#184 guard correctly refuses to write the bearer in cleartext
     *   -> signed out on every cold start, FOREVER, with no in-app remedy.
     *
     * The guard is right and stays. What was missing is that nothing ever
     * cleared the dead keyset, so the failure could not heal.
     *
     * Deleting is safe precisely BECAUSE the open failed: the contents are
     * undecryptable, so there is nothing to lose — only an unusable file that
     * blocks a working store from ever being created. A transient keystore
     * fault still succeeds on the first attempt and never reaches the delete.
     *
     * The backup exclusion prevents new occurrences; this repairs devices that
     * were already poisoned by a restore before that shipped.
     */
    private fun openEncrypted(context: Context): SharedPreferences? {
        openEncryptedOnce(context)?.let { return it }

        val existing = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (existing.all.isEmpty()) {
            // Nothing on disk to blame — a genuinely unavailable keystore.
            // Leave it alone; the write-time retry (#182) is the right answer.
            return null
        }

        android.util.Log.w(
            "AuthTokenStore",
            "Encrypted auth store could not be opened but is non-empty — " +
                "discarding an undecryptable keyset (device restore) so a new " +
                "store can be created. The customer signs in once more.",
        )
        val cleared = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(PREFS)
            } else {
                existing.edit().clear().commit()
            }
        }.isSuccess
        return if (cleared) openEncryptedOnce(context) else null
    }

    /**
     * Re-attempt the encrypted store before persisting a bearer. Issue #182.
     *
     * ⚠️ The read-path fallback binds PLAIN prefs for the whole process, so
     * once the keystore failed at launch, every later save wrote the bearer in
     * CLEARTEXT — silently, no log, no behavioural difference, on a device
     * whose keystore had already shown a problem. The old comment said the
     * token is "re-issued at next login anyway", which answers LOSING it and
     * not storing the next one in the open.
     *
     * Kemar's call: retry the keystore on each save. The plain fallback stays
     * for READS so the app still launches — that preserves the standing rule
     * that customers stay signed in — but a bearer never reaches cleartext if
     * encryption can be obtained at write time. A keystore unavailable at cold
     * start is frequently available minutes later.
     *
     * Returns true when the write may proceed to an ENCRYPTED store.
     */
    private fun ensureEncryptedForWrite(): Boolean {
        if (usedEncryptedStore) return true
        val reopened = appContext?.let(::openEncrypted) ?: return false
        prefs = reopened
        usedEncryptedStore = true
        return true
    }

    fun init(context: Context) {
        synchronized(transitionLock) {
            usedEncryptedStore = true
            val restoredSessionId = synchronized(stateLock) {
                invalidateActiveRequestsLocked()
                appContext = context.applicationContext
                prefs = openEncrypted(context) ?: run {
                    // Keystore corruption fallback: plain prefs beat a hard crash at
                    // launch; the token is re-issued at next login anyway.
                    //
                    // ⚠️ This fallback is SILENT, and it reads a DIFFERENT file
                    // from the encrypted store. A process that falls back
                    // therefore sees no token even though one was saved — which
                    // is indistinguishable, from the outside, from "the session
                    // did not survive". Recorded so a diagnostic can tell those
                    // two apart instead of guessing. Not a behaviour change.
                    //
                    // ⚠️⚠️ SECURITY, TRACKED SEPARATELY — DO NOT "FIX" IT HERE.
                    // `prefs` stays bound to this UNENCRYPTED instance for the
                    // process lifetime, so a later save() writes the bearer to
                    // plaintext prefs. The comment above says the token is
                    // "re-issued at next login anyway", which addresses losing
                    // it and NOT the fact that the next one is stored in the
                    // clear. Raised by BrightHarbor (#80504); it needs its own
                    // decision — fail closed, re-attempt the keystore, or hold
                    // the token in memory only — and that decision does not
                    // belong in a Firebase-guard PR.
                    usedEncryptedStore = false
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
                com.ga.airdrop.core.push.NotificationBadgeSync
                    .onAuthenticatedSessionChanged(
                        com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary.capture(),
                    )
                // #182: a bearer must never land in an unencrypted store.
                //
                // ⚠️ WHEN THIS GUARD IS FALSE THE BEARER IS NOT PERSISTED AT
                // ALL, AND THE CUSTOMER IS SIGNED OUT ON THE NEXT COLD START.
                //
                // That is the deliberate consequence of Kemar's ruling — plain
                // prefs are a READ fallback only, never a write target — and on
                // a permanently broken keystore there is nowhere safe to put a
                // bearer. Security over convenience, chosen knowingly.
                //
                // The commit that introduced this claimed it was "chosen over
                // holding the token in memory only because both of those log
                // the customer out". THAT CLAIM IS FALSE: when the retry fails
                // this IS memory-only, and it does log them out. Correcting it
                // here rather than leaving a justification that contradicts the
                // code it justifies. Raised by an adversarial audit.
                //
                // What is NOT acceptable is doing it silently, so the skip is
                // now logged. A customer stuck in a re-login loop is otherwise
                // undiagnosable from a support ticket: the app looks healthy,
                // login succeeds every time, and nothing anywhere says the
                // write was dropped.
                // Called ONCE — ensureEncryptedForWrite() reassigns `prefs` as a
                // side effect, so invoking it twice per save would re-open the
                // store on the recovery path for no reason.
                val mayWriteEncrypted = ::prefs.isInitialized && ensureEncryptedForWrite()
                if (::prefs.isInitialized && !mayWriteEncrypted) {
                    android.util.Log.w(
                        "AuthTokenStore",
                        "Encrypted store unavailable at write time — bearer NOT persisted. " +
                            "This session will not survive a cold start (#182 ruling: " +
                            "plain prefs are read-only).",
                    )
                }
                if (mayWriteEncrypted) {
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
            // #182: a rotated bearer must never land in an unencrypted store.
            if (::prefs.isInitialized && ensureEncryptedForWrite()) {
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
        com.ga.airdrop.core.push.NotificationBadgeSync.onAuthenticatedSessionChanged(null)
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
