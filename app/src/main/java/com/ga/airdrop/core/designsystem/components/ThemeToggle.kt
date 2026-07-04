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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController

/**
 * Figma light/dark pill toggle (node 40003881:68304): 48dp-wide gray200
 * track, white knob with the sun glyph; knob sits right in light mode and
 * left in dark mode per the design frames.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    val isDark = colors.isDark
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
                .background(colors.gray100, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_toggle_sun),
                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
