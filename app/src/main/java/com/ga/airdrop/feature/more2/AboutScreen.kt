package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore

/**
 * About AirDrop — Swift FigmaAboutViewController's stable core. Swift keeps
 * About as a platform-convention utility screen ("About screen on every iOS
 * app. Keep it as a Swift-owned utility", FigmaSpecificPages.swift:540), so
 * this is a utility port rather than a Figma-strict node.
 *
 * SCOPE (per ORC #15406, amended by Kemar's D/A/C/F/G/H ruling): the stable
 * core (app identity + Terms, Privacy, Contact Support → Help, "Visit
 * airdropja.com") PLUS the Swift deep-audit preference rows Kemar greenlit —
 * "Default delivery method" (D) here. The remaining Swift rows (active
 * sessions, download-your-data) stay excluded until ruled in.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val colors = AirdropTheme.colors
    val uriHandler = LocalUriHandler.current

    // Feature D — default delivery method (Swift DeliveryDefaultsStore). Held in
    // local state so the picker reflects the write immediately (the store is
    // SharedPreferences, not observable).
    var deliveryMethod by remember { mutableStateOf(DeliveryDefaultsStore.preferredMethod) }
    var showDeliveryDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("about-root")
            .background(colors.gray200)
    ) {
        More2InnerHeader(title = "About AirDrop", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Image(
                painter = painterResource(
                    if (colors.isDark) R.drawable.img_airdrop_logo_dark else R.drawable.img_airdrop_logo
                ),
                contentDescription = "Airdrop",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(48.dp)
                    .testTag("about-logo"),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = AirdropType.body2,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about-version"),
            )
            Spacer(Modifier.height(Spacing.lg))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm1)) {
                AboutRow(
                    title = "Terms & Conditions",
                    testTag = "about-row-terms",
                    onClick = { onNavigate(Routes.TERMS) },
                )
                AboutRow(
                    title = "Privacy Policy",
                    testTag = "about-row-privacy",
                    onClick = { onNavigate(Routes.PRIVACY) },
                )
                AboutRow(
                    title = "Contact Support",
                    testTag = "about-row-support",
                    onClick = { onNavigate(Routes.CONTACTS) },
                )
                AboutRow(
                    title = "Visit airdropja.com",
                    testTag = "about-row-web",
                    onClick = { runCatching { uriHandler.openUri("https://airdropja.com") } },
                )
                AboutRow(
                    title = "Default delivery method",
                    testTag = "about-row-delivery",
                    onClick = { showDeliveryDialog = true },
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (showDeliveryDialog) {
        DeliveryMethodDialog(
            current = deliveryMethod,
            onSelect = {
                DeliveryDefaultsStore.preferredMethod = it
                deliveryMethod = it
                showDeliveryDialog = false
            },
            onClear = {
                DeliveryDefaultsStore.preferredMethod = null
                deliveryMethod = null
                showDeliveryDialog = false
            },
            onDismiss = { showDeliveryDialog = false },
        )
    }
}

/**
 * Default-delivery-method picker — one row per [DeliveryDefaultsStore.Method]
 * (highlighted + "…selected" when it is the current default), a destructive
 * "Clear default" shown only when a default is set, and a "Close" action.
 * Mirrors the Swift action-sheet: pick to set, clear to remove.
 */
@Composable
private fun DeliveryMethodDialog(
    current: DeliveryDefaultsStore.Method?,
    onSelect: (DeliveryDefaultsStore.Method) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = {
            Text(
                "Default delivery method",
                style = AirdropType.h6,
                color = colors.textDarkTitle,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm1)) {
                DeliveryDefaultsStore.Method.entries.forEach { method ->
                    val isCurrent = method == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.s))
                            .background(if (isCurrent) BrandPalette.OrangeTertiary1 else colors.gray200)
                            .clickable { onSelect(method) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            .testTag("delivery-option-${method.raw}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isCurrent) "${method.displayName} selected" else method.displayName,
                            style = AirdropType.subtitle1,
                            color = if (isCurrent) BrandPalette.OrangeMain else colors.textDarkTitle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) {
                Text(
                    text = "Clear default",
                    style = AirdropType.button,
                    color = AlertPalette.Error,
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        .testTag("delivery-clear"),
                )
            }
        },
        dismissButton = {
            Text(
                text = "Close",
                style = AirdropType.button,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .testTag("delivery-close"),
            )
        },
    )
}

@Composable
private fun AboutRow(title: String, testTag: String, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.gray500),
            modifier = Modifier
                .size(13.dp)
                .rotate(-90f),
        )
    }
}
