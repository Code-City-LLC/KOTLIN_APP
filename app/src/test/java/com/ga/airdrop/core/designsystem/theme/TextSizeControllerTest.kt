package com.ga.airdrop.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Text-size preference pins (Kemar directive 2026-07-12, Swift
 * `AirdropTextSizePreference` parity @ 8ce745c).
 *
 * Plain-JVM: `init()` is never called, mirroring ThemeControllerCrashGuardTest —
 * `set()` must update the in-memory level without touching lateinit prefs.
 */
class TextSizeControllerTest {

    @Test
    fun `levels mirror the Swift multipliers exactly`() {
        val expected = mapOf(
            TextSizeController.Level.SMALLER to 0.92f,
            TextSizeController.Level.STANDARD to 1.00f,
            TextSizeController.Level.LARGER to 1.10f,
            TextSizeController.Level.LARGEST to 1.18f,
        )
        assertEquals(expected.keys.toList(), TextSizeController.Level.entries.toList())
        expected.forEach { (level, multiplier) ->
            assertEquals(multiplier, level.fontMultiplier, 0.0001f)
        }
    }

    @Test
    fun `display names match the Swift picker labels`() {
        assertEquals(
            listOf("Smaller", "Standard", "Larger", "Largest"),
            TextSizeController.Level.entries.map { it.displayName },
        )
    }

    @Test
    fun `set before init updates level without crashing`() {
        TextSizeController.set(TextSizeController.Level.LARGEST)
        assertEquals(TextSizeController.Level.LARGEST, TextSizeController.level)
        TextSizeController.set(TextSizeController.Level.STANDARD)
        assertEquals(TextSizeController.Level.STANDARD, TextSizeController.level)
    }
}
