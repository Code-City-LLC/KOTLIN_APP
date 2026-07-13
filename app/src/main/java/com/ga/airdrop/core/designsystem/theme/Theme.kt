package com.ga.airdrop.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

object AirdropTheme {
    val colors: AirdropColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAirdropColors.current
}

@Composable
fun AirdropTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeController.mode) {
        ThemeController.Mode.SYSTEM -> isSystemInDarkTheme()
        ThemeController.Mode.LIGHT -> false
        ThemeController.Mode.DARK -> true
    }
    val colors = if (dark) darkAirdropColors else lightAirdropColors

    // Material3 is only a substrate (ripples, text field internals); all
    // visible colors come from AirdropColorScheme per the Figma tokens.
    val material = if (dark) {
        darkColorScheme(
            primary = colors.orangeMain,
            background = colors.gray200,
            surface = colors.gray100,
        )
    } else {
        lightColorScheme(
            primary = colors.orangeMain,
            background = colors.gray200,
            surface = colors.gray100,
        )
    }

    // Text-size funnel (Kemar 2026-07-12, Swift DesignTokens parity): the
    // preference multiplies fontScale only, so every sp text scales app-wide
    // while dp layout metrics stay untouched.
    val density = LocalDensity.current
    val scaledDensity = Density(
        density = density.density,
        fontScale = density.fontScale * TextSizeController.level.fontMultiplier,
    )

    CompositionLocalProvider(
        LocalAirdropColors provides colors,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

@Composable
fun AirdropThemeProvider(content: @Composable () -> Unit) {
    AirdropTheme(content)
}
