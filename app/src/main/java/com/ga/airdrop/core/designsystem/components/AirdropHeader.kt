package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Cairo
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.util.Calendar

/**
 * Shared tab header — Swift `FigmaTabHeader` (Figma node 40000817:8974).
 *
 * Shared tab chrome follows Swift `FigmaTabHeader`: both hero and frosted
 * styles draw an opaque `gray200` surface over the blur layer. The only
 * difference:
 *  - [AirdropHeaderStyle.OverImage] (Home hero): no bottom divider.
 *  - [AirdropHeaderStyle.Solid] (Shipments/Shop/Help/More): 1dp divider.
 *
 * Greeting = subtitle2 textDarkTitle; tier = subtitle2 in the tier's accent
 * color with a 14pt chevron tinted the same (FigmaTabHeader.swift:355-371);
 * bell/cart = 24pt iconSelected; AirCoin pill = subtitle1 value + 28x24 coin.
 */
enum class AirdropHeaderStyle { OverImage, Solid }

/**
 * Tier accent colors — Swift FigmaTabHeader.tierAccentColor (lines 385-398).
 * Gold uses the Figma literal; others use the light/visible end of RN's
 * gradient pairs for legibility.
 */
private fun tierAccentColor(name: String): Color = when (name) {
    "Inactive" -> Color(0xFFF1A88C)
    "Ruby Starter" -> Color(0xFFD2554D)
    "Sapphire Saver" -> Color(0xFF40C4FF)
    "Gold Standard" -> Color(0xFFC19A02)
    "Platinum Priority", "Platinum Standard" -> Color(0xFFCACACA)
    "Diamond Elite", "Diamond Standard" -> Color(0xFFB8B8B8)
    "Corporate" -> Color(0xFF877CE5)
    else -> Color(0xFFC19A02)
}

@Composable
fun AirdropHeader(
    greeting: String,
    tierName: String,
    style: AirdropHeaderStyle = AirdropHeaderStyle.OverImage,
    cartCount: Int = 0,
    airCoins: String = "",
    onTierClick: () -> Unit = {},
    onBellClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onAirCoinsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val overImage = style == AirdropHeaderStyle.OverImage
    // Swift-precedence chrome: Figma shows translucent hero chrome, but
    // FigmaTabHeader.swift overlays opaque DesignTokens.Color.gray200 for both
    // .hero and .frosted styles. AirdropChromeTest enforces this source of truth.
    val headerBg = AirdropChrome.headerBackground(overImage, colors.gray200)
    val headerText = colors.textDarkTitle
    val headerIcon = colors.iconSelected
    val resolvedGreeting = greeting.ifBlank { defaultGreetingForNow() }
    val resolvedTierName = tierName.ifBlank { DEFAULT_TIER_NAME }
    val resolvedAirCoins = airCoins.ifBlank { DEFAULT_AIRCOINS }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(headerBg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Swift: greeting top 2, tier +2 below, bottom breathing -4
                // inside the 106pt header (47pt content under the safe area).
                .padding(start = Spacing.md, end = 16.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = resolvedGreeting,
                    style = AirdropType.subtitle2,
                    color = headerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val accent = tierAccentColor(resolvedTierName)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.clickable(onClick = onTierClick),
                ) {
                    Text(
                        text = resolvedTierName,
                        style = AirdropType.subtitle2,
                        color = accent,
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_small_arrow_down),
                        contentDescription = null,
                        // Swift: 14pt chevron tinted to the tier accent.
                        colorFilter = ColorFilter.tint(accent),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_header_bell),
                    contentDescription = "Notifications",
                    colorFilter = ColorFilter.tint(headerIcon),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBellClick),
                )
                Box(Modifier.padding(start = 20.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_header_cart),
                        contentDescription = "Cart",
                        colorFilter = ColorFilter.tint(headerIcon),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onCartClick),
                    )
                    if (cartCount > 0) {
                        // Swift cartBadgeLabel: 18pt circle, top -2 / end +6,
                        // bold 10, white on orangeMain.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-2).dp)
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .background(BrandPalette.OrangeMain, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cartCount.toString(),
                                style = TextStyle(
                                    fontFamily = Cairo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                ),
                                color = BrandPalette.White,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 19.dp)
                        .clickable(onClick = onAirCoinsClick),
                ) {
                    Text(
                        text = resolvedAirCoins,
                        style = AirdropType.subtitle1,
                        color = headerText,
                    )
                    // RN AirCoinButton: Coins.png at 28x24, 4dp gap.
                    Image(
                        painter = painterResource(R.drawable.img_coin_stack),
                        contentDescription = "AirCoins",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(28.dp)
                            .height(24.dp),
                    )
                }
            }
        }
        if (style == AirdropHeaderStyle.Solid) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
        }
    }
}

private const val DEFAULT_TIER_NAME = "Gold Standard"
private const val DEFAULT_AIRCOINS = "0"

private fun defaultGreetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> "Good Morning"
    in 12..16 -> "Good Afternoon"
    else -> "Good Evening"
}
