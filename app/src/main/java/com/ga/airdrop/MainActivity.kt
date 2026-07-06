package com.ga.airdrop

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.ga.airdrop.core.push.PushDeepLink
import com.ga.airdrop.core.security.BiometricGate
import com.ga.airdrop.data.model.EmptyRequest
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PushDeepLink.capture(intent)
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
                AppRoot()
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
        if (AuthTokenStore.token == null) return
        lifecycleScope.launch {
            runCatching { ApiClient.service.refreshToken(EmptyRequest()) }
                .onSuccess { TokenRefresher.applyForegroundRefresh(null, it.token) }
                .onFailure { e ->
                    TokenRefresher.applyForegroundRefresh((e as? HttpException)?.code(), null)
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushDeepLink.capture(intent)
    }
}
