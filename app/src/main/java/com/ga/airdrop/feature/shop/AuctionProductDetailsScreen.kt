package com.ga.airdrop.feature.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore
import java.util.Locale

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
    // Swift onPurchaseProduct alert when a featured product has no amazon_url.
    var showLinkUnavailable by remember { mutableStateOf(false) }
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

        // Sticky bottom CTA — Swift FigmaAuctionProductDetailsViewController.swift:745-782:
        // gray100 bar, iconShape top divider, solid orangeMain button
        // (radius 10, height 52), insets top 14 / sides 20 / bottom 8.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
            Box(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(BrandPalette.OrangeMain, RoundedCornerShape(10.dp))
                        .clickable(enabled = product != null) {
                            if (featured) {
                                val raw = product?.amazonUrl?.trim().orEmpty()
                                if (raw.isNotEmpty()) {
                                    val url = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                                        raw.replaceFirst("http://", "https://")
                                    } else {
                                        "https://$raw"
                                    }
                                    launchExternalUrl(context, url)
                                } else {
                                    // Swift onPurchaseProduct (:817-831): alert
                                    // instead of a dead no-op.
                                    showLinkUnavailable = true
                                }
                            } else {
                                viewModel.addToCart()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (featured) "Purchase Product" else "Add to Cart",
                        style = AirdropType.button,
                        color = BrandPalette.White,
                    )
                }
            }
        }
    }

    if (showLinkUnavailable) {
        AlertDialog(
            onDismissRequest = { showLinkUnavailable = false },
            containerColor = colors.gray100,
            title = {
                Text(
                    text = "Product link unavailable",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
            },
            text = {
                Text(
                    text = "No purchase link was returned for this feature product.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLinkUnavailable = false }) {
                    Text(text = "OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
                }
            },
        )
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

    // Swift FigmaAuctionProductDetailsViewController.swift:200-247 — one
    // 20dp-margined stack (spacing 16, custom 12 after dividers) on gray100.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md),
    ) {
        Spacer(Modifier.height(16.dp))

        // ─── Hero image card — Swift :268-308: gray150 fill, radius 15,
        //     1dp iconShape border, height 240, photo inset 20. ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(colors.gray150, RoundedCornerShape(15.dp))
                .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp)),
        ) {
            // Swift buildHeroImage (:276-294): gray400 airplane placeholder
            // when the URL is missing or the fetch fails.
            var heroFailed by remember(product.imageUrl) {
                mutableStateOf(product.imageUrl.isNullOrBlank())
            }
            if (heroFailed) {
                Image(
                    painter = painterResource(R.drawable.ic_standard_shipping),
                    contentDescription = product.title,
                    colorFilter = ColorFilter.tint(colors.gray400),
                    modifier = Modifier.align(Alignment.Center).size(96.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentScale = ContentScale.Fit,
                    onError = { heroFailed = true },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // ─── Paging dots — Swift :310-338: 8dp dots, 6dp spacing, centered. ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (index == 1) BrandPalette.OrangeMain else colors.iconShape,
                            CircleShape,
                        )
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // ─── Stats row — Swift :343-388: no strip background, 18dp icons +
        //     body2 labels in textDarkTitle, 6dp icon gap, 24dp pair gap. ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "0 Reviews", style = AirdropType.body2, color = colors.textDarkTitle)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "50 Shares", style = AirdropType.body2, color = colors.textDarkTitle)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        Spacer(Modifier.height(12.dp))

        // ─── Title block — Swift :392-449. ───
        Text(
            text = product.title,
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(AirdropType.body2.toSpanStyle().copy(color = colors.textDescription)) {
                    append("Model: ")
                }
                withStyle(AirdropType.subtitle2.toSpanStyle().copy(color = colors.textDarkTitle)) {
                    append(product.slug?.uppercase() ?: "—")
                }
            },
            style = AirdropType.body2,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val regular = product.regularPriceUsd
                if (regular != null && regular > product.priceUsd && regular > 0) {
                    Text(
                        // Swift :477-481 — strikethrough body2 "$%.2f".
                        text = String.format(Locale.US, "$%.2f", regular),
                        style = AirdropType.body2.copy(textDecoration = TextDecoration.LineThrough),
                        color = colors.textDescription,
                    )
                }
                Text(
                    // Swift :487-494 — h5 orangeMain "$%.2f".
                    text = String.format(Locale.US, "$%.2f", product.priceUsd),
                    style = AirdropType.h5,
                    color = BrandPalette.OrangeMain,
                )
            }
            QuantityStepper(quantity = quantity, onChange = onChangeQuantity)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            // Swift :428-445 — "Stock Quantity: " subtitle2 + value subtitle1,
            // both blueAccentMain.
            text = buildAnnotatedString {
                withStyle(AirdropType.subtitle2.toSpanStyle()) { append("Stock Quantity: ") }
                withStyle(AirdropType.subtitle1.toSpanStyle()) { append("${product.inventory ?: 0}") }
            },
            style = AirdropType.subtitle2,
            color = BrandPalette.BlueAccentMain,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        Spacer(Modifier.height(12.dp))

        // ─── Description — Swift :561-599: subtitle1 title, body2
        //     textDescription body (4 lines collapsed), underlined See all. ───
        Text(text = "Description", style = AirdropType.subtitle1, color = colors.textDarkTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            text = descriptionAnnotated(product.description),
            style = AirdropType.body2,
            color = colors.textDescription,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (expanded) "See less" else "See all",
            style = AirdropType.underlineLink.copy(textDecoration = TextDecoration.Underline),
            color = BrandPalette.OrangeMain,
            modifier = Modifier.clickable(onClick = onToggleExpanded),
        )

        // ─── Related Products (auction mode only) — Swift :237-240, :643-654. ───
        if (!featured && related.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ShopSectionHeader(
                title = "Related Products",
                actionLabel = "View More",
                onAction = onViewMoreRelated,
            )
            Spacer(Modifier.height(16.dp))
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

        // Tail spacer — Swift :242-245.
        Spacer(Modifier.height(80.dp))
    }
}

/**
 * 132x44 stepper card — Swift FigmaAuctionProductDetailsViewController.swift:501-549:
 * gray100 fill, radius 12, 1dp iconShape border, 16dp +/- icons in 36dp
 * touch targets inset 4, subtitle1 quantity, all textDarkTitle.
 */
@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .width(132.dp)
            .height(44.dp)
            .background(colors.gray100, RoundedCornerShape(12.dp))
            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable { onChange(-1) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_shop_minus_square),
                contentDescription = "Decrease quantity",
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = quantity.toString(),
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable { onChange(1) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_shop_add_square),
                contentDescription = "Increase quantity",
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Strip the simple HTML/markdown the API mixes into descriptions (Swift
 * renderProductDescription): <br>/<p> to newlines, drop other tags and
 * `**bold**` markers.
 */
/** Strips HTML but PRESERVES the `**...**` markers for the annotated builder. */
private fun cleanDescription(raw: String?): String {
    if (raw.isNullOrBlank()) return "No description available."
    return raw
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("</p>"), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()
}

/**
 * Swift renderProductDescription (:840-891): `**text**` spans render Cairo-Bold
 * (an explicit user-flagged iOS fix) instead of stripping the markers.
 */
private fun descriptionAnnotated(raw: String?): androidx.compose.ui.text.AnnotatedString {
    val cleaned = cleanDescription(raw)
    return buildAnnotatedString {
        var i = 0
        val re = Regex("\\*\\*(.+?)\\*\\*")
        for (m in re.findAll(cleaned)) {
            if (m.range.first > i) append(cleaned.substring(i, m.range.first))
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            ) { append(m.groupValues[1]) }
            i = m.range.last + 1
        }
        if (i < cleaned.length) append(cleaned.substring(i))
    }
}
