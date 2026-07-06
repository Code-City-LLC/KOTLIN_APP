package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.Color

/**
 * Shared tab chrome background source of truth.
 *
 * Latest ruling: compare Figma to Swift, and Swift takes precedence. Figma Home
 * node 40001464:28899 still shows translucent header/footer chrome, but Swift
 * `FigmaTabHeader` and `FigmaBottomTabBar` both place a blur layer underneath an
 * opaque semantic `DesignTokens.Color.gray200` overlay. Compose therefore uses
 * the same opaque theme surface here instead of the older flat-alpha Figma
 * approximation.
 */
internal object AirdropChrome {
    /** Swift uses the same opaque gray200 surface for hero and frosted styles. */
    fun headerBackground(overImage: Boolean, gray200: Color): Color =
        gray200

    /** Swift bottom tab bar overlays opaque gray200 above the blur layer. */
    fun bottomBarBackground(gray200: Color): Color = gray200
}
