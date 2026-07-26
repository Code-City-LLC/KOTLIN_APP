package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⚠️ LOCKED-CHROME GUARD (Kemar 2026-07-05, tightened 2026-07-06). The tab header
 * + bottom bar MUST be TRANSLUCENT but SUBTLE (frosted), matching Figma
 * (rgba(...,0.70) + backdrop-blur-[10px]). This has been reverted to opaque
 * gray200 three times (479b245, 789f86f, 37eabc9); separately, a flat 0.70 alpha
 * (no blur) read "far too transparent" and Kemar rejected THAT too.
 *
 * So the alpha is now guarded on BOTH ends: it must be < 1f (never opaque) AND
 * >= 0.80f (never too see-through — approximates Figma's frosted opacity since
 * Compose lacks a cheap backdrop blur). If any assertion fails, someone pushed
 * the chrome to an extreme Kemar forbade. Fix [AirdropChrome.SCRIM_ALPHA] back
 * into the frosted band; do NOT weaken this test.
 */
class AirdropChromeTest {
    private val gray200 = Color(0xFF333333)

    private fun assertFrosted(name: String, alpha: Float) {
        assertTrue("$name must be translucent (Kemar-LOCKED), not opaque", alpha < 1f)
        assertTrue(
            "$name must not be too see-through (Kemar 2026-07-06: 0.70 flat was too transparent)",
            alpha >= 0.80f,
        )
        assertEquals(
            "$name must retain the approved subtle 0.95 translucency",
            0.95f,
            alpha,
            0.001f,
        )
    }

    @Test
    fun headerHeroBackgroundIsFrostedTranslucent() {
        assertFrosted(
            "Home hero header",
            AirdropChrome.headerBackground(overImage = true, gray200 = gray200).alpha,
        )
    }

    @Test
    fun headerSolidBackgroundIsFrostedTranslucent() {
        assertFrosted(
            "Solid header",
            AirdropChrome.headerBackground(overImage = false, gray200 = gray200).alpha,
        )
    }

    @Test
    fun bottomBarBackgroundIsFrostedTranslucent() {
        assertFrosted("Bottom tab bar", AirdropChrome.bottomBarBackground(gray200).alpha)
        // Kemar 2026-07-26: the inner detail headers (Packages et al) are glass
        // too. This was an opaque gray100 wash; the lock now covers it so an
        // "opaque header" revert fails the build here as well.
        assertFrosted(
            "Inner detail header",
            AirdropChrome.detailHeaderBackground(Color(0xFFFFFFFF)).alpha,
        )
    }

    /**
     * ⚠️ Kemar (2026-07-25): "the header should not be dark, it should be light
     * like the footer." The Home header previously used the #292929 hero scrim
     * and rendered near-black (measured luminance 43) while the footer rendered
     * 245. Header and footer are ONE chrome family: same surface, same alpha,
     * on every tab including Home over the hero. This test is the lock — if it
     * fails, someone reintroduced a dark/hero-specific header.
     */
    @Test
    fun headerMatchesFooterSurfaceOnEveryTabIncludingHome() {
        val footer = AirdropChrome.bottomBarBackground(gray200)
        val homeHeader = AirdropChrome.headerBackground(overImage = true, gray200 = gray200)
        val solidHeader = AirdropChrome.headerBackground(overImage = false, gray200 = gray200)
        assertEquals(
            "Home header must use the SAME surface as the footer (Kemar-LOCKED, no dark hero scrim)",
            footer,
            homeHeader,
        )
        assertEquals(
            "Solid header must use the SAME surface as the footer",
            footer,
            solidHeader,
        )
    }
}
