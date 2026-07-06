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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * About AirDrop — Swift FigmaAboutViewController's stable core. Swift keeps
 * About as a platform-convention utility screen ("About screen on every iOS
 * app. Keep it as a Swift-owned utility", FigmaSpecificPages.swift:540), so
 * this is a utility port rather than a Figma-strict node.
 *
 * SCOPE (per ORC #15406): the stable core only — app identity + the four rows
 * that map to existing surfaces (Terms, Privacy, Contact Support → Help,
 * "Visit airdropja.com"). The Swift screen's flag-gated deep-audit rows
 * (biometric lock, active sessions, quiet hours, delivery method,
 * download-your-data) are DELIBERATELY EXCLUDED until Kemar wants them in
 * Kotlin — do not add them here without that ruling.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val colors = AirdropTheme.colors
    val uriHandler = LocalUriHandler.current

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
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
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
