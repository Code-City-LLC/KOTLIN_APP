package com.ga.airdrop.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorParityTest {

    @Test
    fun sharedHairlinesMatchCurrentSwiftTokens() {
        assertEquals(0xFFE5E5E5.toInt(), lightAirdropColors.divider.toArgb())
        assertEquals(0xFFE5E5E5.toInt(), lightAirdropColors.iconShape.toArgb())
        assertEquals(0xFFE5E5E5.toInt(), lightAirdropColors.cardHairline.toArgb())
        assertEquals(0xFF404040.toInt(), darkAirdropColors.divider.toArgb())
        assertEquals(0xFF4D4D4D.toInt(), darkAirdropColors.iconShape.toArgb())
        assertEquals(0xFF4D4D4D.toInt(), darkAirdropColors.cardHairline.toArgb())
    }

    @Test
    fun flatGlassFallbackPreservesTintAndHomeCardsMatchPostBlurColor() {
        assertEquals(FROSTED_GLASS_FALLBACK_ALPHA, lightAirdropColors.frostedGlassSurface.alpha, 0.003f)
        assertEquals(FROSTED_GLASS_FALLBACK_ALPHA, darkAirdropColors.frostedGlassSurface.alpha, 0.003f)
        assertEquals(0xF7FFFFFF.toInt(), lightAirdropColors.frostedGlassSurface.toArgb())
        assertEquals(0xF7292929.toInt(), darkAirdropColors.frostedGlassSurface.toArgb())
        assertEquals(0xFFFBFBFB.toInt(), lightAirdropColors.frostedGlassCardSurface.toArgb())
        assertEquals(0xFF2E2E2E.toInt(), darkAirdropColors.frostedGlassCardSurface.toArgb())
        assertTrue(lightAirdropColors.frostedGlassSurface.alpha > lightAirdropColors.glassOverlay70.alpha)
        assertTrue(darkAirdropColors.frostedGlassSurface.alpha > darkAirdropColors.glassOverlay70.alpha)
    }
}
