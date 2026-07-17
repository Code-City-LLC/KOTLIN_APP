package com.ga.airdrop.feature.more2

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
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
import com.ga.airdrop.feature.shop.AMAZON_ASSOCIATES_DISCLOSURE
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.ShopProductCard
import com.ga.airdrop.feature.shop.amazonAssociatesUrlOrNull

/**
 * Promotions — Figma node 40001646:14035, behavior from
 * FigmaPromotionsViewController: banner cards (hero image + description with
 * View Details/View Less expand toggle), loading/empty/error states.
 */
@Composable
fun PromotionsScreen(
    onBack: () -> Unit,
    viewModel: PromotionsViewModel = viewModel(),
    onOpenAmazon: (String) -> Unit = {},
    onOpenSale: (ShopProduct) -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val hasContent = state.banners.isNotEmpty() ||
        state.appleFinds.isNotEmpty() ||
        state.saleHighlights.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag("promotions-root")
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
                !hasContent && state.hasLoaded -> {
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
                            .testTag("promotions-scroll")
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        state.banners.forEachIndexed { index, banner ->
                            PromotionCard(banner = banner, index = index)
                        }
                        if (state.appleFinds.isNotEmpty()) {
                            PromotionProductRail(
                                title = "Apple Finds on Amazon",
                                products = state.appleFinds,
                                tag = "promotions-apple",
                                disclosure = AMAZON_ASSOCIATES_DISCLOSURE,
                                onProduct = { product ->
                                    product.amazonAssociatesUrlOrNull()?.let(onOpenAmazon)
                                },
                            )
                        }
                        if (state.saleHighlights.isNotEmpty()) {
                            PromotionProductRail(
                                title = "Sale Highlights",
                                products = state.saleHighlights,
                                tag = "promotions-sale",
                                onProduct = onOpenSale,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionProductRail(
    title: String,
    products: List<ShopProduct>,
    tag: String,
    disclosure: String? = null,
    onProduct: (ShopProduct) -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$tag-section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = AirdropType.title1,
            color = colors.textDarkTitle,
        )
        disclosure?.let {
            Text(
                text = it,
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.testTag("$tag-disclosure"),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                items = products,
                key = { index, product -> product.id.takeIf { it != 0 } ?: "$tag-$index" },
            ) { index, product ->
                ShopProductCard(
                    product = product,
                    inCart = false,
                    onClick = { onProduct(product) },
                    onToggleCart = null,
                    modifier = Modifier
                        .width(160.dp)
                        .testTag("$tag-card-$index"),
                )
            }
        }
    }
}

@Composable
private fun PromotionCard(banner: PromotionalBanner, index: Int) {
    val colors = AirdropTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val tag = "promotions-card-$index"

    More2OuterCard(Modifier.testTag(tag)) {
        AsyncImage(
            model = banner.imageUrl ?: banner.imagePath,
            contentDescription = banner.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(colors.gray200)
                .testTag("$tag-hero"),
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
                modifier = Modifier.testTag("$tag-description"),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .height(24.dp)
                    .testTag("$tag-toggle")
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
