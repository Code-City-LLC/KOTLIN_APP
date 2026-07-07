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
import androidx.compose.foundation.layout.fillMaxHeight
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
 * 40006149:75728 (dark): wave background, logo, bottom sheet card
 * (top radius ~31dp) with Welcome Back!, email/password fields,
 * Forgot Password?, gradient Log In, Register link.
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

    Box(
        Modifier
            .fillMaxSize()
            .testTag("login-root")
    ) {
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
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = Spacing.md, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeToggle()
            }
        }
        // Figma dark node 40006149:75728 places the round mark at
        // x=28/y=106/w=321/h=306 on a 375-wide frame. Keep it independent
        // from the sheet so the panel remains bottom anchored like Swift.
        Image(
            painter = painterResource(
                if (colors.isDark) R.drawable.img_airdrop_logo_dark
                else R.drawable.img_airdrop_logo
            ),
            contentDescription = "AirDrop",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (colors.isDark) 106.dp else 104.dp)
                .fillMaxWidth(if (colors.isDark) 321f / 375f else 260f / 375f)
                .aspectRatio(if (colors.isDark) 642f / 612f else 649f / 180f)
                .testTag("login-logo"),
            contentScale = ContentScale.Fit,
        )
        // Bottom panel — Swift FigmaLoginViewController.swift keeps the sheet
        // at 65% height with 32 top/bottom padding and 30 horizontal inset.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .testTag("login-panel")
                .background(
                    if (colors.isDark) colors.gray150 else colors.gray100,
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
                .padding(horizontal = 30.dp)
                .padding(top = 32.dp)
                // Lift the form above the keyboard (edge-to-edge means the
                // window doesn't resize on its own): pad by whichever is
                // larger — the nav bar or the IME. The focused field then
                // scrolls into view above the keyboard.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(bottom = 32.dp)
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
                Spacer(Modifier.height(20.dp))
                TypeInputField(
                    label = "Email Address",
                    required = true,
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = "e.g. username@email.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    autofillContentType = ContentType.EmailAddress + ContentType.Username,
                    testTagPrefix = "login-email",
                    labelStyle = AirdropType.subtitle2,
                    placeholderStyle = AirdropType.body2,
                    inputTextStyle = AirdropType.body2,
                    fieldHeight = 50.dp,
                    fieldRadius = 10.dp,
                    fieldHorizontalPadding = 20.dp,
                    labelFieldGap = 5.dp,
                )
                Spacer(Modifier.height(14.dp))
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
                    testTagPrefix = "login-password",
                    labelStyle = AirdropType.subtitle2,
                    placeholderStyle = AirdropType.body2,
                    inputTextStyle = AirdropType.body2,
                    fieldHeight = 50.dp,
                    fieldRadius = 10.dp,
                    fieldHorizontalPadding = 20.dp,
                    labelFieldGap = 5.dp,
                )
                // Swift FigmaLoginViewController.swift:213 — 10 after password;
                // :171-179 — Body2 underline in textDarkTitle.
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Forgot Password?",
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
                Spacer(Modifier.height(14.dp))
                GradientButton(
                    text = "Log In",
                    onClick = viewModel::login,
                    loading = state.loading,
                    enabled = !state.loading,
                    modifier = Modifier.testTag("login-button"),
                )
                Spacer(Modifier.height(12.dp))
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
