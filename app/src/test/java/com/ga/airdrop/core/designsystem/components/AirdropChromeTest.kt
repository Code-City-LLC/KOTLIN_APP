package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⚠️ LOCKED-CHROME GUARD (Kemar 2026-07-05). The tab header + bottom bar MUST be
 * translucent (Figma 40000817:8974 = gradiant/black/70). This has been reverted
 * to opaque gray200 three times (479b245, 789f86f, 37eabc9). If any of these
 * assertions fail, someone re-opaqued the chrome — that is a Kemar-forbidden
 * regression. Restore [AirdropChrome] to translucent; do NOT weaken this test.
 */
class AirdropChromeTest {
    private val gray200 = Color(0xFF333333)

    @Test
    fun headerHeroBackgroundIsTranslucent() {
        assertTrue(
            "Home hero header must be translucent (Kemar-LOCKED), not opaque",
            AirdropChrome.headerBackground(overImage = true, gray200 = gray200).alpha < 1f,
        )
    }

    @Test
    fun headerSolidBackgroundIsTranslucent() {
        assertTrue(
            "Solid header must be translucent (Kemar-LOCKED), not opaque",
            AirdropChrome.headerBackground(overImage = false, gray200 = gray200).alpha < 1f,
        )
    }

    @Test
    fun bottomBarBackgroundIsTranslucent() {
        assertTrue(
            "Bottom tab bar must be translucent (Kemar-LOCKED), not opaque",
            AirdropChrome.bottomBarBackground(gray200).alpha < 1f,
        )
    }
}
