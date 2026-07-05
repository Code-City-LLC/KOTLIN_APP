package com.ga.airdrop.feature.homedetails.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
internal object HomeDetailsHeaderTags {
    const val TITLE = "home-details-header-title"
    const val BACK = "home-details-header-back"
    const val BACK_ICON = "home-details-header-back-icon"
}

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
                    .padding(start = 12.dp)
                    .testTag(HomeDetailsHeaderTags.BACK),
                iconTag = HomeDetailsHeaderTags.BACK_ICON,
            )
            AutoscalingHeaderTitle(
                text = title,
                style = titleStyle,
                color = tint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 52.dp)
                    .testTag(HomeDetailsHeaderTags.TITLE),
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
    iconTag: String? = null,
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
                .rotate(rotation)
                .then(if (iconTag != null) Modifier.testTag(iconTag) else Modifier),
        )
    }
}

@Composable
private fun AutoscalingHeaderTitle(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val fit = remember(text, style, maxWidthPx, textMeasurer) {
            if (maxWidthPx <= 0) {
                HeaderTitleFit(scale = 1f, maxLines = 2)
            } else {
                HeaderTitleScaleSteps
                    .firstOrNull { scale ->
                        val result = textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = style.scaledBy(scale),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            constraints = Constraints(maxWidth = maxWidthPx),
                        )
                        !result.hasVisualOverflow
                    }
                    ?.let { HeaderTitleFit(scale = it, maxLines = 1) }
                    ?: HeaderTitleFit(scale = HEADER_TITLE_MIN_SCALE, maxLines = 2)
            }
        }

        Text(
            text = text,
            style = style.scaledBy(fit.scale),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = fit.maxLines,
            softWrap = fit.maxLines > 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private data class HeaderTitleFit(
    val scale: Float,
    val maxLines: Int,
)

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(
        fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )

private const val HEADER_TITLE_MIN_SCALE = 0.8f

private val HeaderTitleScaleSteps = listOf(1f, 0.95f, 0.9f, 0.85f, HEADER_TITLE_MIN_SCALE)

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
