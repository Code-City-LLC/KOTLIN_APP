package com.ga.airdrop.feature.more2

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.PromotionalBanner

/**
 * Promotions — Figma node 40001646:14035, behavior from
 * FigmaPromotionsViewController: banner cards (hero image + description with
 * View Details/View Less expand toggle), loading/empty/error states.
 */
@Composable
fun PromotionsScreen(
    onBack: () -> Unit,
    viewModel: PromotionsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        More2InnerHeader(title = "Promotions", onBack = onBack)

        Box(Modifier.weight(1f)) {
            when {
                state.loading -> More2Loading()
                state.error != null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            style = AirdropType.subtitle1,
                            color = AlertPalette.Error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                state.banners.isEmpty() && state.hasLoaded -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No promotions available right now. Check back soon!",
                            style = AirdropType.body2,
                            color = colors.textDescription,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        state.banners.forEach { banner -> PromotionCard(banner) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionCard(banner: PromotionalBanner) {
    val colors = AirdropTheme.colors
    var expanded by remember { mutableStateOf(false) }

    More2OuterCard {
        AsyncImage(
            model = banner.imageUrl ?: banner.imagePath,
            contentDescription = banner.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(colors.gray200),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .animateContentSize()
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = banner.description ?: banner.title.orEmpty(),
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .height(24.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (expanded) "View Less" else "View Details",
                    style = AirdropType.subtitle2,
                    color = BrandPalette.OrangeMain,
                )
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
