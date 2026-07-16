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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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

internal object LoginTags {
    const val LOGIN_BUTTON = "login-submit-button"
    const val REGISTER_PROMPT = "login-register-prompt"
    const val REGISTER_LABEL = "login-register-label"
}

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
        // auth sheet covering the bottom 65% with 30dp horizontal padding.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .background(
                    if (colors.isDark) colors.gray150 else colors.gray100,
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
                .testTag("login-bottom-panel")
                // Lift the form above the keyboard (edge-to-edge means the
                // window doesn't resize on its own): pad by whichever is
                // larger — the nav bar or the IME. Keep the action group
                // pinned while only the fields/recovery content scrolls.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(top = 32.dp, bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 30.dp)
                    .padding(bottom = Spacing.md),
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
                Spacer(Modifier.height(24.dp))
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
            }
            // Swift keeps Log In and Register as one bottom action group. They
            // must be visible without scrolling on every supported live scale.
            GradientButton(
                text = "Log In",
                onClick = viewModel::login,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .testTag(LoginTags.LOGIN_BUTTON),
                loading = state.loading,
                enabled = !state.loading,
            )
            // Swift FigmaLoginViewController.swift:218 — exactly 16 after Log In.
            // Keep that gap inside the full-width click target so it is not
            // accidentally doubled by a separate Spacer.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .testTag(LoginTags.REGISTER_PROMPT)
                    .clickable(onClick = onRegister)
                    .padding(top = 16.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Don't have an account? ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append("Register")
                        }
                    },
                    style = AirdropType.body2.copy(textAlign = TextAlign.Center),
                    color = colors.textDarkTitle,
                    modifier = Modifier.testTag(LoginTags.REGISTER_LABEL),
                )
            }
        }
    }
}
