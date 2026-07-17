package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.toArgb
import com.ga.airdrop.core.designsystem.theme.darkAirdropColors
import com.ga.airdrop.core.designsystem.theme.lightAirdropColors
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkOrangeConsistencyTest {
    @Test
    fun `dark primary controls use the approved F46427 orange`() {
        val expected = 0xFFF46427.toInt()

        assertEquals(expected, darkAirdropColors.orangeMain.toArgb())
        assertEquals(expected, darkAirdropColors.buttonStatic.toArgb())
        assertEquals(
            listOf(expected, expected),
            primaryButtonGradient(darkAirdropColors).map { it.toArgb() },
        )
    }

    @Test
    fun `light primary controls preserve the approved gradient`() {
        assertEquals(
            listOf(0xFFFF783E.toInt(), 0xFFF15114.toInt()),
            primaryButtonGradient(lightAirdropColors).map { it.toArgb() },
        )
    }
}
