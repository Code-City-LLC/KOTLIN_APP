package com.ga.airdrop.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Design-system colors.
 *
 * Ported 1:1 from SWIFT_APP/Airdrop/DesignTokens.swift, which was generated
 * from the Figma "Design System" page (node 1:4) variables via the Figma
 * Dev Mode MCP. Do NOT hand-edit values unless Figma changed — drift makes
 * the design diverge silently.
 *
 * Every token that is dynamic on iOS (UIColor.dynamic(light:dark:)) is a
 * pair here, resolved by [AirdropColorScheme] for the active theme.
 */

// ─── Static brand palette (same in both modes) ───────────────────────────
object BrandPalette {
    // Primary brand orange — Figma Primary Color/Orange/Main
    val OrangeMain = Color(0xFFF15114)
    val OrangeDark = Color(0xFFF15114) // alias retained (see Swift comment)
    val OrangeMainDark = Color(0xFFF46427) // Kemar-approved dark primary orange
    val OrangeFunctionDark = Color(0xFFF46427) // Keep dark CTA/fill aligned with the primary accent
    val OrangeLegacy = Color(0xFFFF8000) // RN's bright pumpkin — explicit references only
    val OrangeTertiary1 = Color(0xFF994D00)
    val OrangeTertiary2 = Color(0xFFCC6600)
    val OrangeTertiary3 = Color(0xFFFFA64D)
    val OrangeTertiary4 = Color(0xFFFFCC99)
    val OrangeTertiary5 = Color(0xFFFFEEDD)
    val OrangeTertiary6 = Color(0xFFFAF6F5)

    // Secondary orange (warm gold accent)
    val OrangeAccentMain = Color(0xFFF99A3C)
    val OrangeAccentTertiary1 = Color(0xFF804F1F)
    val OrangeAccentTertiary2 = Color(0xFFB26E2B)
    val OrangeAccentTertiary3 = Color(0xFFF9BF86)

    // Primary brand blue (deep navy)
    val BlueMain = Color(0xFF2A2367)
    val BlueTertiary1 = Color(0xFF3E3499)
    val BlueTertiary2 = Color(0xFF5345CC)
    val BlueTertiary3 = Color(0xFF877CE5)
    val BlueTertiary4 = Color(0xFFC4BDFF)

    // Secondary blue (cyan accent)
    val BlueAccentMain = Color(0xFF0A96D4)
    val BlueAccentTertiary1 = Color(0xFF0872A1)
    val BlueAccentTertiary2 = Color(0xFF40C4FF)
    val BlueAccentTertiary3 = Color(0xFFA6E3FF)

    // Buttons (function state) — synced with RN buttons{}
    val ButtonStatic = Color(0xFFFF8000)
    val ButtonHover = Color(0xFFFFA64D)
    val ButtonActive = Color(0xFFCC6600)
    val ButtonLoading = Color(0xFFFF8000)
    val ButtonDisable = Color(0xFFD1D1D1)

    val White = Color(0xFFFFFFFF)
}

// ─── Alerts (shipment / package status) — static, no dark variant ────────
object AlertPalette {
    val Completed = Color(0xFF39A634)
    val OnHold = Color(0xFF0049D9)
    val Error = Color(0xFFD92A2A)
    val Cancel = Color(0xFFB8B8B8)
    val Pending = Color(0xFFF2A813)
    val NotStarted = Color(0xFF292929)

    object Light {
        val Completed = Color(0xFFCCF9CA)
        val OnHold = Color(0xFFE3ECFF)
        val Error = Color(0xFFF9D3D3)
        val Pending = Color(0xFFF8EAD0)
    }

    object Middle {
        val Completed = Color(0xFF99E495)
        val OnHold = Color(0xFF97AFDD)
        val Error = Color(0xFFDF9494)
        val Pending = Color(0xFFE7CC97)
    }
}

// ─── Customer-tier accents ────────────────────────────────────────────────
object TierPalette {
    val BronzeSaver2 = Color(0xFFD2554D)
    val PlatinumElite2 = Color(0xFF6C46C5)
    val CorporateBulk2 = Color(0xFF004B6C)
    val CorporateBulk3 = Color(0xFF0A96D4)
}

// ─── Gradients ────────────────────────────────────────────────────────────
object GradientPalette {
    /** RN MainButton main variant: vertical orange gradient. */
    val SignInButton = listOf(Color(0xFFFF783E), Color(0xFFF15114))

    /** Warm sunrise ramp behind the login logo — light mode. Stops 0/0.55/1. */
    val SignInBackgroundLight = listOf(Color(0xFFFFE9D6), Color(0xFFFFC9A3), Color(0xFFF88458))

    /** Deep navy ramp — dark mode. Stops 0/0.55/1. */
    val SignInBackgroundDark = listOf(Color(0xFF16182B), Color(0xFF2A2367), Color(0xFF3E3499))
}

/**
 * Mode-dependent tokens. Mirrors the `UIColor.dynamic(light:dark:)` pairs in
 * DesignTokens.swift; [lightAirdropColors]/[darkAirdropColors] hold the same
 * hex pairs as RN `light.gray.*` / `dark.gray.*`.
 */
@Immutable
data class AirdropColorScheme(
    val isDark: Boolean,
    // Brand accents
    val orangeMain: Color,
    val orangeDark: Color,
    val buttonStatic: Color,
    val buttonLoading: Color,
    // Grayscale
    val gray100: Color, // surface
    val gray150: Color, // BG Box
    val gray200: Color, // BG
    val gray300: Color,
    val gray400: Color,
    val gray500: Color,
    val gray600: Color,
    val gray700: Color,
    // Text
    val textDarkTitle: Color,
    val textWhiteTitle: Color,
    val textDescription: Color,
    val textPlaceholder: Color,
    // Misc
    val divider: Color,
    val iconShape: Color,
    val cardHairline: Color,
    val iconWhite: Color,
    val iconSelected: Color,
    val peachLight: Color,
    // Glass overlays
    val glassOverlay20: Color,
    val glassOverlay62: Color,
    val glassOverlay70: Color,
    // Login gradient for the active mode
    val signInBackground: List<Color>,
)

val lightAirdropColors = AirdropColorScheme(
    isDark = false,
    orangeMain = BrandPalette.OrangeMain,
    orangeDark = BrandPalette.OrangeDark,
    buttonStatic = BrandPalette.ButtonStatic,
    buttonLoading = BrandPalette.ButtonLoading,
    gray100 = Color(0xFFFFFFFF),
    gray150 = Color(0xFFFBFBFB),
    gray200 = Color(0xFFF5F5F5),
    gray300 = Color(0xFFEBEBEB),
    gray400 = Color(0xFFB8B8B8),
    gray500 = Color(0xFF9E9E9E),
    gray600 = Color(0xFF6B6B6B),
    gray700 = Color(0xFF292929),
    textDarkTitle = Color(0xFF292929),
    textWhiteTitle = Color(0xFFFFFFFF),
    textDescription = Color(0xFF5C5C5C),
    textPlaceholder = Color(0xFF999999),
    divider = Color(0xFFE5E5E5),
    iconShape = Color(0xFFE5E5E5),
    cardHairline = Color(0xFFE5E5E5),
    iconWhite = Color(0xFFFFFFFF),
    iconSelected = Color(0xFF292929),
    peachLight = Color(0xFFF8E9E0),
    glassOverlay20 = Color(0x33FFFFFF),
    glassOverlay62 = Color(0x9EFFFFFF),
    glassOverlay70 = Color(0xB3FFFFFF),
    signInBackground = GradientPalette.SignInBackgroundLight,
)

val darkAirdropColors = AirdropColorScheme(
    isDark = true,
    orangeMain = BrandPalette.OrangeMainDark,
    orangeDark = BrandPalette.OrangeFunctionDark,
    buttonStatic = BrandPalette.OrangeFunctionDark,
    buttonLoading = BrandPalette.OrangeFunctionDark,
    gray100 = Color(0xFF383838),
    gray150 = Color(0xFF2E2E2E),
    gray200 = Color(0xFF333333),
    gray300 = Color(0xFF494949),
    gray400 = Color(0xFFEBEBEB),
    gray500 = Color(0xFFF2F2F2),
    gray600 = Color(0xFFFBFBFB),
    gray700 = Color(0xFFFFFFFF),
    textDarkTitle = Color(0xFFFFFFFF),
    textWhiteTitle = Color(0xFFFFFFFF),
    textDescription = Color(0xFF999999),
    textPlaceholder = Color(0xFF5C5C5C),
    divider = Color(0xFF404040),
    iconShape = Color(0xFF4D4D4D),
    cardHairline = Color(0xFF4D4D4D),
    iconWhite = Color(0xFFFFFFFF),
    iconSelected = Color(0xFFFFFFFF),
    peachLight = Color(0xFF3A2A22),
    glassOverlay20 = Color(0x33292929),
    glassOverlay62 = Color(0x9E292929),
    glassOverlay70 = Color(0xB3292929),
    signInBackground = GradientPalette.SignInBackgroundDark,
)

/**
 * Android fallback for Figma and Swift glass surfaces. Swift combines a
 * 62/70% tint with a real backdrop blur; applying that tint alone in Compose
 * leaves imagery sharply visible through the surface. Keep the source overlay
 * tokens unchanged and raise only the flat fallback's perceived opacity. The
 * 97% value keeps bright hero detail from reading sharply through an unblurred
 * Android panel while preserving a small backdrop contribution.
 */
const val FROSTED_GLASS_FALLBACK_ALPHA = 0.97f

val AirdropColorScheme.frostedGlassSurface: Color
    get() = (if (isDark) Color(0xFF292929) else Color.White)
        .copy(alpha = FROSTED_GLASS_FALLBACK_ALPHA)

/** Home card glass resolves visually to Figma's opaque gray150 after Swift blur. */
val AirdropColorScheme.frostedGlassCardSurface: Color
    get() = gray150

val LocalAirdropColors = staticCompositionLocalOf { lightAirdropColors }
