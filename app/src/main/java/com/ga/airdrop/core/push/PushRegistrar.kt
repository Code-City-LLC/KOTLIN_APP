package com.ga.airdrop.core.push

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.prefs.NotificationAccountPreferences
import com.ga.airdrop.data.repo.MiscRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sole owner of the FCM token lifecycle. Every register, disable, and logout
 * operation runs through one FIFO queue, so a late token deletion cannot erase
 * a token minted by a later enable or authenticated session.
 */
object PushRegistrar {

    enum class DevicePushOutcome {
        RegistrationRequested,
        Disabled,
    }

    private const val PREFS = "push_registrar"
    private const val KEY_TOKEN = "fcmToken"
    private const val KEY_REGISTERED = "registeredFcmToken"
    private const val KEY_REGISTERED_ACCOUNT = "registeredFcmAccount"
    private const val KEY_REGISTERED_CLIENT = "registeredClientVersion"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var authorizationCheck: (() -> Boolean)? = null
    private val stateLock = Any()
    private var commandGeneration = 0L
    private var inFlightKey: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (operation in operations) {
                // One unexpected callback must not terminate the sole lifecycle worker.
                runCatching { operation() }
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        authorizationCheck = null
        prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        NotificationAccountPreferences.init(context.applicationContext)
    }

    internal fun initForTest(context: Context, authorizationCheck: () -> Boolean) {
        init(context)
        this.authorizationCheck = authorizationCheck
    }

    /** FCM rotated (or first issued) a token. Eligibility is checked twice. */
    fun onNewToken(token: String) {
        if (token.isBlank()) return
        val expected = currentProvenance() ?: return
        if (!NotificationAccountPreferences.masterEnabledFor(expected)) return
        if (!notificationAuthorizationAllowed()) return
        val generation = synchronized(stateLock) { commandGeneration }
        enqueue {
            if (!registrationAllowed(expected, generation)) return@enqueue
            prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
            registerTokenAwait(
                token = token,
                expected = expected,
                force = false,
                generation = generation,
                registerRequest = ::registerWithBackend,
            )
        }
    }

    fun registerIfLoggedIn(force: Boolean = false) {
        val expected = currentProvenance() ?: return
        enqueueRegistration(expected, force)
    }

    fun setDevicePushEnabled(
        expected: AuthTokenStore.RequestProvenance,
        enabled: Boolean,
        onComplete: (Result<DevicePushOutcome>) -> Unit = {},
    ) {
        setDevicePushEnabledWith(
            expected = expected,
            enabled = enabled,
            deleteToken = ::deleteCurrentFcmToken,
            tokenRequester = ::requestCurrentFcmToken,
            registerRequest = ::registerWithBackend,
            onComplete = onComplete,
        )
    }

    internal fun setDevicePushEnabledWith(
        expected: AuthTokenStore.RequestProvenance,
        enabled: Boolean,
        deleteToken: suspend () -> Result<Unit>,
        tokenRequester: suspend () -> String?,
        registerRequest: suspend (String, AuthTokenStore.RequestProvenance) -> Boolean,
        onComplete: (Result<DevicePushOutcome>) -> Unit = {},
    ) {
        enqueue {
            val result = runCatching {
                expected.accountId
                    ?: error("No signed-in account is available. Sign in again, then retry.")
                check(isCurrent(expected)) {
                    "The signed-in account changed before the preference could be applied."
                }
                val generation = synchronized(stateLock) {
                    commandGeneration += 1
                    commandGeneration
                }
                if (enabled) {
                    val registered = registerForSessionAwait(
                        expected = expected,
                        force = true,
                        generation = generation,
                        tokenRequester = tokenRequester,
                        registerRequest = registerRequest,
                    )
                    check(registered) { "Device registration could not be completed. Tap Retry." }
                    DevicePushOutcome.RegistrationRequested
                } else {
                    clearRegistrationMarkers(deleteCachedToken = false)
                    deleteToken().getOrThrow()
                    clearRegistrationMarkers(deleteCachedToken = true)
                    DevicePushOutcome.Disabled
                }
            }
            onComplete(result)
        }
    }

    fun registerForSession(
        expected: AuthTokenStore.RequestProvenance,
        force: Boolean = false,
    ) {
        enqueueRegistration(expected, force)
    }

    internal fun registerForSessionWith(
        expected: AuthTokenStore.RequestProvenance,
        force: Boolean,
        tokenRequester: suspend () -> String?,
        registerRequest: suspend (String, AuthTokenStore.RequestProvenance) -> Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val generation = synchronized(stateLock) { commandGeneration }
        enqueue {
            onComplete(
                registerForSessionAwait(
                    expected,
                    force,
                    generation,
                    tokenRequester,
                    registerRequest,
                ),
            )
        }
    }

    fun onLogout() {
        onLogoutWith(::deleteCurrentFcmToken)
    }

    internal fun onLogoutWith(
        deleteToken: suspend () -> Result<Unit>,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        synchronized(stateLock) {
            commandGeneration += 1
            inFlightKey = null
        }
        clearRegistrationMarkers(deleteCachedToken = false)
        enqueue {
            val result = deleteToken()
            if (result.isSuccess) clearRegistrationMarkers(deleteCachedToken = true)
            onComplete(result)
        }
    }

    private fun enqueueRegistration(
        expected: AuthTokenStore.RequestProvenance,
        force: Boolean,
    ) {
        val generation = synchronized(stateLock) { commandGeneration }
        enqueue {
            registerForSessionAwait(
                expected = expected,
                force = force,
                generation = generation,
                tokenRequester = ::requestCurrentFcmToken,
                registerRequest = ::registerWithBackend,
            )
        }
    }

    private suspend fun registerForSessionAwait(
        expected: AuthTokenStore.RequestProvenance,
        force: Boolean,
        generation: Long,
        tokenRequester: suspend () -> String?,
        registerRequest: suspend (String, AuthTokenStore.RequestProvenance) -> Boolean,
    ): Boolean {
        if (!registrationAllowed(expected, generation)) return false
        val token = prefs?.getString(KEY_TOKEN, null)
            ?: tokenRequester()?.takeIf { it.isNotBlank() }?.also { fresh ->
                if (registrationAllowed(expected, generation)) {
                    prefs?.edit()?.putString(KEY_TOKEN, fresh)?.apply()
                }
            }
            ?: return false
        return registerTokenAwait(token, expected, force, generation, registerRequest)
    }

    private suspend fun registerTokenAwait(
        token: String,
        expected: AuthTokenStore.RequestProvenance,
        force: Boolean,
        generation: Long,
        registerRequest: suspend (String, AuthTokenStore.RequestProvenance) -> Boolean,
    ): Boolean {
        val accountId = expected.accountId ?: return false
        if (!registrationAllowed(expected, generation)) return false
        val clientIdentity = InstalledAppVersionProvider.current().registrationIdentity
        val alreadyRegistered = prefs?.getString(KEY_REGISTERED, null) == token &&
            prefs?.getString(KEY_REGISTERED_ACCOUNT, null) == accountId.toString() &&
            prefs?.getString(KEY_REGISTERED_CLIENT, null) == clientIdentity
        if (!force && alreadyRegistered) return true

        val requestKey = "$accountId|$token|$clientIdentity"
        synchronized(stateLock) {
            if (inFlightKey == requestKey) return false
            inFlightKey = requestKey
        }
        return try {
            val registered = registerRequest(token, expected)
            if (registered && registrationAllowed(expected, generation)) {
                prefs?.edit()
                    ?.putString(KEY_REGISTERED, token)
                    ?.putString(KEY_REGISTERED_ACCOUNT, accountId.toString())
                    ?.putString(KEY_REGISTERED_CLIENT, clientIdentity)
                    ?.apply()
                true
            } else {
                false
            }
        } finally {
            synchronized(stateLock) {
                if (inFlightKey == requestKey) inFlightKey = null
            }
        }
    }

    private suspend fun registerWithBackend(
        token: String,
        provenance: AuthTokenStore.RequestProvenance,
    ): Boolean {
        val installed = InstalledAppVersionProvider.current()
        return MiscRepository(ApiClient.service)
            .registerFcmToken(
                deviceToken = token,
                deviceType = "android",
                deviceInfo = androidDeviceInfo(),
                appVersion = installed.versionName,
                buildNumber = installed.buildNumber.toString(),
                expectedSession = provenance,
            )
            .isSuccess
    }

    private fun currentProvenance(): AuthTokenStore.RequestProvenance? =
        AuthTokenStore.requestProvenance(AuthTokenStore.snapshot())

    private fun isCurrent(expected: AuthTokenStore.RequestProvenance): Boolean =
        currentProvenance() == expected

    private fun registrationAllowed(
        expected: AuthTokenStore.RequestProvenance,
        generation: Long,
    ): Boolean {
        val accountId = expected.accountId ?: return false
        if (!isCurrent(expected)) return false
        if (!NotificationAccountPreferences.masterEnabledFor(expected)) return false
        if (!notificationAuthorizationAllowed()) return false
        return synchronized(stateLock) { commandGeneration == generation }
    }

    private fun clearRegistrationMarkers(deleteCachedToken: Boolean) {
        prefs?.edit()?.apply {
            if (deleteCachedToken) remove(KEY_TOKEN)
            remove(KEY_REGISTERED)
            remove(KEY_REGISTERED_ACCOUNT)
            remove(KEY_REGISTERED_CLIENT)
        }?.apply()
    }

    private fun enqueue(operation: suspend () -> Unit) {
        check(operations.trySend(operation).isSuccess) { "Push operation queue is unavailable" }
    }

    fun notificationAuthorizationAllowed(): Boolean {
        authorizationCheck?.let { return it() }
        val context = appContext ?: return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        return notificationAuthorizationAllowed(context)
    }
}

internal suspend fun requestCurrentFcmToken(): String? = suspendCancellableCoroutine { continuation ->
    val task = runCatching { FirebaseMessaging.getInstance().token }.getOrNull()
    if (task == null) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    task.addOnSuccessListener { token ->
        if (continuation.isActive) continuation.resume(token?.takeIf { it.isNotBlank() })
    }.addOnFailureListener {
        if (continuation.isActive) continuation.resume(null)
    }
}

private suspend fun deleteCurrentFcmToken(): Result<Unit> =
    suspendCancellableCoroutine { continuation ->
        val task = runCatching { FirebaseMessaging.getInstance().deleteToken() }.getOrElse { error ->
            continuation.resume(Result.failure(error))
            return@suspendCancellableCoroutine
        }
        task.addOnSuccessListener {
            if (continuation.isActive) continuation.resume(Result.success(Unit))
        }.addOnFailureListener { error ->
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

internal fun androidDeviceInfo(): String =
    "Manufacturer: ${Build.MANUFACTURER}, Model: ${Build.MODEL}, OS: ${Build.VERSION.RELEASE}"

internal fun notificationAuthorizationAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
