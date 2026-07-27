package com.ga.airdrop.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Track rail's "passed" green must be the SAME green as every other
 * completed signal in the app, and the same one web uses.
 *
 * It was not. `DeliveryStagePalette.Passed` carried its own `#2E9E5B` while
 * `AlertPalette.Completed` — the token the rest of the app uses — is `#39A634`.
 * So a customer looking at the Track journey saw a slightly different green from
 * the one on their package status, and a different one again from the web view.
 *
 * Laravel is the reference: `resources/css/customer-delivery.css:37` sets
 * `--cdl-done: #39a634`, and its own comment maps that to
 * `Alert.completed #39A634`. Handed over by BronzeMountain 2026-07-25
 * ("[WEB->MOBILE] Tracking parity: green-on-passed #39A634").
 *
 * ⚠️ This is a CROSS-REPO constant. The test states the literal so that changing
 * it here fails loudly rather than silently diverging from web again — the exact
 * failure mode that produced two greens in the first place.
 */
class DeliveryStageGreenParityTest {

    /** Laravel `--cdl-done`. Do not edit without changing web in the same breath. */
    private val webDoneGreen = 0xFF39A634.toInt()

    @Test
    fun `the passed-stage green is the canonical completed green`() {
        assertEquals(
            "Track's passed green must equal AlertPalette.Completed — two greens for " +
                "'done' is what this fixed",
            AlertPalette.Completed.value,
            DeliveryStagePalette.Passed.value,
        )
    }

    @Test
    fun `the completed green matches web's --cdl-done exactly`() {
        assertEquals(
            "must equal Laravel resources/css/customer-delivery.css --cdl-done (#39a634)",
            webDoneGreen,
            AlertPalette.Completed.toArgb(),
        )
    }

    @Test
    fun `the passed green matches web's --cdl-done exactly`() {
        assertEquals(webDoneGreen, DeliveryStagePalette.Passed.toArgb())
    }

    /** The in-flight accent is a DIFFERENT colour and must not be collapsed into it. */
    @Test
    fun `current stays distinct from passed`() {
        org.junit.Assert.assertNotEquals(
            "a customer must be able to tell the stage they are AT from the ones behind it",
            DeliveryStagePalette.Passed.value,
            DeliveryStagePalette.Current.value,
        )
    }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
    val a = (alpha * 255f + 0.5f).toInt() shl 24
    val r = (red * 255f + 0.5f).toInt() shl 16
    val g = (green * 255f + 0.5f).toInt() shl 8
    val b = (blue * 255f + 0.5f).toInt()
    return a or r or g or b
}
