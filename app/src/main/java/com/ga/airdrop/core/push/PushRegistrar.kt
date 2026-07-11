package com.ga.airdrop.core.push

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.repo.MiscRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the FCM device-token lifecycle: cache → register with the backend →
 * re-register on rotation/login. Fixes the fresh-install hole where
 * `onNewToken` fired before login (token dropped, never replayed) and the
 * login path never called POST /device-tokens/register at all — so a normal
 * user's device was never registered and received no pushes, ever.
 *
 * Swift parity: FigmaLoginViewController.swift:504-511 re-POSTs the cached
 * Messaging token on login success; RN NotificationService.register() runs on
 * permission grant at app mount. Kotlin now does both:
 *  - [onNewToken] always caches (even logged out) and registers when a bearer
 *    exists — on an application-scoped coroutine, so the POST survives the
 *    short-lived FirebaseMessagingService being destroyed mid-flight.
 *  - [registerIfLoggedIn] replays the cached (or freshly fetched) token from
 *    login success, MainActivity.onStart, and the notification-permission
 *    grant callback. Dedupes on the last successfully registered token.
 */
object PushRegistrar {

    private const val PREFS = "push_registrar"
    private const val KEY_TOKEN = "fcmToken"
    private const val KEY_REGISTERED = "registeredFcmToken"

    private var prefs: SharedPreferences? = null

    // Application-scoped: token registration must not die with the transient
    // FirebaseMessagingService (its onDestroy used to cancel the in-flight POST).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** FCM rotated (or first issued) a token. Always cache; register if we can. */
    fun onNewToken(token: String) {
        if (token.isBlank()) return
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
        registerIfLoggedIn()
    }

    /**
     * POST the current token to /device-tokens/register when a bearer exists.
     * No-ops when logged out or when [force] is false and this exact token was
     * already registered.
     */
    fun registerIfLoggedIn(force: Boolean = false) {
        if (AuthTokenStore.token == null) return
        val cached = prefs?.getString(KEY_TOKEN, null)
        if (cached != null) {
            register(cached, force)
        } else {
            requestCurrentFcmToken { token ->
                prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
                register(token, force)
            }
        }
    }

    /**
     * Logout hygiene: SettingsViewModel deletes the Firebase token, so drop the
     * cached copy and the registered marker — the next login fetches and
     * registers the freshly issued token instead of replaying a dead one.
     */
    fun onLogout() {
        prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_REGISTERED)?.apply()
    }

    private fun register(token: String, force: Boolean) {
        if (!force && prefs?.getString(KEY_REGISTERED, null) == token) return
        scope.launch {
            MiscRepository(ApiClient.service)
                .registerFcmToken(
                    deviceToken = token,
                    deviceType = "android",
                    deviceInfo = androidDeviceInfo(),
                )
                .onSuccess { prefs?.edit()?.putString(KEY_REGISTERED, token)?.apply() }
            // onFailure: keep KEY_REGISTERED unset — the next registerIfLoggedIn
            // call (every foreground) retries automatically.
        }
    }
}

/** Current FCM token via the async Firebase API (never blocks). */
internal fun requestCurrentFcmToken(onToken: (String) -> Unit) {
    runCatching { FirebaseMessaging.getInstance().token }
        .getOrNull()
        ?.addOnSuccessListener { token ->
            if (!token.isNullOrBlank()) onToken(token)
        }
}

/** Swift sends "ID: <idfv>, OS: <ver>" — Android equivalent (settings-path parity). */
internal fun androidDeviceInfo(): String =
    "Manufacturer: ${Build.MANUFACTURER}, Model: ${Build.MODEL}, OS: ${Build.VERSION.RELEASE}"
