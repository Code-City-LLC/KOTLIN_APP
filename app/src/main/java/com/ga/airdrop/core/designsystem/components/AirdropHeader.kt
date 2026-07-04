package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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

/**
 * Shared tab header — Figma "Header Type".
 * [AirdropHeaderStyle.OverImage]: black/70 glass + white text (Home hero).
 * [AirdropHeaderStyle.Solid]: white/70 glass + divider + dark-title text
 * (Shipments/Shop/Help/More), tier label in gradient gold.
 */
enum class AirdropHeaderStyle { OverImage, Solid }

private val TierGoldGradient = Brush.verticalGradient(
    0.47f to Color(0xFFEFBF04),
    1.0f to Color(0xFF8C6F01),
)

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
    val background = if (overImage) Color(0xB3292929) else colors.glassOverlay70
    val contentColor = if (overImage) BrandPalette.White else colors.textDarkTitle
    val iconTint = if (overImage) BrandPalette.White else colors.iconSelected

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = Spacing.md, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = AirdropType.subtitle2,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tierName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.clickable(onClick = onTierClick),
                    ) {
                        if (overImage) {
                            Text(
                                text = tierName,
                                style = AirdropType.subtitle2.copy(lineHeight = 22.sp),
                                color = Color(0xFFC19A02),
                            )
                        } else {
                            Text(
                                text = tierName,
                                style = AirdropType.subtitle3.copy(brush = TierGoldGradient),
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.ic_small_arrow_down),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                if (overImage) BrandPalette.White else colors.iconSelected
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_header_bell),
                    contentDescription = "Notifications",
                    colorFilter = ColorFilter.tint(iconTint),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBellClick),
                )
                Box {
                    Image(
                        painter = painterResource(R.drawable.ic_header_cart),
                        contentDescription = "Cart",
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onCartClick),
                    )
                    if (cartCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-3).dp)
                                .background(BrandPalette.OrangeMain, RoundedCornerShape(40.dp))
                                .padding(horizontal = Spacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cartCount.toString(),
                                style = TextStyle(
                                    fontFamily = Cairo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                ),
                                color = BrandPalette.White,
                            )
                        }
                    }
                }
                if (airCoins.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onAirCoinsClick),
                    ) {
                        Text(
                            text = airCoins,
                            style = AirdropType.subtitle1,
                            color = contentColor,
                        )
                        Image(
                            painter = painterResource(R.drawable.img_coin_stack),
                            contentDescription = "AirCoins",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        if (!overImage) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider)
            )
        }
    }
}
