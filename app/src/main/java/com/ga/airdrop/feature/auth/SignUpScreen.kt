package com.ga.airdrop.feature.auth

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/** RN Config.termsAndConditions — opened from the T&C checkbox link. */
private const val TERMS_URL = "https://airdropja.com/terms-and-conditions/"

/** RN Translations user_auth2 — revealed when the customs link is tapped. */
private const val CUSTOMS_AUTH_TEXT =
    "I authorize Airdropja Limited and or its agents to act on my behalf in " +
        "relation to the customs clearance, declaration and payment of any and " +
        "all Customs charges that may be applicable to goods shipments received " +
        "on my Airdropja Limited account at the Norman Manley International " +
        "Airport Sangster's International Airport. This authorization will " +
        "remain in effect until my account is closed."

/**
 * Registration form — Figma "Edit Profile" node 40006240:23906 ("Sign Up"):
 * glass back header, gray150 page, 20dp-gap Type Input Fields (first/last
 * name row, passwords, emails, address, country/state pickers, pickup +
 * hear-about-us pickers, mobile), two consent checkboxes, pinned gradient
 * Sign Up bar. Behavior mirrors RN SignUpView/useSignUp: POST /auth/register
 * then on success routes to Registration Successful.
 */
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: SignUpViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var pickerFor by remember { mutableStateOf<String?>(null) }
    var showCustomsText by remember { mutableStateOf(false) }

    // Current Swift (FigmaSignUpViewController success sheet): registration
    // success routes straight to the full-screen Registration Successful
    // glass sheet with the verification-link copy — the old :510-523 bare
    // alert is gone (FuchsiaTower Audit #59 F5). Consume the flag before
    // navigating so this can never re-fire (WORK ORDER B1).
    if (state.registered) {
        LaunchedEffect(Unit) {
            viewModel.consumeRegistered()
            onRegistered()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            // Lift the whole form (incl. pinned Sign Up bar) above the keyboard.
            .imePadding(),
    ) {
        AuthDetailHeader(title = "Sign Up", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TypeInputField(
                    label = "First Name",
                    value = state.firstName,
                    onValueChange = { v -> viewModel.set { it.copy(firstName = v) } },
                    placeholder = "e.g. John",
                    required = true,
                    modifier = Modifier.weight(1f),
                )
                TypeInputField(
                    label = "Last Name",
                    value = state.lastName,
                    onValueChange = { v -> viewModel.set { it.copy(lastName = v) } },
                    placeholder = "e.g. Maat",
                    required = true,
                    modifier = Modifier.weight(1f),
                )
            }
            TypeInputField(
                label = "Tax Registration Number",
                value = state.trnNumber,
                onValueChange = { v -> viewModel.set { it.copy(trnNumber = v) } },
                placeholder = "e.g. 194049512",
                required = true,
                testTagPrefix = "signup-trn",
            )
            AuthSelectField(
                label = "ID Type",
                value = state.identityType,
                required = true,
                placeholder = "Select",
                onClick = { pickerFor = "identityType" },
                testTag = "signup-identity-type",
            )
            TypeInputField(
                label = "ID Number",
                value = state.identityNumber,
                onValueChange = { v -> viewModel.set { it.copy(identityNumber = v) } },
                placeholder = "e.g. P-4242",
                required = true,
                testTagPrefix = "signup-identity-number",
            )
            TypeInputField(
                label = "Password",
                value = state.password,
                onValueChange = { v -> viewModel.set { it.copy(password = v) } },
                required = true,
                isPassword = true,
                passwordVisible = state.passwordVisible,
                onTogglePasswordVisibility = viewModel::togglePasswordVisible,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                autofillContentType = ContentType.NewPassword,
            )
            TypeInputField(
                label = "Confirm Password",
                value = state.confirmPassword,
                onValueChange = { v -> viewModel.set { it.copy(confirmPassword = v) } },
                required = true,
                isPassword = true,
                passwordVisible = state.confirmPasswordVisible,
                onTogglePasswordVisibility = viewModel::toggleConfirmPasswordVisible,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            TypeInputField(
                label = "Email Address",
                value = state.email,
                onValueChange = { v -> viewModel.set { it.copy(email = v) } },
                placeholder = "e.g. kemi2627@yahoo.com",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                autofillContentType = ContentType.EmailAddress,
            )
            TypeInputField(
                label = "Confirm Email Address",
                value = state.confirmEmail,
                onValueChange = { v -> viewModel.set { it.copy(confirmEmail = v) } },
                placeholder = "e.g. kemi2627@yahoo.com",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            TypeInputField(
                label = "Address line 1",
                value = state.addressLine1,
                onValueChange = { v -> viewModel.set { it.copy(addressLine1 = v) } },
                placeholder = "e.g. 6175 NW 167th Street",
                required = true,
            )
            TypeInputField(
                label = "Address line 2",
                value = state.addressLine2,
                onValueChange = { v -> viewModel.set { it.copy(addressLine2 = v) } },
                placeholder = "e.g. G6",
            )
            AuthSelectField(
                label = "Country",
                value = state.country,
                required = true,
                placeholder = "",
                onClick = { pickerFor = "country" },
            )
            AuthSelectField(
                label = "State/Province/Department",
                value = state.state,
                required = true,
                placeholder = "e.g. Florida",
                enabled = state.country.isNotEmpty(),
                onClick = { pickerFor = "state" },
            )
            // City is typed free-form (Swift SignUpViewController behavior —
            // there is no bundled city list on mobile).
            TypeInputField(
                label = "City",
                value = state.city,
                onValueChange = { v -> viewModel.set { it.copy(city = v) } },
                placeholder = "e.g. Miami",
                required = true,
            )
            AuthSelectField(
                label = "Pickup Location",
                value = state.pickupLocation,
                required = true,
                onClick = { pickerFor = "pickup" },
            )
            AuthSelectField(
                label = "How did you hear about us",
                value = state.hearType,
                required = true,
                onClick = { pickerFor = "hearType" },
            )
            TypeInputField(
                label = "Mobile Number",
                value = state.mobile,
                onValueChange = { v -> viewModel.set { it.copy(mobile = v) } },
                placeholder = "e.g. +1876-5290736",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AuthCheckboxRow(
                    checked = state.acceptTerms,
                    onToggle = { viewModel.set { it.copy(acceptTerms = !it.acceptTerms) } },
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("I accept the ")
                            withLink(
                                LinkAnnotation.Clickable("terms") {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                android.net.Uri.parse(TERMS_URL),
                                            ),
                                        )
                                    }
                                },
                            ) {
                                withStyle(
                                    SpanStyle(
                                        color = BrandPalette.OrangeMain,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ) { append("Terms and Conditions") }
                            }
                            append(" of the membership agreement to airdrop limited.")
                        },
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                    )
                }
                AuthCheckboxRow(
                    checked = state.acceptCustomsAuth,
                    onToggle = {
                        viewModel.set { it.copy(acceptCustomsAuth = !it.acceptCustomsAuth) }
                    },
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("I accept ")
                            withLink(
                                LinkAnnotation.Clickable("customs") {
                                    showCustomsText = !showCustomsText
                                },
                            ) {
                                append("airdrop limited customs authorization form.")
                            }
                        },
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                    )
                }
                if (showCustomsText) {
                    Text(
                        text = CUSTOMS_AUTH_TEXT,
                        style = AirdropType.body3,
                        color = colors.textDescription,
                    )
                }
            }
        }
        AuthBottomButtonBar(
            text = "Sign Up",
            onClick = viewModel::signUp,
            loading = state.loading,
        )
    }

    when (pickerFor) {
        "country" -> AuthOptionSheet(
            title = "Country",
            options = signUpCountries.map { it.name },
            selected = state.country.ifEmpty { null },
            onSelect = viewModel::selectCountry,
            onDismiss = { pickerFor = null },
        )
        "state" -> AuthOptionSheet(
            title = "State/Province/Department",
            options = state.stateOptions,
            selected = state.state.ifEmpty { null },
            onSelect = { v -> viewModel.set { it.copy(state = v) } },
            onDismiss = { pickerFor = null },
        )
        "identityType" -> AuthOptionSheet(
            title = "ID Type",
            options = signUpIdTypeOptions,
            selected = state.identityType.ifEmpty { null },
            onSelect = { v -> viewModel.set { it.copy(identityType = v) } },
            onDismiss = { pickerFor = null },
        )
        "pickup" -> AuthOptionSheet(
            title = "Pickup Location",
            options = pickupLocationOptions,
            selected = state.pickupLocation.ifEmpty { null },
            onSelect = { v -> viewModel.set { it.copy(pickupLocation = v) } },
            onDismiss = { pickerFor = null },
        )
        "hearType" -> AuthOptionSheet(
            title = "How did you hear about us",
            options = hearAboutUsOptions,
            selected = state.hearType.ifEmpty { null },
            onSelect = { v -> viewModel.set { it.copy(hearType = v) } },
            onDismiss = { pickerFor = null },
        )
    }

    state.alert?.let { (title, message) ->
        AuthAlertDialog(title = title, message = message, onDismiss = viewModel::dismissAlert)
    }
}
