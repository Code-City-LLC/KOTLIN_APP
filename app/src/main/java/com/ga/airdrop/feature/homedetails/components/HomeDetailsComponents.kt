package com.ga.airdrop.feature.homedetails.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Inner-page header shared by the Home drill-down screens — port of the RN
 * InnerHeader used by every Figma<Screen>ViewController: 56dp bar under the
 * status bar, back chevron (24dp in a 36dp tap box, 12dp from the edge),
 * centered Title1 label, optional 24dp trailing action, 1dp divider.
 */
@Composable
fun HomeDetailsHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    titleStyle: TextStyle = AirdropType.title1,
    showDivider: Boolean = true,
    trailingIconRes: Int? = null,
    trailingContentDescription: String? = null,
    onTrailingClick: () -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val background = containerColor ?: colors.gray100
    val tint = contentColor ?: colors.textDarkTitle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            HeaderIconButton(
                iconRes = R.drawable.ic_small_arrow_down,
                contentDescription = "Back",
                tint = tint,
                rotation = 90f,
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
            )
            Text(
                text = title,
                style = titleStyle,
                color = tint,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 52.dp),
            )
            if (trailingIconRes != null) {
                HeaderIconButton(
                    iconRes = trailingIconRes,
                    contentDescription = trailingContentDescription,
                    tint = tint,
                    onClick = onTrailingClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    iconRes: Int,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
        )
    }
}

/**
 * Copy-confirmation pill — Figma 40000944:3698 ("All the information is
 * copied"). Light-gray glass pill below the header, dark Body2 label.
 */
@Composable
fun CopiedToastPill(text: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .background(colors.gray300.copy(alpha = 0.92f), RoundedCornerShape(60.dp))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Text(
            text = text,
            style = AirdropType.body2,
            color = colors.textDarkTitle,
        )
    }
}
