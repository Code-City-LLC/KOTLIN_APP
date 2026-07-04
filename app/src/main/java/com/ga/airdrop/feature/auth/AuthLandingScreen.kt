package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.components.ThemeToggle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Auth landing — Figma "Log in" node 40006240:23928 (light) /
 * 40006149:75728 (dark): soft-wave background, theme toggle, logo,
 * "Welcome to AirDrop" + tricolor tagline, Log in / Sign Up buttons.
 */
@Composable
fun AuthLandingScreen(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
) {
    val colors = AirdropTheme.colors
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
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo top edge sits at 207.26/812 of the screen in Figma.
                androidx.compose.foundation.layout.Spacer(Modifier.weight(207f))
                // Theme-aware logo: wide color wordmark (light) / round logo
                // (dark) — same asset pair the Swift login uses.
                Image(
                    painter = painterResource(
                        if (colors.isDark) R.drawable.img_airdrop_logo_dark
                        else R.drawable.img_airdrop_logo
                    ),
                    contentDescription = "AirDrop",
                    modifier = Modifier
                        .fillMaxWidth(324f / 375f)
                        .aspectRatio(if (colors.isDark) 1.06f else 649f / 180f),
                    contentScale = ContentScale.Fit,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(54.dp))
                Text(
                    text = "Welcome to AirDrop",
                    style = AirdropType.h4,
                    color = colors.textDarkTitle,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFFF45A26))) { append("Shop. ") }
                        withStyle(
                            SpanStyle(color = if (colors.isDark) Color(0xFFFFFFFF) else Color(0xFF243141))
                        ) { append("Ship. ") }
                        withStyle(SpanStyle(color = Color(0xFF0A96D4))) { append("Simplified.") }
                    },
                    style = AirdropType.h5.copy(fontSize = 26.sp, lineHeight = 30.sp),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(300f))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GradientButton(text = "Log in", onClick = onLogin)
                OutlineButton(text = "Sign Up", onClick = onSignUp)
            }
        }
    }
}
