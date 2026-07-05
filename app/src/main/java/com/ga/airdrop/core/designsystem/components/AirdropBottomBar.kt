package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Cairo
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Custom bottom nav — Swift `FigmaBottomTabBar` (FigmaTabHeader.swift:427+).
 *
 * Swift places a blur under an OPAQUE gray200 overlay ("not a translucent
 * gray layer that lets page content shift the perceived color") with a 1pt
 * iconShape top divider. Items: 28pt icons; active = orangeMain icon +
 * Cairo SemiBold 14 label; inactive = icon only, tinted iconSelected,
 * vertically centered (Swift pushes inactive icons down 14pt inside the
 * 67pt row so both states read centered).
 */
enum class AirdropTab(val label: String) {
    Home("Home"),
    Shipments("Shipment"),
    Shop("Shop"),
    Help("Help"),
    More("More"),
}

@Composable
fun AirdropBottomBar(
    selected: AirdropTab,
    onSelect: (AirdropTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Swift: opaque gray200 surface — never a transparent wash.
            .background(colors.gray200)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Swift row: top 15, horizontal 20, height 67.
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm1)
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AirdropTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    isSelected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun TabItem(
    tab: AirdropTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Swift: active icon at row top (label below); inactive icon +14pt
        // so the lone glyph reads vertically centered.
        verticalArrangement = if (isSelected) Arrangement.Top else Arrangement.Center,
    ) {
        val tint: Color = if (isSelected) BrandPalette.OrangeMain else colors.iconSelected
        Image(
            painter = painterResource(tab.iconRes(isSelected)),
            contentDescription = tab.label,
            // Swift: 28pt in both states.
            modifier = Modifier.size(28.dp),
            colorFilter = ColorFilter.tint(tint),
        )
        if (isSelected) {
            Text(
                text = tab.label,
                // Swift: Cairo SemiBold 14, 5pt below the icon.
                style = TextStyle(
                    fontFamily = Cairo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = BrandPalette.OrangeMain,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun AirdropTab.iconRes(selected: Boolean): Int = when (this) {
    AirdropTab.Home -> if (selected) R.drawable.ic_home_filled else R.drawable.ic_home
    AirdropTab.Shipments -> if (selected) R.drawable.ic_shipment_filled else R.drawable.ic_nav_shipment
    AirdropTab.Shop -> if (selected) R.drawable.ic_shop_filled else R.drawable.ic_nav_shop
    AirdropTab.Help -> if (selected) R.drawable.ic_help_filled else R.drawable.ic_nav_help
    AirdropTab.More -> if (selected) R.drawable.ic_more_filled else R.drawable.ic_nav_more
}
