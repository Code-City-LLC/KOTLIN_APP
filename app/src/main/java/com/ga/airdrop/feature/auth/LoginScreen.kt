package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.ThemeToggle
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Login form — Figma "Log in" node 40006240:24005 (light) /
 * 40005190:29153 (dark): wave background, logo, bottom sheet card
 * (top radius ~31dp) with Welcome Back!, email/password fields,
 * Forget Password?, gradient Log In, Register link.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    if (state.loggedIn) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onLoggedIn() }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.bg_auth_dark else R.drawable.bg_auth_light
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(62.dp)
                .padding(horizontal = Spacing.md, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeToggle()
        }
        // Figma Login logo: light uses the 260x72 wordmark at y=150
        // (40006240:26932); dark uses the 321x306 hero at y=106
        // (40006149:75728). Keep their independent vertical geometry.
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.img_airdrop_logo_dark
                else R.drawable.img_airdrop_logo
            ),
            contentDescription = "AirDrop",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (colors.isDark) 106.dp else 150.dp)
                .fillMaxWidth(if (colors.isDark) 321f / 375f else 260f / 375f)
                .aspectRatio(if (colors.isDark) 321f / 306f else 649f / 180f)
                .testTag("login-logo"),
            contentScale = ContentScale.Fit,
        )
        // Bottom panel — Figma 40006149:75739 / Swift LoginVC: rounded-top
        // auth sheet with 30 horizontal padding and dark #2e2e2e fill.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(524.dp)
                .background(
                    if (colors.isDark) colors.gray150 else colors.gray100,
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
                .testTag("login-bottom-panel")
                .padding(horizontal = 30.dp)
                .padding(top = 36.dp)
                // Lift the form above the keyboard (edge-to-edge means the
                // window doesn't resize on its own): pad by whichever is
                // larger — the nav bar or the IME. The focused field then
                // scrolls into view above the keyboard.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Welcome Back!",
                style = AirdropType.h4,
                color = colors.textDarkTitle,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Login to AirDrop",
                style = AirdropType.body1,
                color = colors.textDarkTitle,
            )
            Spacer(Modifier.height(30.dp))
            TypeInputField(
                label = "Email Address",
                required = true,
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "e.g. username@email.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                autofillContentType = ContentType.EmailAddress + ContentType.Username,
            )
            Spacer(Modifier.height(Spacing.md))
            TypeInputField(
                label = "Password",
                required = true,
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                isPassword = true,
                passwordVisible = state.passwordVisible,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                autofillContentType = ContentType.Password,
            )
            // Swift FigmaLoginViewController.swift:213 — 10 after password;
            // :171-179 — Body2 underline in textDarkTitle.
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Forget Password?",
                style = AirdropType.body2.copy(textDecoration = TextDecoration.Underline),
                color = colors.textDarkTitle,
                modifier = Modifier.clickable(onClick = onForgotPassword),
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
                text = "Log In",
                onClick = viewModel::login,
                loading = state.loading,
                enabled = !state.loading,
            )
            // Swift FigmaLoginViewController.swift:218 — 16 after Log In.
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Don't have an account? ",
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                )
                Text(
                    text = "Register",
                    style = AirdropType.underlineLink.copy(textDecoration = TextDecoration.Underline),
                    color = colors.textDarkTitle,
                    modifier = Modifier.clickable(onClick = onRegister),
                )
            }
        }
    }
}
