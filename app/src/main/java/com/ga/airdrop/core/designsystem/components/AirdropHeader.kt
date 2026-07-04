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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Cairo
import com.ga.airdrop.core.designsystem.theme.Spacing
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Shared tab header — Figma "Header Type" (node 40000817:8974).
 * Glass overlay (#292929 at 70%) intended to sit over the hero image:
 * greeting + tier link on the left; bell, cart (badged) and AirCoin count
 * on the right, 20dp gaps, 24dp icons, 62dp content row.
 */
@Composable
fun AirdropHeader(
    greeting: String,
    tierName: String,
    tierColor: Color = Color(0xFFC19A02),
    cartCount: Int = 0,
    airCoins: String = "",
    onTierClick: () -> Unit = {},
    onBellClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onAirCoinsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    overlayColor: Color = Color(0xB3292929), // gradiant/black/70
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(overlayColor)
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
                    color = BrandPalette.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.clickable(onClick = onTierClick),
                ) {
                    Text(
                        text = tierName,
                        style = AirdropType.subtitle2.copy(lineHeight = 22.sp),
                        color = tierColor,
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_small_arrow_down),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_header_bell),
                    contentDescription = "Notifications",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBellClick),
                )
                Box {
                    Image(
                        painter = painterResource(R.drawable.ic_header_cart),
                        contentDescription = "Cart",
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
                            color = BrandPalette.White,
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
    }
}
