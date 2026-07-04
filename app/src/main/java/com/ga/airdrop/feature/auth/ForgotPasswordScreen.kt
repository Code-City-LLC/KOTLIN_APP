package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Forgot password — Figma "Forget Password?" node 40006240:23942: wave
 * background, logo, bottom glass card (top radius 31) titled Reset Password
 * with an email field, gradient Save and outline Back to Log In. Submitting
 * POSTs /auth/forgot-password (Swift ForgotViewController behavior — the
 * design's password inputs belong to the web reset step that follows the
 * emailed link), then swaps in the success sheet from Figma 40006240:23961.
 */
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.bg_auth_dark else R.drawable.bg_auth_light
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Logo top edge sits at 142/812 in the frame (98 below status bar).
            Spacer(Modifier.height(98.dp))
            Image(
                painter = painterResource(R.drawable.img_airdrop_logo),
                contentDescription = "AirDrop",
                modifier = Modifier
                    .fillMaxWidth(260f / 375f)
                    .aspectRatio(260.032f / 71.879f)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.weight(1f))
            if (!state.sent) {
                // Reset Password card — top radius 31, 31dp side padding.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            colors.glassOverlay70,
                            RoundedCornerShape(topStart = 31.dp, topEnd = 31.dp),
                        )
                        .padding(horizontal = 31.dp)
                        .padding(top = 42.dp)
                        // Lift the reset form above the keyboard.
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(bottom = 28.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Reset Password",
                        style = AirdropType.h4,
                        color = colors.textDarkTitle,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Enter your email to receive recovery instructions",
                        style = AirdropType.body1,
                        color = colors.textDarkTitle,
                    )
                    Spacer(Modifier.height(31.dp))
                    TypeInputField(
                        label = "Email Address",
                        required = true,
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = "e.g. username@email.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        autofillContentType = ContentType.EmailAddress,
                    )
                    if (state.error != null) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = state.error ?: "",
                            style = AirdropType.body2,
                            color = AlertPalette.Error,
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    GradientButton(
                        text = "Save",
                        onClick = viewModel::submit,
                        loading = state.loading,
                        enabled = state.email.isNotBlank(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    OutlineButton(text = "Back to Log In", onClick = onBackToLogin)
                }
            }
        }
        if (state.sent) {
            // Success pop-up — Figma 40006240:23961 (copy per Swift alert).
            AuthSuccessSheet(
                title = "Email sent",
                message = state.sentMessage
                    ?: "A password reset link has been sent to your email. " +
                        "Please check your inbox.",
                buttonText = "Back to Log In",
                onButtonClick = onBackToLogin,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
