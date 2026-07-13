package com.ga.airdrop.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.TextSizeController

/**
 * Preferences — Figma node 40000994:19044, behavior from
 * FigmaPreferencesViewController / RN PreferencesView: read-only Email
 * Address, Set Pickup Location and Set Default Currency pickers (JMD before
 * USD), pinned Save CTA → PUT /user/profile.
 */
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    viewModel: PreferencesViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pickerFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.start(context) }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Preferences", onBack = onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
            ) {
                MoreSelectField(
                    label = "Email Address",
                    value = state.email,
                    placeholder = "your.email@example.com",
                    required = false, // Swift Preferences rows show no asterisk
                    enabled = false,
                    onClick = null,
                    trailingIconRes = null,
                )
                MoreSelectField(
                    label = "Set Pickup Location",
                    value = state.pickupLocation,
                    placeholder = "Select a pickup location",
                    required = false, // Swift Preferences rows show no asterisk
                    onClick = { pickerFor = "pickup" },
                )
                MoreSelectField(
                    label = "Set Default Currency",
                    value = state.paymentCurrency,
                    placeholder = "Select a payment currency",
                    required = false, // Swift Preferences rows show no asterisk
                    onClick = { pickerFor = "currency" },
                )
                // Text Size — device preference, not part of the profile
                // PUT: the ONE controller-backed editor (Settings' row
                // routes here; gate #24601). Applies app-wide instantly.
                MoreSelectField(
                    label = "Text Size",
                    value = TextSizeController.level.displayName,
                    placeholder = "Select text size",
                    required = false,
                    onClick = { pickerFor = "text_size" },
                )
            }
            MoreBottomButtonBar(
                text = "Save",
                loading = state.saving,
                onClick = viewModel::save,
            )
        }
    }

    when (pickerFor) {
        "pickup" -> MoreOptionSheet(
            title = "Set Pickup Location",
            options = viewModel.pickupLocations,
            selected = state.pickupLocation,
            onSelect = { viewModel.applyPickup(context, it) },
            onDismiss = { pickerFor = null },
        )
        "currency" -> MoreOptionSheet(
            title = "Set Default Currency",
            options = viewModel.paymentCurrencies,
            selected = state.paymentCurrency,
            onSelect = { viewModel.applyCurrency(context, it) },
            onDismiss = { pickerFor = null },
        )
        "text_size" -> MoreOptionSheet(
            title = "Text Size",
            options = TextSizeController.Level.entries.map { it.displayName },
            selected = TextSizeController.level.displayName,
            onSelect = { picked ->
                TextSizeController.Level.entries
                    .firstOrNull { it.displayName == picked }
                    ?.let(TextSizeController::set)
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }
    state.alert?.let { (title, message) ->
        MoreAlertDialog(title = title, message = message, onDismiss = viewModel::dismissAlert)
    }
}
