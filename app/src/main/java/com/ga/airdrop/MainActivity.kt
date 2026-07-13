package com.ga.airdrop

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.AppRoot
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.network.TokenRefresher
import com.ga.airdrop.core.push.AirdropMessagingService
import com.ga.airdrop.core.push.PushDeepLink
import com.ga.airdrop.core.push.PushRegistrar
import com.ga.airdrop.core.security.BiometricGate
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.feature.auth.OnboardingStore
import com.ga.airdrop.feature.security.BiometricLockScreen
import kotlinx.coroutines.launch
import retrofit2.HttpException

// FragmentActivity (not ComponentActivity) because androidx.biometric's
// BiometricPrompt requires a FragmentActivity host. FragmentActivity IS a
// ComponentActivity, so setContent / enableEdgeToEdge / lifecycleScope are
// unchanged.
class MainActivity : FragmentActivity() {

    // The resolved night bit the activity was built with (set in
    // attachBaseContext). A ThemeController Mode change that flips this bit needs
    // an activity recreate so the values-night resources (duotone icons, window
    // background) re-resolve in lockstep with Compose.
    private var attachedNight = false

    /**
     * Dark-mode night-sync: force the activity's uiMode night bit from
     * ThemeController so @color/icon_duotone, @color/window_background and
     * themes-night follow the in-app theme instead of the OS uiMode (Swift
     * FigmaAppTheme.apply parity). SYSTEM passes the OS bit through unchanged.
     */
    override fun attachBaseContext(newBase: Context) {
        val mask = ThemeController.nightMask()
        val base = if (mask != null) {
            val config = Configuration(newBase.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mask
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        attachedNight = (base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        super.attachBaseContext(base)
    }

    /**
     * Android 13+ (targetSdk 35): POST_NOTIFICATIONS defaults to DENIED and
     * every notify() silently no-ops until the user grants it. The permission
     * was declared in the manifest but never requested — no push was ever
     * visible on 13+. RN requests on app mount (NotificationService.ts:29-39);
     * Swift at cold launch (AppDelegate.swift:100-113). Same timing here.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) PushRegistrar.registerIfLoggedIn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PushDeepLink.capture(intent)
        PushDeepLink.captureUri(intent)
        maybeSeedSession()
        // Channels must exist BEFORE the first background (system-posted) push:
        // the manifest meta-data routes those to airdrop_alerts, which only
        // takes effect once the channel is created.
        AirdropMessagingService.createChannels(
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
        )
        maybeRequestNotificationPermission()
        // Cold-launch biometric gate (Swift SceneDelegate.presentBiometricLockIfNeeded).
        // Opt-in + default OFF + falls back to no-gate when biometry is
        // unavailable, so this is inert unless the user enabled it.
        val lockedAtLaunch = BiometricGate.requiresAuthOnLaunch(this)
        setContent {
            AirdropTheme {
                // Recreate only when the effective night bit actually flips
                // (SYSTEM↔matching light/dark is a no-op), so resource-night
                // catches up with Compose without recreate loops. OS-level night
                // changes in SYSTEM mode already recreate via the manifest (no
                // uiMode configChanges), so system mode stays correct for free.
                LaunchedEffect(ThemeController.mode) {
                    if (ThemeController.resolvedNight(applicationContext) != attachedNight) {
                        recreate()
                    }
                }
                var locked by rememberSaveable { mutableStateOf(lockedAtLaunch) }
                // #90: the lock state gates pending-push consumption inside
                // AppRoot — navigation must not fire under the overlay.
                AppRoot(navigationLocked = locked)
                if (locked) {
                    BiometricLockScreen(
                        activity = this@MainActivity,
                        onUnlocked = { locked = false },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshStoredSession()
        // Replays the cached FCM token to /device-tokens/register when logged
        // in (dedupes on last-registered) — covers login-before-token installs,
        // permission grants, and app updates that predate PushRegistrar.
        PushRegistrar.registerIfLoggedIn()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Swift SceneDelegate.refreshStoredSessionIfNeeded (:429/:436): every
     * foreground with a stored bearer refreshes the session so it survives
     * expiry windows. 401 → the token is dead → TokenRefresher clears it and
     * AppRoot's reactive logout returns the user to the auth landing; network
     * errors leave the session untouched (the 401-recovery interceptor path
     * still guards individual calls).
     */
    private fun refreshStoredSession() {
        val refreshingSession = AuthTokenStore.snapshot()
        if (refreshingSession.token == null) return
        lifecycleScope.launch {
            runCatching { ApiClient.service.refreshToken(EmptyRequest()) }
                .onSuccess {
                    TokenRefresher.applyForegroundRefresh(refreshingSession, null, it.token)
                }
                .onFailure { e ->
                    TokenRefresher.applyForegroundRefresh(
                        refreshingSession,
                        (e as? HttpException)?.code(),
                        null,
                    )
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushDeepLink.capture(intent)
        PushDeepLink.captureUri(intent)
    }

    /**
     * Debug-only headless session seed — writes a bearer into AuthTokenStore in
     * the APP's own process (EncryptedSharedPreferences is not multi-process
     * safe, so an instrumentation-process write is not readable here). Lets a
     * verifier land on an authenticated screen without typing a password
     * on-device (Kemar rule airdrop-prestaging-ui-login-routing). No-op in
     * release. Mint a staging token on Forge, then:
     *   adb shell am start -n <pkg>/com.ga.airdrop.MainActivity \
     *     -e seed_bearer "<token>" --ez seed_onboarding true
     */
    private fun maybeSeedSession() {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra("seed_bearer")?.takeIf { it.isNotBlank() }?.let {
            AuthTokenStore.save(it)
        }
        if (intent?.getBooleanExtra("seed_onboarding", false) == true) {
            OnboardingStore.markSeen(this)
        }
    }
}
