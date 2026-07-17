package com.ga.airdrop.feature.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.security.BiometricGate
import kotlinx.coroutines.launch

/**
 * Security bottom sheet — Swift FigmaBiometricSecuritySheet. A single toggle
 * whose flip requires a LIVE biometric prompt: the desired value is only
 * persisted on a successful auth, and the switch reverts to the stored value on
 * failure. This stops anyone with an already-unlocked device from changing the
 * setting without proving biometry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricSecuritySheet(onDismiss: () -> Unit) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val typeName = remember { BiometricGate.biometricTypeName(context) }
    var enabled by remember { mutableStateOf(BiometricGate.isEnabled) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.gray100) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
        ) {
            Text("Security", style = AirdropType.h5, color = colors.textDarkTitle)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "When enabled, AirDrop asks for $typeName every time the app " +
                    "launches cold. The bearer token still lives in the device's secure " +
                    "store either way — this just adds a hardware-backed check before the " +
                    "UI is interactive.",
                style = AirdropType.body2,
                color = colors.textDescription,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("biometric-toggle-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Lock with $typeName at launch",
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onChange@{ desired ->
                        val act = activity ?: return@onChange
                        scope.launch {
                            val ok = BiometricGate.authenticate(
                                act,
                                if (desired) {
                                    "Authenticate to enable biometric unlock"
                                } else {
                                    "Authenticate to disable biometric unlock"
                                },
                            )
                            if (ok) {
                                BiometricGate.isEnabled = desired
                                enabled = desired
                            } else {
                                enabled = BiometricGate.isEnabled // revert
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AirdropTheme.colors.buttonStatic,
                        checkedThumbColor = BrandPalette.White,
                        uncheckedTrackColor = colors.gray300,
                        uncheckedThumbColor = BrandPalette.White,
                        uncheckedBorderColor = colors.gray300,
                    ),
                    modifier = Modifier.testTag("biometric-toggle"),
                )
            }
        }
    }
}
