package com.ga.airdrop.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG_AUDIT C7 regression guard.
 *
 * `set()` must not touch the `lateinit var prefs` before `init()` has bound it.
 * A theme toggle racing cold start (or a Compose preview) threw
 * `UninitializedPropertyAccessException` in the pre-fix code.
 *
 * Plain-JVM test: `init()` is never called, so `::prefs` stays uninitialized
 * and the guard skips persistence. A completing test proves there is no crash
 * and that the in-memory `mode` still updates so the UI reacts.
 */
class ThemeControllerCrashGuardTest {

    @Test
    fun `set before init updates mode without crashing`() {
        ThemeController.set(ThemeController.Mode.DARK)
        assertEquals(ThemeController.Mode.DARK, ThemeController.mode)
    }
}
