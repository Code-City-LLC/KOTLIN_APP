package com.ga.airdrop.core.designsystem.theme

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dark-mode night-sync: nightMask() is what makes values-night resources
 * (@color/icon_duotone, window_background, themes-night) follow ThemeController
 * instead of the OS uiMode. The mapping is the whole contract:
 *  - DARK/LIGHT force a concrete night bit;
 *  - SYSTEM returns null = "don't override" (Swift FigmaAppTheme .unspecified),
 *    so the OS Dark-Mode setting still propagates (the 2026-05-24 iOS bug guard).
 * The int constants are compile-time-inlined, so this runs on plain JVM.
 */
class ThemeControllerNightMaskTest {

    @Test
    fun `nightMask maps modes to uiMode night bits, SYSTEM passes through`() {
        ThemeController.set(ThemeController.Mode.DARK)
        assertEquals(Configuration.UI_MODE_NIGHT_YES, ThemeController.nightMask())

        ThemeController.set(ThemeController.Mode.LIGHT)
        assertEquals(Configuration.UI_MODE_NIGHT_NO, ThemeController.nightMask())

        ThemeController.set(ThemeController.Mode.SYSTEM)
        assertNull(ThemeController.nightMask())
    }
}
