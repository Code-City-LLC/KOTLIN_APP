package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R

/**
 * One row policy for every tier benefit surface. Figma owns the 24dp page
 * artwork; Swift owns the fixed 18dp sheet check/x treatment. Both keep the
 * mark outside the weighted text column so backend copy can wrap to any
 * number of lines without shrinking or distorting the icon.
 */
internal sealed interface TierBenefitRowVisual {
    data object Page : TierBenefitRowVisual

    data class Sheet(
        val gained: Boolean,
        val markColor: Color,
    ) : TierBenefitRowVisual
}

@Composable
internal fun TierBenefitRow(
    text: String,
    visual: TierBenefitRowVisual,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration? = null,
    rowTag: String? = null,
    iconTag: String? = null,
    textTag: String? = null,
) {
    val gap = when (visual) {
        TierBenefitRowVisual.Page -> 16.dp
        is TierBenefitRowVisual.Sheet -> 10.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .optionalTestTag(rowTag),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Top,
    ) {
        when (visual) {
            TierBenefitRowVisual.Page -> PageBenefitCheck(
                modifier = Modifier
                    .requiredSize(24.dp)
                    .optionalTestTag(iconTag),
            )

            is TierBenefitRowVisual.Sheet -> Box(Modifier.padding(top = 2.dp)) {
                TierSheetMark(
                    gained = visual.gained,
                    markColor = visual.markColor,
                    modifier = Modifier
                        .requiredSize(18.dp)
                        .optionalTestTag(iconTag),
                )
            }
        }

        Text(
            text = text,
            style = textStyle.copy(textDecoration = textDecoration),
            color = textColor,
            softWrap = true,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .optionalTestTag(textTag),
        )
    }
}

/** White check in an 18%-white circle — Swift/Figma page artwork, fixed 24dp. */
@Composable
private fun PageBenefitCheck(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.requiredSize(12.dp),
        )
    }
}

@Composable
internal fun TierSheetMark(
    gained: Boolean,
    markColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clip(CircleShape)) {
        drawCircle(Color.White.copy(alpha = if (gained) 1f else 0.75f))
        val stroke = size.minDimension * 0.12f
        if (gained) {
            val check = Path().apply {
                moveTo(size.width * 0.27f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.68f)
                lineTo(size.width * 0.73f, size.height * 0.35f)
            }
            drawPath(
                path = check,
                color = markColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            val inset = size.minDimension * 0.31f
            drawLine(
                color = markColor,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = markColor,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun Modifier.optionalTestTag(tag: String?): Modifier =
    if (tag == null) this else testTag(tag)
