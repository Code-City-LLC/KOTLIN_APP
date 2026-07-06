package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.GradientPalette
import com.ga.airdrop.core.designsystem.theme.Radius

/**
 * Primary CTA — Swift "RN MainButton main variant"
 * (FigmaLoginViewController.swift:185-193, FigmaAddAuthorizedUserViewController.swift:299-306):
 * 52dp tall, radius 14, vertical #FF783E→#F15114 gradient, Cairo SemiBold 16
 * white label.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.verticalGradient(GradientPalette.SignInButton)
                else Brush.verticalGradient(
                    listOf(BrandPalette.ButtonDisable, BrandPalette.ButtonDisable)
                )
            )
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.height(24.dp),
                color = BrandPalette.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = AirdropType.button, color = BrandPalette.White)
        }
    }
}

/**
 * Secondary CTA — white surface, 1dp #F15114 border, radius 10,
 * Cairo SemiBold 16 dark-title label.
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Swift secondary button — 52pt (FigmaCalculatorResults:296 / DropAlert:80).
            .height(52.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Swift secondary button label = orangeMain (FigmaCalculatorResults:290 /
        // DropAlert:74), not textDarkTitle.
        Text(text = text, style = AirdropType.button, color = BrandPalette.OrangeMain)
    }
}
