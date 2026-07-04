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
import androidx.compose.ui.res.painterResource
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
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AuctionProduct
import java.text.NumberFormat
import java.util.Locale

/**
 * Home tab — Figma "Home - Light Mode" 40000710:5347 / dark 40000710:5667.
 * Hero image (534dp, gradient fade into bg), warehouse card carousel,
 * 2x2 activity grid, Auction Highlights carousel, Refer-a-friend row.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
)
{
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero: image 534dp tall with dark gradient at bottom fading to bg.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(534.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.img_home_hero),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.79f to Color.Transparent,
                                1f to Color(0xFF343538),
                            )
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                0.21f to Color.Transparent,
                                0.85f to colors.gray200,
                            )
                        )
                )
                // Warehouse cards overlap the hero (start at y=326 of 534).
                WarehouseCarousel(
                    onReadMore = { onNavigate(Routes.WAREHOUSES) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 0.dp),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ActivityGrid(onNavigate)
                AuctionHighlights(
                    products = state.auctionHighlights,
                    onSeeMore = { onNavigate(Routes.SHOP) },
                    onProduct = { onNavigate(Routes.auctionProductDetails(it)) },
                )
                ReferAFriendCard(onClick = { onNavigate(Routes.REFER_A_FRIEND) })
                // Clearance for the glass bottom bar.
                Spacer(Modifier.height(90.dp))
            }
        }

        AirdropHeader(
            greeting = listOf(state.greeting, state.firstName)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            tierName = state.tierName.ifBlank { " " },
            cartCount = state.cartCount,
            airCoins = state.airCoins,
            onTierClick = { onNavigate(Routes.GOLD_PRIORITY) },
            onBellClick = { onNavigate(Routes.NOTIFICATIONS) },
            onCartClick = { onNavigate(Routes.CART) },
            onAirCoinsClick = { onNavigate(Routes.AIRCOIN_HISTORY) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// ─── Warehouse cards — Figma Component 36/37/38 ───────────────────────────

private data class WarehouseCard(
    val title: String,
    val subtitle: String,
    val description: String,
    val imageRes: Int,
)

private val warehouseCards = listOf(
    WarehouseCard(
        title = "AirDrop Standard",
        subtitle = "Air Freight",
        description = "2 to 3 business days after items are delivered to our warehouse",
        imageRes = R.drawable.img_warehouse_standard,
    ),
    WarehouseCard(
        title = "SeaDrop",
        subtitle = "Sea Freight",
        description = "2 to 4 weeks after items are\ndelivered to our warehouse",
        imageRes = R.drawable.img_warehouse_seadrop,
    ),
    WarehouseCard(
        title = "Express",
        subtitle = "Air Freight",
        description = "1 to 2 business days after items are delivered to our warehouse",
        imageRes = R.drawable.img_warehouse_express,
    ),
)

@Composable
private fun WarehouseCarousel(onReadMore: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(warehouseCards) { card ->
            Column(
                modifier = Modifier
                    .width(238.dp)
                    .background(colors.gray150, RoundedCornerShape(Radius.s))
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                    .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        painter = painterResource(card.imageRes),
                        contentDescription = card.title,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = card.title,
                                style = AirdropType.h5,
                                color = colors.textDarkTitle,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                text = card.subtitle,
                                style = AirdropType.subtitle1,
                                color = colors.textDarkTitle,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        Text(
                            text = card.description,
                            style = AirdropType.body2,
                            color = colors.textDarkTitle,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                Text(
                    text = "Read More",
                    style = AirdropType.underlineLink.copy(textDecoration = TextDecoration.Underline),
                    color = BrandPalette.OrangeMain,
                    modifier = Modifier.clickable(onClick = onReadMore),
                )
            }
        }
    }
}

// ─── Activity grid — Figma Card Page 162.5dp ──────────────────────────────

private data class Activity(val label: String, val iconRes: Int, val route: String)

@Composable
private fun ActivityGrid(onNavigate: (String) -> Unit) {
    val activities = listOf(
        Activity("Services", R.drawable.ic_services, Routes.SERVICES),
        Activity("Ship Tax", R.drawable.ic_ship_tax, Routes.SALES_TAXES),
        Activity("Calculator", R.drawable.ic_calculator, Routes.CALCULATOR),
        Activity("Drop Alert", R.drawable.ic_drop_alert, Routes.DROP_ALERT),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(activity.iconRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = activity.label,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
    }
}

// ─── Auction Highlights — Figma Item Static 160dp ─────────────────────────

@Composable
private fun AuctionHighlights(
    products: List<AuctionProduct>,
    onSeeMore: () -> Unit,
    onProduct: (String) -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Auction Highlights",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
            )
            Text(
                text = "See More",
                style = AirdropType.underlineLink.copy(textDecoration = TextDecoration.Underline),
                color = BrandPalette.OrangeMain,
                modifier = Modifier.clickable(onClick = onSeeMore),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(products, key = { it.id ?: it.hashCode() }) { product ->
                ProductHighlightCard(product = product, onClick = { onProduct(product.slug.orEmpty()) })
            }
        }
    }
}

@Composable
fun ProductHighlightCard(product: AuctionProduct, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp)
                .clip(RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .background(colors.gray150)
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = product.displayImageUrl,
                contentDescription = product.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm1, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = product.displayTitle,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = NumberFormat.getCurrencyInstance(Locale.US)
                        .format(product.displayPriceUsd),
                    style = AirdropType.title2,
                    color = BrandPalette.OrangeMain,
                )
                Image(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Add to cart",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ─── Refer a friend — Figma Card Page 59dp row ────────────────────────────

@Composable
private fun ReferAFriendCard(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(59.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_refer),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Refer a friend",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_small_arrow_down),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(24.dp)
                .rotate(-90f),
        )
    }
}
