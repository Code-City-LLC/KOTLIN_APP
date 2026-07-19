package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.setValue
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
import com.ga.airdrop.core.designsystem.theme.BrandPalette
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val biometricsAvailable = androidx.compose.runtime.remember {
        com.ga.airdrop.core.security.BiometricGate.isAvailable(context)
    }
    var vaultEnabled by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.ga.airdrop.core.security.BiometricLoginVault.isEnabled(),
        )
    }
    var showEnrollOffer by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    if (state.loggedIn) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            // Offer biometric sign-in ONCE after a successful password login
            // (Kemar 2026-07-19: biometrics must be usable for logging in).
            if (com.ga.airdrop.core.security.shouldOfferBiometricLogin(
                    biometricsAvailable = biometricsAvailable,
                    vaultEnabled = com.ga.airdrop.core.security.BiometricLoginVault.isEnabled(),
                    vaultEmail = com.ga.airdrop.core.security.BiometricLoginVault.savedEmail(),
                    loginEmail = state.email,
                    offerDeclined = com.ga.airdrop.core.security.BiometricLoginVault.offerDeclined(),
                ) && state.password.isNotEmpty()
            ) {
                showEnrollOffer = true
            } else {
                onLoggedIn()
            }
        }
    }

    if (showEnrollOffer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            containerColor = colors.gray100,
            title = {
                Text(
                    "Sign in with biometrics?",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
            },
            text = {
                Text(
                    "Use your fingerprint or face to sign in to Airdrop next " +
                        "time — no password needed.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val host = activity
                        if (host == null) {
                            showEnrollOffer = false
                            onLoggedIn()
                        } else {
                            scope.launch {
                                // Confirm the sensor works before vaulting.
                                val passed = com.ga.airdrop.core.security.BiometricGate
                                    .authenticate(host, "Confirm to enable biometric sign-in")
                                if (passed) {
                                    com.ga.airdrop.core.security.BiometricLoginVault
                                        .enable(state.email, state.password)
                                }
                                showEnrollOffer = false
                                onLoggedIn()
                            }
                        }
                    },
                    modifier = Modifier.testTag("biometric-enroll-yes"),
                ) {
                    Text("Enable", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        com.ga.airdrop.core.security.BiometricLoginVault.setOfferDeclined()
                        showEnrollOffer = false
                        onLoggedIn()
                    },
                    modifier = Modifier.testTag("biometric-enroll-no"),
                ) {
                    Text("Not now", style = AirdropType.subtitle2, color = colors.textDescription)
                }
            },
        )
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
            // Biometric sign-in — shown only once the customer opted in
            // after a password login (vault holds their credentials).
            if (vaultEnabled && biometricsAvailable && activity != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp)
                        .padding(top = 12.dp)
                        .height(44.dp)
                        .border(
                            1.dp,
                            BrandPalette.OrangeMain,
                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        )
                        .clickable(enabled = !state.loading) {
                            scope.launch {
                                val creds = com.ga.airdrop.core.security.BiometricLoginVault
                                    .unlock(activity)
                                if (creds != null) {
                                    viewModel.loginWithVaultCredentials(
                                        creds.email,
                                        creds.password,
                                    )
                                }
                                vaultEnabled = com.ga.airdrop.core.security.BiometricLoginVault
                                    .isEnabled()
                            }
                        }
                        .testTag("biometric-sign-in"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sign in with " +
                            com.ga.airdrop.core.security.BiometricGate.biometricTypeName(context),
                        style = AirdropType.subtitle2,
                        color = colors.textDarkTitle,
                    )
                }
            }
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
