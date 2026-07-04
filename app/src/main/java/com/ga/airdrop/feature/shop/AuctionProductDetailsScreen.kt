package com.ga.airdrop.feature.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Cairo
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore

/**
 * Auction Product Details — Figma 40002072:24025, behavior from
 * FigmaAuctionProductDetailsViewController. `featured` mode drops the
 * Related Products section and swaps the CTA to "Purchase Product"
 * (opens the Amazon URL).
 */
@Composable
fun AuctionProductDetailsScreen(
    slug: String,
    featured: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AuctionProductDetailsViewModel = viewModel(key = "details/$slug/$featured") {
        AuctionProductDetailsViewModel(slug = slug, featured = featured)
    },
)
{
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val cartLines by CartStore.items.collectAsState()
    val context = LocalContext.current
    val product = state.product

    LaunchedEffect(Unit) { CartStore.init(context) }

    Column(Modifier.fillMaxSize().background(colors.gray100)) {
        ShopInnerHeader(
            title = if (featured) "Feature Product" else "Auction",
            onBack = onBack,
            trailing = {
                ShopHeaderCartIcon(count = cartLines.size, onClick = { onNavigate(Routes.CART) })
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                state.loading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandPalette.OrangeMain, strokeWidth = 2.dp)
                    }
                }
                product == null -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.error ?: "Product unavailable",
                            style = AirdropType.body1,
                            color = colors.textDescription,
                        )
                    }
                }
                else -> {
                    DetailsContent(
                        product = product,
                        related = state.related,
                        featured = featured,
                        quantity = state.quantity,
                        expanded = state.expanded,
                        cartIds = cartLines.map { it.id }.toSet(),
                        onChangeQuantity = viewModel::changeQuantity,
                        onToggleExpanded = viewModel::toggleExpanded,
                        onRelatedClick = { onNavigate(Routes.auctionProductDetails(it.routeSlug)) },
                        onRelatedToggleCart = { CartStore.toggle(it.toCartLine()) },
                        onViewMoreRelated = { onNavigate(Routes.AUCTION) },
                    )
                }
            }
        }

        // Sticky bottom CTA — Figma "Button Type".
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.glassOverlay70)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Box(Modifier.padding(Spacing.md).navigationBarsPadding()) {
                GradientButton(
                    text = if (featured) "Purchase Product" else "Add to Cart",
                    enabled = product != null,
                    onClick = {
                        if (featured) {
                            val raw = product?.amazonUrl?.trim().orEmpty()
                            if (raw.isNotEmpty()) {
                                val url = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                                    raw.replaceFirst("http://", "https://")
                                } else {
                                    "https://$raw"
                                }
                                launchExternalUrl(context, url)
                            }
                        } else {
                            viewModel.addToCart()
                        }
                    },
                )
            }
        }
    }

    // "Added to cart" / "Already in cart" alert (Swift onAddToCart).
    val added = state.addedDialog
    if (added != null && product != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            containerColor = colors.gray100,
            title = {
                Text(
                    text = if (added) "Added to cart" else "Already in cart",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
            },
            text = {
                Text(
                    text = if (added) {
                        "${product.title} added. Tap View Cart to checkout."
                    } else {
                        "${product.title} is already in your cart."
                    },
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissDialog()
                    onNavigate(Routes.CART)
                }) {
                    Text(text = "View Cart", style = AirdropType.button, color = BrandPalette.OrangeMain)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) {
                    Text(text = "OK", style = AirdropType.button, color = colors.textDarkTitle)
                }
            },
        )
    }
}

@Composable
private fun DetailsContent(
    product: ShopProduct,
    related: List<ShopProduct>,
    featured: Boolean,
    quantity: Int,
    expanded: Boolean,
    cartIds: Set<Int>,
    onChangeQuantity: (Int) -> Unit,
    onToggleExpanded: () -> Unit,
    onRelatedClick: (ShopProduct) -> Unit,
    onRelatedToggleCart: (ShopProduct) -> Unit,
    onViewMoreRelated: () -> Unit,
) {
    val colors = AirdropTheme.colors

    // ─── Hero image + dots — white section with bottom divider ───
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.gray100)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            contentScale = ContentScale.Fit,
        )
        // Paging dots — Figma shows 3 dots, middle orange (single-image
        // carousel placeholder, Swift parity).
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (index == 1) BrandPalette.OrangeMain else colors.iconShape,
                            CircleShape,
                        )
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

    // ─── Stats row — gray150 strip: reviews + shares ───
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.gray150)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_star),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(20.dp),
            )
            Text(text = "0 Reviews", style = AirdropType.body2, color = colors.iconSelected)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(20.dp),
            )
            Text(text = "50 Shares", style = AirdropType.body2, color = colors.iconSelected)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

    // ─── Title / model / price + stepper / stock ───
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column {
            Text(
                text = product.title,
                style = AirdropType.title1,
                color = colors.textDarkTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = Cairo, fontWeight = FontWeight.Normal)) {
                        append("Model: ")
                    }
                    withStyle(SpanStyle(fontFamily = Cairo, fontWeight = FontWeight.Bold)) {
                        append(product.slug?.uppercase() ?: "—")
                    }
                },
                style = AirdropType.body2.copy(lineHeight = 20.sp),
                color = colors.textDarkTitle,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                val regular = product.regularPriceUsd
                if (regular != null && regular > product.priceUsd && regular > 0) {
                    Text(
                        text = formatUsd(regular),
                        style = AirdropType.body3.copy(textDecoration = TextDecoration.LineThrough),
                        color = colors.textDescription,
                    )
                }
                Text(
                    text = formatUsd(product.priceUsd),
                    style = TextStyle(
                        fontFamily = Cairo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                    ),
                    color = BrandPalette.OrangeMain,
                )
            }
            QuantityStepper(quantity = quantity, onChange = onChangeQuantity)
        }
        Text(
            text = "Stock Quantity: ${product.inventory ?: 0}",
            style = TextStyle(
                fontFamily = Cairo,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            ),
            color = BrandPalette.BlueAccentMain,
        )
    }

    // ─── Description — gray150 section with top/bottom dividers ───
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.gray150)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = "Description", style = AirdropType.title2, color = colors.textDarkTitle)
        Text(
            text = cleanDescription(product.description),
            style = AirdropType.body2,
            color = colors.textDarkTitle,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (expanded) "See less" else "See all",
            style = AirdropType.underlineLink.copy(textDecoration = TextDecoration.Underline),
            color = BrandPalette.OrangeMain,
            modifier = Modifier.clickable(onClick = onToggleExpanded),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

    // ─── Related Products (auction mode only) ───
    if (!featured && related.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ShopSectionHeader(
                title = "Related Products",
                actionLabel = "View More",
                onAction = onViewMoreRelated,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                related.take(2).forEach { item ->
                    ShopProductCard(
                        product = item,
                        inCart = cartIds.contains(item.id),
                        onClick = { onRelatedClick(item) },
                        onToggleCart = { onRelatedToggleCart(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (related.size == 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 144x55 stepper card — Figma node 40002083:26012. */
@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .width(144.dp)
            .height(55.dp)
            .background(colors.gray150, RoundedCornerShape(14.dp))
            .border(1.37.dp, colors.iconShape, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_shop_minus_square),
            contentDescription = "Decrease quantity",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(27.dp)
                .clickable { onChange(-1) },
        )
        Text(
            text = quantity.toString(),
            style = TextStyle(
                fontFamily = Cairo,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 34.sp,
            ),
            color = colors.textDarkTitle,
        )
        Image(
            painter = painterResource(R.drawable.ic_shop_add_square),
            contentDescription = "Increase quantity",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(27.dp)
                .clickable { onChange(1) },
        )
    }
}

/**
 * Strip the simple HTML/markdown the API mixes into descriptions (Swift
 * renderProductDescription): <br>/<p> to newlines, drop other tags and
 * `**bold**` markers.
 */
private fun cleanDescription(raw: String?): String {
    if (raw.isNullOrBlank()) return "No description available."
    return raw
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("</p>"), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("**", "")
        .trim()
}
