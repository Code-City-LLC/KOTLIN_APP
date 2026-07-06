package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.Color
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
    }
}
