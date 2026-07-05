package com.ga.airdrop.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

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
            primary = BrandPalette.OrangeMain,
            background = colors.gray200,
            surface = colors.gray100,
        )
    } else {
        lightColorScheme(
            primary = BrandPalette.OrangeMain,
            background = colors.gray200,
            surface = colors.gray100,
        )
    }

    CompositionLocalProvider(LocalAirdropColors provides colors) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

@Composable
fun AirdropThemeProvider(content: @Composable () -> Unit) {
    AirdropTheme(content)
}
