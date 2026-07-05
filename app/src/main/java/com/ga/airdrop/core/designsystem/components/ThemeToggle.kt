package com.ga.airdrop.core.designsystem.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController

/**
 * Figma light/dark pill toggle (node 40003881:68304): 48dp-wide gray200
 * track with a rounded knob. In LIGHT mode the knob sits right, is white
 * (neutral/0) and carries the orange sun glyph. In DARK mode the knob sits
 * left, is #4D4D4D (shaps/icon/shaps) and carries the blue crescent moon —
 * verified pixel-for-pixel against the dark AuthLanding header render
 * (node 40005296:24400). The glyph must swap with the theme; showing the
 * sun in dark mode was the reported "wrong login icon" bug.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    val isDark = colors.isDark
    // Figma: light knob = neutral/0 white (gray100 resolves to #FFFFFF in
    // light); dark knob = shaps/icon/shaps #4D4D4D (gray100 dark is #383838,
    // too dark — pin the exact Figma value).
    val knobColor = if (isDark) Color(0xFF4D4D4D) else colors.gray100
    Row(
        modifier = modifier
            .width(48.dp)
            .background(colors.gray200, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                ThemeController.set(
                    if (isDark) ThemeController.Mode.LIGHT else ThemeController.Mode.DARK
                )
            }
            .padding(2.dp)
            .animateContentSize(),
        horizontalArrangement = if (isDark) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .shadow(1.dp, CircleShape)
                .background(knobColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    if (isDark) R.drawable.ic_toggle_moon else R.drawable.ic_toggle_sun
                ),
                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
