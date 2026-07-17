package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Account Deletion — Figma node 40007388:24881, behavior from
 * FigmaAccountDeletionViewController: instruction copy, email + password
 * verification, Confirm → Deletion Reason screen.
 */
@Composable
fun AccountDeletionScreen(
    onBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: AccountDeletionViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.verified) {
        if (state.verified) {
            viewModel.consumeVerified()
            onVerified()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .imePadding()
            .testTag("account-deletion-root")
    ) {
        More2InnerHeader(title = "Account Deletion", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .testTag("account-deletion-content"),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "To delete your account, please confirm by entering your account " +
                    "details. This action is permanent and cannot be undone.",
                style = AirdropType.body1,
                color = colors.textDarkTitle,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "We use this information only to verify account ownership",
                style = AirdropType.body2,
                color = colors.textDescription,
            )
            Spacer(Modifier.height(Spacing.md))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                More2Field(
                    label = "Email Address",
                    value = state.email,
                    onValueChange = viewModel::onEmail,
                    placeholder = "e.g. kemi2627@yahoo.com",
                    required = true,
                    asteriskColor = BrandPalette.OrangeMain,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    fieldTag = "account-deletion-email-input",
                    cardTag = "account-deletion-email-card",
                )
                More2Field(
                    label = "Password",
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                    placeholder = "**********",
                    required = true,
                    asteriskColor = BrandPalette.OrangeMain,
                    isPassword = true,
                    passwordVisible = state.passwordVisible,
                    onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    fieldTag = "account-deletion-password-input",
                    cardTag = "account-deletion-password-card",
                )
            }
            Spacer(Modifier.height(40.dp))
        }

        More2BottomBar(modifier = Modifier.testTag("account-deletion-bottom-bar")) {
            More2PrimaryButton(
                text = "Confirm",
                onClick = viewModel::confirm,
                loading = state.loading,
                modifier = Modifier.testTag("account-deletion-confirm"),
            )
        }
    }

    state.error?.let { message ->
        More2Alert(title = "Error", message = message, onDismiss = viewModel::dismissError)
    }
}
