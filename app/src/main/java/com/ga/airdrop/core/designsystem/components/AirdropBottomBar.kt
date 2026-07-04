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
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Custom bottom nav — Figma "Nav Bar Menu" (node 40000798:6657).
 * Glass bar (white/70 light, #292929/70 dark) with hairline top border;
 * active tab = orange filled icon + Cairo SemiBold 14 label; inactive tabs
 * icon-only outlines tinted iconSelected.
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
            .background(colors.glassOverlay70)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .size(width = 0.dp, height = 1.dp)
                .fillMaxWidth()
                .background(colors.iconShape)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
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
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val tint: Color? = if (isSelected) BrandPalette.OrangeMain else colors.iconSelected
        Image(
            painter = painterResource(tab.iconRes(isSelected)),
            contentDescription = tab.label,
            modifier = Modifier.size(if (isSelected) 24.dp else 28.dp),
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
        if (isSelected) {
            Text(
                text = tab.label,
                style = AirdropType.subtitle2,
                color = BrandPalette.OrangeMain,
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
