package com.ga.airdrop.feature.more

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Background Images — Figma frame 40006644:67051 (section 40006644:65735),
 * behavior from FigmaBackgroundImagesViewController: full-width hero
 * thumbnails, orange check badge on the selected tile, "Default Image" pill
 * on the default tile when unselected, pinned Save CTA persisting the
 * choice via BackgroundStore (Home re-reads it — RECONCILE).
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
            ) {
                // Swift: subtitle line above the grid.
                Text(
                    text = "Choose a background for your Home tab",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
                // Figma 40006644: single-column list of full-width 150dp
                // landscape tiles (radius 15), 20dp gap (Column spacedBy).
                BackgroundStore.choices.forEach { choice ->
                    BackgroundTile(
                        choice = choice,
                        isDark = colors.isDark,
                        selected = choice.id == selectedId,
                        onClick = { selectedId = choice.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
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
 * Full-width tile: 140dp, radius 15; top-left 44dp selection circle (orange
 * + white check when selected, white/90 ring otherwise) or the orange
 * "Default Image" pill on the unselected default tile.
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
            // Figma 40006644: 150dp landscape tile, radius 15, image object-cover.
            .height(150.dp)
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
            // Figma: 30dp selection glyph, 20dp inset (top-left).
            selected -> Box(
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(30.dp)
                    .background(BrandPalette.OrangeMain, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Selected",
                    colorFilter = ColorFilter.tint(BrandPalette.White),
                    modifier = Modifier.size(18.dp),
                )
            }
            // Figma: orange "Default Image" pill — 28dp, radius 50, px15,
            // Cairo Regular 14 (body2) white.
            choice.isDefault -> Box(
                modifier = Modifier
                    .padding(Spacing.md)
                    .height(28.dp)
                    .background(BrandPalette.OrangeMain, RoundedCornerShape(50.dp))
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Default Image",
                    style = AirdropType.body2,
                    color = BrandPalette.White,
                )
            }
            else -> Box(
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .border(2.dp, AirdropTheme.colors.iconShape, CircleShape),
            )
        }
    }
}
