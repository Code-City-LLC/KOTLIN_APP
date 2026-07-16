package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.Color

/**
 * ⚠️ LOCKED per Kemar (2026-07-05): the tab header ([AirdropHeader]) and bottom
 * bar ([AirdropBottomBar]) are TRANSLUCENT, never opaque.
 *
 * Figma "Header Type" 40000817:8974 = backdrop-blur-[10px] + gradiant/black/70
 * (rgba(41,41,41,0.7)). FigmaTabHeader.swift's opaque gray200 overlay was the
 * Swift author's DEVIATION from Figma; Kemar explicitly overruled it, so
 * "Swift is the guide" does NOT justify opaque here.
 *
 * This has regressed to opaque THREE times (commits 479b245, 789f86f, 37eabc9,
 * all "restore swift opaque chrome"). The single source of truth for the chrome
 * background now lives here and is enforced by AirdropChromeTest, which asserts
 * 0f < alpha < 1f (translucent — never fully opaque, never fully clear).
 * Making any of these return a fully-opaque color WILL FAIL THE BUILD. Do not do
 * it — raise it with Kemar first.
 *
 * ⚠️ Kemar update (2026-07-06): the 0.70 flat scrim read "far too transparent"
 * vs Figma. Figma header/footer are rgba(...,0.70) WITH a backdrop-blur-[10px]
 * (frosted → reads far more opaque). Compose has no cheap backdrop blur, so we
 * approximate that frosted *perceived* opacity with a higher flat alpha (0.95).
 * Figma reigns supreme here per Kemar: keep it translucent but subtle, not
 * see-through.
 */
internal object AirdropChrome {
    /**
     * Translucency of the header/footer surfaces. Figma = rgba(...,0.70) + 10px
     * backdrop blur; lacking a cheap Compose backdrop blur we use 0.95 to match
     * the frosted perceived opacity while retaining slight translucency.
     */
    const val SCRIM_ALPHA = 0.95f

    /** Figma gradiant/black/70 base for the Home hero header (#292929). */
    val heroScrim: Color = Color(0xFF292929)

    /**
     * Header background. OverImage (Home hero) = the Figma dark scrim; Solid
     * (Shipments/Shop/Help/More) = the theme surface. Both at [SCRIM_ALPHA].
     */
    fun headerBackground(overImage: Boolean, gray200: Color): Color =
        if (overImage) heroScrim.copy(alpha = SCRIM_ALPHA) else gray200.copy(alpha = SCRIM_ALPHA)

    /** Bottom tab bar background — translucent theme surface. */
    fun bottomBarBackground(gray200: Color): Color = gray200.copy(alpha = SCRIM_ALPHA)
}
