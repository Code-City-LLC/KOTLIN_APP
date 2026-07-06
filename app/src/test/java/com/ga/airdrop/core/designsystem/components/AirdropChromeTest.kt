package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Swift-precedence chrome guard. Figma still shows translucent Home chrome, but
 * Swift `FigmaTabHeader`/`FigmaBottomTabBar` render an opaque gray200 overlay
 * above their blur layers, so Android must not keep the stale flat-alpha
 * approximation.
 */
class AirdropChromeTest {
    private val gray200 = Color(0xFF333333)

    private fun assertOpaqueSwiftSurface(name: String, color: Color) {
        assertEquals("$name must be opaque like Swift gray200", 1f, color.alpha, 0f)
        assertEquals("$name must keep the gray200 red channel", gray200.red, color.red, 0f)
        assertEquals("$name must keep the gray200 green channel", gray200.green, color.green, 0f)
        assertEquals("$name must keep the gray200 blue channel", gray200.blue, color.blue, 0f)
    }

    @Test
    fun headerHeroBackgroundIsOpaqueSwiftSurface() {
        assertOpaqueSwiftSurface(
            "Home hero header",
            AirdropChrome.headerBackground(overImage = true, gray200 = gray200),
        )
    }

    @Test
    fun headerSolidBackgroundIsOpaqueSwiftSurface() {
        assertOpaqueSwiftSurface(
            "Solid header",
            AirdropChrome.headerBackground(overImage = false, gray200 = gray200),
        )
    }

    @Test
    fun bottomBarBackgroundIsOpaqueSwiftSurface() {
        assertOpaqueSwiftSurface("Bottom tab bar", AirdropChrome.bottomBarBackground(gray200))
    }
}
