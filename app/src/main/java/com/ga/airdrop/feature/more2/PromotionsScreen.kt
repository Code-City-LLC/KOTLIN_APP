package com.ga.airdrop.feature.more2

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.ga.airdrop.core.external.AffiliateAndMediaLinks
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.formatUsd
import com.ga.airdrop.feature.shop.launchExternalUrl

/**
 * Promotions keeps the approved Swift banner card and adds the two canonical
 * Laravel product feeds. Amazon exits only through a Custom Tab/browser.
 */
@Composable
fun PromotionsScreen(
    onBack: () -> Unit,
    viewModel: PromotionsViewModel = viewModel(),
    onOpenAmazon: ((String) -> Unit)? = null,
    onOpenSale: ((ShopProduct) -> Unit)? = null,
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

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
                state.error != null -> PromotionsError(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::load,
                )
                state.hasLoaded &&
                    state.banners.isEmpty() &&
                    state.amazonFinds.isEmpty() &&
                    state.saleHighlights.isEmpty() -> PromotionsEmpty()
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .testTag("promotions-scroll")
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (state.amazonFinds.isNotEmpty()) {
                            PromotionSection(
                                title = "Apple Finds on Amazon",
                                subtitle =
                                    "Curated products; price and availability may change on Amazon.",
                                tag = "promotions-amazon-section",
                            ) {
                                state.amazonFinds.forEachIndexed { index, product ->
                                    PromotionProductCard(
                                        product = product,
                                        badge = "AMAZON FIND",
                                        action = "Shop on Amazon",
                                        tag = "promotions-amazon-$index",
                                        onClick = {
                                            val url =
                                                AffiliateAndMediaLinks.validateAmazonAffiliateUrl(
                                                    product.amazonUrl,
                                                ) ?: return@PromotionProductCard
                                            onOpenAmazon?.invoke(url)
                                                ?: launchExternalUrl(context, url)
                                        },
                                    )
                                }
                                Text(
                                    text = AffiliateAndMediaLinks.AMAZON_ASSOCIATE_DISCLOSURE,
                                    style = AirdropType.body3,
                                    color = colors.textDescription,
                                    modifier = Modifier.testTag("promotions-amazon-disclosure"),
                                )
                            }
                        }

                        if (state.saleHighlights.isNotEmpty()) {
                            PromotionSection(
                                title = "AirDrop Sale Highlights",
                                subtitle = "Featured auction items available through AirDrop.",
                                tag = "promotions-sale-section",
                            ) {
                                state.saleHighlights.forEachIndexed { index, product ->
                                    PromotionProductCard(
                                        product = product,
                                        badge = "AIRDROP SALE",
                                        action = if (onOpenSale != null) "View sale" else null,
                                        tag = "promotions-sale-$index",
                                        onClick = onOpenSale?.let { callback ->
                                            { callback(product) }
                                        },
                                    )
                                }
                            }
                        }

                        if (state.banners.isNotEmpty()) {
                            PromotionSection(
                                title = "More Promotions",
                                subtitle = "Current AirDrop campaigns and announcements.",
                                tag = "promotions-banner-section",
                            ) {
                                state.banners.forEachIndexed { index, banner ->
                                    PromotionCard(banner = banner, index = index)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionsError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .testTag("promotions-all-feeds-error"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = AirdropType.subtitle1,
            color = AlertPalette.Error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        More2PrimaryButton(
            text = "Retry",
            onClick = onRetry,
            modifier = Modifier
                .width(132.dp)
                .testTag("promotions-retry"),
            height = 48.dp,
            radius = 14.dp,
        )
    }
}

@Composable
private fun PromotionsEmpty() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .testTag("promotions-empty"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No promotions available right now. Check back soon!",
            style = AirdropType.body2,
            color = AirdropTheme.colors.textDescription,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PromotionSection(
    title: String,
    subtitle: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
            Text(text = subtitle, style = AirdropType.body3, color = colors.textDescription)
        }
        content()
    }
}

@Composable
private fun PromotionProductCard(
    product: ShopProduct,
    badge: String,
    action: String?,
    tag: String,
    onClick: (() -> Unit)?,
) {
    val colors = AirdropTheme.colors
    var imageFailed by remember(product.id, product.imageUrl) { mutableStateOf(false) }
    val interaction = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    More2OuterCard(
        Modifier
            .heightIn(min = 144.dp)
            .then(interaction)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.gray200)
                    .testTag("$tag-image"),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageFailed) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.title,
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = badge,
                    style = AirdropType.subtitle3,
                    color = BrandPalette.OrangeMain,
                )
                Text(
                    text = product.title,
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("$tag-title"),
                )
                Text(
                    text = formatUsd(product.priceUsd),
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    modifier = Modifier.testTag("$tag-price"),
                )
                if (action != null) {
                    Text(
                        text = "$action  ›",
                        style = AirdropType.subtitle3,
                        color = BrandPalette.OrangeMain,
                        modifier = Modifier.testTag("$tag-action"),
                    )
                }
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
