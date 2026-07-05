package com.ga.airdrop.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.feature.cart.CartStore
import java.text.NumberFormat
import java.util.Locale

/**
 * Home tab — Swift FigmaHomeViewController (Figma 40001464:28899).
 *
 * Geometry (FigmaHomeViewController.swift:220-276):
 *  - hero photo layer fixed at 375x534, top of the scroll content, under a
 *    flat black-10% scrim (:193-197) plus a bottom fade that dissolves the
 *    photo into the page background before the cards (Figma 40001464:28899);
 *  - content stack starts at y=326, so the warehouse cards overlap the
 *    photo's lower half and spill below its bottom edge;
 *  - stack spacing 8; custom 20 after the activities grid and after the
 *    auction highlights; 120pt tail clears the tab bar.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
)
{
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val header by SessionStore.header.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { CartStore.init(context) }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box {
                // Hero photo — 534dp, aspect-fill, honours the user-selected
                // background; overlaid by the two Figma gradients below.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(534.dp)
                ) {
                    Image(
                        painter = painterResource(
                            com.ga.airdrop.feature.more.BackgroundStore.currentBackgroundRes(
                                context,
                                colors.isDark,
                            )
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    // Hero overlays — EXACT Figma spec (Home 40001464:28899):
                    // (A) node 40001464:28902 — full-height gradient darkening
                    //     the lower photo: transparent @79.2% → #343538 @100%.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.792f to Color.Transparent,
                                    1f to Color(0xFF343538),
                                )
                            )
                    )
                    // (B) node 40001464:28903 — 110dp band at y=424 that fades
                    //     the photo into the page background: white-5% @21% →
                    //     gray200 (#f5f5f5 light / dark page bg) @85.2%. gray200
                    //     is theme-aware so this is correct in dark mode too.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    0.21f to Color.White.copy(alpha = 0.05f),
                                    0.852f to colors.gray200,
                                )
                            )
                    )
                }

                // Content overlaps the photo from y=326 (Swift :245).
                Column(Modifier.padding(top = 326.dp)) {
                    WarehouseCarousel(
                        onOpen = { type -> onNavigate("${Routes.WAREHOUSES}?type=$type") },
                    )
                    Spacer(Modifier.height(8.dp)) // contentStack spacing
                    ActivityGrid(onNavigate)
                    Spacer(Modifier.height(Spacing.md)) // custom 20 after grid
                    AuctionHighlights(
                        products = state.auctionHighlights,
                        loading = state.loading,
                        // Swift :843 — See More opens the Auction list.
                        onSeeMore = { onNavigate(Routes.AUCTION) },
                        onProduct = { onNavigate(Routes.auctionProductDetails(it)) },
                    )
                    Spacer(Modifier.height(Spacing.md)) // custom 20 after auction
                    ReferAFriendCard(onClick = { onNavigate(Routes.REFER_A_FRIEND) })
                    // Tail spacer clears the tab bar (Swift :274).
                    Spacer(Modifier.height(120.dp))
                }
            }
        }

        AirdropHeader(
            greeting = listOf(state.greeting, state.firstName)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            tierName = state.tierName.ifBlank { " " },
            cartCount = header.cartCount,
            airCoins = state.airCoins,
            onTierClick = { onNavigate(Routes.GOLD_PRIORITY) },
            onBellClick = { onNavigate(Routes.NOTIFICATIONS) },
            onCartClick = { onNavigate(Routes.CART) },
            onAirCoinsClick = { onNavigate(Routes.AIRCOIN_HISTORY) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// ─── Warehouse cards — Swift makeWarehouseCard (Figma 40000770:6487) ──────

private data class WarehouseCard(
    val title: String,
    val subtitle: String,
    val description: String,
    val imageRes: Int,
    val type: String,
)

private val warehouseCards = listOf(
    // Copy matches Swift FigmaHomeViewController.swift:296-303 exactly
    // (titles without the "AirDrop" prefix; SeaDrop/Express bodies keep
    // their trailing periods, Standard has none).
    WarehouseCard(
        title = "Standard",
        subtitle = "Air Freight",
        description = "2 to 3 business days after items are delivered to our warehouse",
        imageRes = R.drawable.img_warehouse_standard,
        type = "standard",
    ),
    WarehouseCard(
        title = "SeaDrop",
        subtitle = "Sea Freight",
        description = "2 to 4 weeks after items are delivered to our warehouse.",
        imageRes = R.drawable.img_warehouse_seadrop,
        type = "seadrop",
    ),
    WarehouseCard(
        title = "Express",
        subtitle = "Air Freight",
        description = "1 to 2 business days after items are delivered to our warehouse.",
        imageRes = R.drawable.img_warehouse_express,
        type = "express",
    ),
)

@Composable
private fun WarehouseCarousel(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    LazyRow(
        // Swift: row top = scroll top + 20, leading/trailing 20, gap 10.
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .testTag("home-warehouse-carousel"),
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(warehouseCards) { card ->
            Column(
                modifier = Modifier
                    // Swift: fixed 238x326 (Figma Components 36/37/38).
                    .width(238.dp)
                    .height(326.dp)
                    .testTag("home-warehouse-${card.type}")
                    .clip(RoundedCornerShape(Spacing.sm1)) // radius 15 (Figma 2xs)
                    .background(colors.gray150)
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Spacing.sm1))
                    // Swift: the WHOLE card is a tap target → WarehouseView.
                    .clickable { onOpen(card.type) }
                    // Swift: px 20, top pinned 30, bottom ≤ (stack bottom is a
                    // lessThanOrEqualTo constraint — no bottom padding here so
                    // Cairo's taller-than-nominal metrics can't clip Read More).
                    .padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Swift stack spacing 10 between text rows.
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(card.imageRes),
                        contentDescription = card.title,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.height(Spacing.md - Spacing.sm)) // icon→title 30 total
                Text(
                    text = card.title,
                    style = AirdropType.h5,
                    color = colors.textDarkTitle,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = card.subtitle,
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = card.description,
                    style = AirdropType.body2,
                    // Swift: body uses textDescription, not the title color.
                    color = colors.textDescription,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Read More",
                    // Swift: Body2 underlined in orangeDark.
                    style = AirdropType.body2.copy(textDecoration = TextDecoration.Underline),
                    color = BrandPalette.OrangeDark,
                )
            }
        }
    }
}

// ─── Activity grid — Swift makeActivitiesGrid (Figma 40000770:6493) ───────

private data class Activity(
    val label: String,
    val lightIconRes: Int,
    val darkIconRes: Int,
    val route: String,
)

@Composable
private fun ActivityGrid(onNavigate: (String) -> Unit) {
    val activities = listOf(
        Activity("Services", R.drawable.ic_services, R.drawable.ic_services_dark, Routes.SERVICES),
        Activity("Ship Tax", R.drawable.ic_ship_tax, R.drawable.ic_ship_tax_dark, Routes.SALES_TAXES),
        Activity("Calculator", R.drawable.ic_calculator, R.drawable.ic_calculator_dark, Routes.CALCULATOR),
        Activity("Drop Alert", R.drawable.ic_drop_alert, R.drawable.ic_drop_alert_dark, Routes.DROP_ALERT),
    )
    Column(
        // Swift wrap: grid inset top 20, horizontal 20; rows gap 10.
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        activities.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { activity ->
                    ActivityCard(
                        activity = activity,
                        onClick = { onNavigate(activity.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: Activity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            // Swift: fixed 108pt tile (py20 + icon 32 + gap 10 + label 26).
            .height(108.dp)
            .clip(RoundedCornerShape(Spacing.sm1)) // radius 15
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Spacing.sm1))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val iconRes = if (colors.isDark) activity.darkIconRes else activity.lightIconRes
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = activity.label,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
    }
}

// ─── Auction Highlights — Swift makeAuctionHighlights ─────────────────────

@Composable
private fun AuctionHighlights(
    products: List<AuctionProduct>,
    loading: Boolean,
    onSeeMore: () -> Unit,
    onProduct: (String) -> Unit,
) {
    val colors = AirdropTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Swift: title row 335x26 inset 20; cards row 10 below.
                .height(26.dp)
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // Swift :527 — Title1.
                text = "Auction Highlights",
                style = AirdropType.title1,
                color = colors.textDarkTitle,
            )
            Text(
                // Swift :529-534 — underlined Body2 in orangeDark.
                text = "See More",
                style = AirdropType.body2.copy(textDecoration = TextDecoration.Underline),
                color = BrandPalette.OrangeDark,
                modifier = Modifier.clickable(onClick = onSeeMore),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when {
                products.isNotEmpty() -> {
                    // Swift renders at most 4 live cards (:138).
                    items(products.take(4), key = { it.id ?: it.hashCode() }) { product ->
                        ProductHighlightCard(
                            product = product,
                            onClick = { onProduct(product.slug.orEmpty()) },
                        )
                    }
                }
                loading -> items(listOf(0, 1, 2)) { AuctionSkeletonCard() }
                else -> items(listOf(0)) { EmptyAuctionCard() }
            }
        }
    }
}

private fun AuctionProduct.toCartLine(): CartStore.CartLine = CartStore.CartLine(
    id = id ?: 0,
    packageId = checkoutPackageId,
    imageUrl = displayImageUrl,
    title = displayTitle,
    priceUsd = displayPriceUsd,
)

@Composable
fun ProductHighlightCard(product: AuctionProduct, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    val cartItems by CartStore.items.collectAsState()
    val inCart = product.id != null && cartItems.any { it.id == product.id }
    // Swift makeAuctionCard: fixed 160x245, radius 14, padding 8, spacing 6;
    // photo 124 aspect-fill on gray200; title Body3 single line; price
    // Title2 buttonStatic (textDescription when unavailable); 34pt plus
    // button pinned bottom-trailing (-10, -16) toggling the cart.
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(245.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.gray200),
            ) {
                AsyncImage(
                    model = product.displayImageUrl,
                    contentDescription = product.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = product.displayTitle,
                style = AirdropType.body3,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.US)
                    .format(product.displayPriceUsd),
                style = AirdropType.title2,
                color = if (product.isAvailable) BrandPalette.ButtonStatic else colors.textDescription,
            )
        }
        // Cart toggle — Swift onTapAddToCart / updateAuctionCartButton.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 16.dp)
                .size(34.dp)
                .clickable(enabled = product.id != null) {
                    CartStore.toggle(product.toCartLine())
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(if (inCart) R.drawable.ic_check else R.drawable.ic_add),
                contentDescription = if (inCart) "Remove from cart" else "Add to cart",
                colorFilter = ColorFilter.tint(
                    if (inCart) BrandPalette.OrangeMain else colors.textDarkTitle
                ),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Swift makeAuctionSkeletonCard — gray blocks while products load. */
@Composable
private fun AuctionSkeletonCard() {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .width(160.dp)
            .height(245.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.gray200)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(end = 20.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.gray200)
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .width(74.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.gray200)
        )
    }
}

/** Swift makeEmptyAuctionCard — shown when the API returns no products. */
@Composable
private fun EmptyAuctionCard() {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(245.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No auction highlights",
            style = AirdropType.body1,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Refer a friend — Swift makeReferAFriend (Figma 40000770:6511) ────────

@Composable
private fun ReferAFriendCard(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Swift: 335x59 card inset 20, padding px 20 / py 10.
            .padding(horizontal = Spacing.md)
            .height(59.dp)
            .clip(RoundedCornerShape(Spacing.sm1)) // radius 15 (Figma 2xs)
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Spacing.sm1))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            // Swift row spacing 12.
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Figma Home card 40001464:28925 uses the actual duotone "Refer a
            // friend" asset (40000710:13310): two people in the theme icon
            // color + brand-orange (#F15114) motion accents. Layered as two
            // drawables — people tinted at the Compose level (theme-correct in
            // light AND dark; a @color/icon_duotone stroke would not re-resolve
            // to the in-app ThemeController mode), orange accents baked on top.
            Box(Modifier.size(24.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_refer_a_friend),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(R.drawable.ic_refer_a_friend_accent),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = "Refer a friend",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_small_arrow_down),
            contentDescription = null,
            // Swift: chevron tinted textDarkTitle, rotated -90°.
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier
                .size(24.dp)
                .rotate(-90f),
        )
    }
}
