package com.ga.airdrop.feature.security

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ga.airdrop.R
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore
import com.ga.airdrop.core.push.QuietHoursStore
import com.ga.airdrop.core.security.BiometricGate
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.cart.SavedForLaterStore
import kotlinx.coroutines.launch

/**
 * Full-screen cold-launch lock overlay — Swift FigmaBiometricLockViewController.
 * Auto-fires the system prompt once on appear; a failed/cancelled attempt keeps
 * the retry button live. "Sign out" runs the same full logout hygiene as
 * SettingsViewModel.finishLocalLogout — clearing the bearer lets AppRoot's
 * reactive logout drop to the auth landing after the overlay dismisses.
 */
@Composable
fun BiometricLockScreen(
    activity: FragmentActivity,
    onUnlocked: () -> Unit,
) {
    val typeName = remember { BiometricGate.biometricTypeName(activity) }
    var status by remember { mutableStateOf("Locked") }
    val scope = rememberCoroutineScope()

    fun attemptUnlock() {
        scope.launch {
            if (BiometricGate.authenticate(activity, "Unlock AirDrop")) {
                onUnlocked()
            } else {
                status = "$typeName failed — tap to try again."
            }
        }
    }

    LaunchedEffect(Unit) { attemptUnlock() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .clickable { attemptUnlock() }
            .testTag("biometric-lock-root"),
    ) {
        Text(
            text = "AirDrop",
            style = AirdropType.h2,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp),
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = status,
                style = AirdropType.subtitle1,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("biometric-lock-status"),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandPalette.ButtonStatic)
                    .clickable { attemptUnlock() }
                    .testTag("biometric-lock-unlock"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Unlock with $typeName",
                    style = AirdropType.button,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Sign out",
                style = AirdropType.button,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .clickable {
                        AuthTokenStore.clear()
                        SessionStore.clear()
                        CartStore.init(activity.applicationContext)
                        CartStore.clear()
                        SavedForLaterStore.init(activity.applicationContext)
                        SavedForLaterStore.clearAll()
                        DeliveryDefaultsStore.clearAll()
                        QuietHoursStore.clear(activity.applicationContext)
                        BiometricGate.reset()
                        com.ga.airdrop.feature.shipments.clearShipmentsSessionCaches()
                        com.ga.airdrop.feature.shop.clearShopSessionCaches()
                        com.ga.airdrop.core.prefs.ExchangeRateStore.clear()
                        onUnlocked()
                    }
                    .padding(8.dp)
                    .testTag("biometric-lock-signout"),
            )
        }
    }
}
