package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Shared list-load-failure card — Swift quality pass 89fbb11
 * (accessibility ids airdrop.loadFailure / airdrop.loadFailure.retry):
 * gray100 card radius 14 with a 1dp hairline border, warning glyph,
 * "Couldn't load" title, per-screen message, filled 132x44 Retry.
 *
 * Rule (Swift): show ONLY when the list is empty AND not loading AND the
 * load failed — already-loaded/cached rows always win over this card.
 *
 * Swift uses a wifi-exclamation glyph; no wifi asset exists in the Android
 * drawable set and material-icons is not a dependency, so the calculator's
 * info-circle stands in, tinted textDescription like the Swift icon.
 */
@Composable
fun LoadFailureCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Kebab-case prefix so multiple cards on one screen keep unique tags. */
    testTagPrefix: String = "load-failure",
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.gray100)
            .border(1.dp, colors.cardHairline, RoundedCornerShape(14.dp))
            .padding(Spacing.md)
            .testTag("$testTagPrefix-card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_calc_info_circle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDescription),
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = "Couldn't load",
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
        )
        Text(
            text = message,
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .padding(top = Spacing.xs)
                .width(132.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.buttonStatic)
                .clickable(onClick = onRetry)
                .testTag("$testTagPrefix-retry"),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Retry", style = AirdropType.button, color = BrandPalette.White)
        }
    }
}
