package com.ga.airdrop

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
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
