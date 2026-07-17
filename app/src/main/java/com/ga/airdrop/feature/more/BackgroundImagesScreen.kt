package com.ga.airdrop.feature.more

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Background Images — Swift FigmaBackgroundImagesViewController takes
 * precedence over Figma frame 40006644:67051 where they conflict: Swift uses a
 * 2-column 220dp portrait grid of IDs 0-13, while Figma shows 335x150
 * single-column landscape tiles.
 */
@Composable
fun BackgroundImagesScreen(
    onBack: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    var selectedId by remember { mutableIntStateOf(BackgroundStore.selectedId(context)) }

    Box(Modifier.fillMaxSize().background(colors.gray100)) {
        Column(Modifier.fillMaxSize()) {
            // Swift: this screen's header title is Title1 (Bold 18).
            MoreDetailHeader(
                title = "Background Images",
                onBack = onBack,
                titleStyle = AirdropType.title1,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("background-images-grid"),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = 40.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            text = "Choose a background for your Home tab",
                            style = AirdropType.body2,
                            color = colors.textDescription,
                        )
                        Box(Modifier.height(4.dp))
                    }
                }
                items(BackgroundStore.choices, key = { it.id }) { choice ->
                    BackgroundTile(
                        choice = choice,
                        isDark = colors.isDark,
                        selected = choice.id == selectedId,
                        onClick = { selectedId = choice.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            MoreBottomButtonBar(
                text = "Save",
                onClick = {
                    BackgroundStore.save(context, selectedId)
                    Toast.makeText(
                        context,
                        "Background image saved successfully",
                        Toast.LENGTH_SHORT,
                    ).show()
                    onBack()
                },
            )
        }
    }
}

/**
 * Swift tile: 220dp portrait thumbnail, radius 15; top-left 44dp selection
 * circle (orange + white check when selected, white/90 ring otherwise) or the
 * orange "Default Image" pill on the unselected default tile.
 */
@Composable
private fun BackgroundTile(
    choice: BackgroundStore.Choice,
    isDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .testTag("background-tile-${choice.id}")
            .height(220.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray300)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(if (isDark) choice.darkRes else choice.lightRes),
            contentDescription = "Background ${choice.id}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        when {
            selected -> Box(
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(44.dp)
                    .testTag("background-selected-${choice.id}")
                    .background(AirdropTheme.colors.orangeMain, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Selected",
                    colorFilter = ColorFilter.tint(BrandPalette.White),
                    modifier = Modifier.size(20.dp),
                )
            }
            choice.isDefault -> Box(
                modifier = Modifier
                    .padding(Spacing.md)
                    .height(28.dp)
                    .testTag("background-default-pill")
                    .background(AirdropTheme.colors.orangeMain, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Default Image",
                    style = AirdropType.subtitle3,
                    color = BrandPalette.White,
                )
            }
            else -> Box(
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(44.dp)
                    .testTag("background-unselected-${choice.id}")
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .border(2.dp, AirdropTheme.colors.iconShape, CircleShape),
            )
        }
    }
}
