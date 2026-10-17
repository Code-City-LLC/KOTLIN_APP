package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kemar 2026-10-17: the Platinum tier label in the header read as greyed out
 * (#CACACA, like a disabled label). He chose #6E7B8B for Platinum, and only
 * Platinum.
 */
class TierAccentColorTest {

    @Test
    fun platinumUsesKemarsSlateNotTheGreyedOutGrey() {
        listOf("Platinum Priority", "Platinum Standard").forEach { tier ->
            assertEquals(tier, 0xFF6E7B8B.toInt(), tierAccentColor(tier).toArgb())
        }
    }

    @Test
    fun theOtherTiersKeepTheirAccents() {
        mapOf(
            "Inactive" to 0xFFF1A88C,
            "Ruby Starter" to 0xFFD2554D,
            "Sapphire Saver" to 0xFF40C4FF,
            "Gold Standard" to 0xFFC19A02,
            "Diamond Elite" to 0xFFB8B8B8,
            "Diamond Standard" to 0xFFB8B8B8,
            "Corporate" to 0xFF877CE5,
        ).forEach { (tier, argb) ->
            assertEquals(tier, argb.toInt(), tierAccentColor(tier).toArgb())
        }
    }
}
