package com.ga.airdrop.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.components.AirdropHeaderStyle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.feature.cart.CartStore

/**
 * Shop tab root — Figma "Shop" 40001846:53519.
 * Solid AirdropHeader, search (500 ms debounce), "Auction" 2-column grid
 * (shortlist of 4), "Feature Products" horizontal 160dp card row, 90dp+
 * clearance for the glass bottom bar. Behavior: FigmaShopViewController.
 */
@Composable
fun ShopScreen(
    onNavigate: (String) -> Unit,
    viewModel: ShopViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val headerInfo by SessionStore.header.collectAsState()
    val cartLines by CartStore.items.collectAsState()
    val cartKeys = cartLines.map(CartStore.CartLine::key).toSet()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { CartStore.init(context) }

    fun openDetails(product: ShopProduct, featured: Boolean) {
        // Swift passes the object into the details VC; featured products have
        // no show endpoint, so the hand-off is what makes their detail load
        // (VERIFICATION_LEDGER P1).
        ShopProductHandoffStore.put(product)
        onNavigate(Routes.auctionProductDetails(product.routeSlug, featured))
    }

    // Swift FigmaShopViewController.swift:42 — gray200 background.
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .testTag("shop-root")
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(126.dp)) // Swift FigmaShopViewController.swift:109 — 126 top clearance
            Column(
                Modifier
                    .fillMaxWidth()
                    // Swift: 20pt SIDE insets only — the 126 top is the spacer
                    // above; adding vertical padding double-counted the top.
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ShopSearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Search",
                    onFilterClick = { viewModel.setSortSheetVisible(true) },
                    modifier = Modifier
                        .testTag("shop-root-search")
                        .onFocusEvent { searchFocused = it.hasFocus },
                )

                // Swift §C.7 recents accessory: while the search field is
                // focused, offer the last submitted queries as tappable chips.
                if (searchFocused && state.recentSearches.isNotEmpty()) {
                    RecentSearchChips(
                        recents = state.recentSearches,
                        onSelect = { query ->
                            focusManager.clearFocus()
                            viewModel.onRecentSearchSelected(query)
                        },
                    )
                }

                // ─── Auction section (2-column grid of 4) ───
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ShopSectionHeader(
                        title = "Sale",
                        actionLabel = "View More",
                        onAction = { onNavigate(Routes.AUCTION) },
                    )
                    if (state.auctionLoading && state.auction.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            repeat(2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    ShopSkeletonCard(Modifier.weight(1f))
                                    ShopSkeletonCard(Modifier.weight(1f))
                                }
                            }
                        }
                    } else if (state.auction.isEmpty()) {
                        ShopEmptyCard(text = "No sale products")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            state.auction.chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    rowItems.forEach { product ->
                                        ShopProductCard(
                                            product = product,
                                            inCart = CartStore.CartLineKey(
                                                CartStore.CartLineKind.AUCTION,
                                                product.id,
                                            ) in cartKeys,
                                            onClick = { openDetails(product, featured = false) },
                                            onToggleCart = { CartStore.toggle(product.toCartLine()) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("shop-root-auction-card-${product.id}"),
                                            // Swift Shop root: 1-line title, 15/10 insets.
                                            titleLines = 1,
                                            rootInsets = true,
                                        )
                                    }
                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // ─── Feature Products section (horizontal 160dp cards) ───
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ShopSectionHeader(
                        title = "Feature Products",
                        actionLabel = "View More",
                        onAction = { onNavigate(Routes.FEATURED_PRODUCTS) },
                    )
                    if (state.featuredLoading && state.featured.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            repeat(3) { ShopSkeletonCard(Modifier.width(160.dp)) }
                        }
                    } else if (state.featured.isEmpty()) {
                        // Swift: fixed 240pt-wide empty card inside the row.
                        ShopEmptyCard(text = "No featured products", modifier = Modifier.width(240.dp))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(state.featured, key = { it.id }) { product ->
                                ShopProductCard(
                                    product = product,
                                    inCart = CartStore.CartLineKey(
                                        CartStore.CartLineKind.AUCTION,
                                        product.id,
                                    ) in cartKeys,
                                    onClick = { openDetails(product, featured = true) },
                                    // Swift: no cart toggle on Shop-root featured cards.
                                    onToggleCart = null,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .testTag("shop-root-featured-card-${product.id}"),
                                    // Swift Shop root: 1-line title, 15/10 insets.
                                    titleLines = 1,
                                    rootInsets = true,
                                )
                            }
                        }
                    }
                }

                // Clearance for the glass bottom bar (Swift tail = 120).
                Spacer(Modifier.height(120.dp))
            }
        }

        AirdropHeader(
            greeting = listOf(headerInfo.greeting, headerInfo.firstName)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            tierName = headerInfo.tierName,
            style = AirdropHeaderStyle.Solid,
            cartCount = headerInfo.cartCount,
            airCoins = headerInfo.airCoins,
            onTierClick = { onNavigate(Routes.GOLD_PRIORITY) },
            onBellClick = { onNavigate(Routes.NOTIFICATIONS) },
            onCartClick = { onNavigate(Routes.CART) },
            onAirCoinsClick = { onNavigate(Routes.AIRCOIN_HISTORY) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("shop-root-header"),
        )
    }

    if (state.showSortSheet) {
        ShopSortSheet(
            selected = state.sort,
            onSelect = viewModel::applySort,
            onDismiss = { viewModel.setSortSheetVisible(false) },
        )
    }
}

@Composable
internal fun ShopEmptyCard(text: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.sm1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AirdropType.body1,
            color = colors.textDescription,
        )
    }
}

/**
 * Swift §C.7 recents accessory ported to Compose: a horizontally-scrolling
 * "Recent:" strip of the last submitted queries — Body3 chips on gray150,
 * radius 14, 12h/6v padding. Tapping one re-runs that search.
 */
@Composable
private fun RecentSearchChips(recents: List<String>, onSelect: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("shop-recent-searches"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Recent:", style = AirdropType.body3, color = colors.textDescription)
        recents.forEach { query ->
            Text(
                text = query,
                style = AirdropType.body3,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.gray150)
                    .clickable { onSelect(query) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("shop-recent-chip-$query"),
            )
        }
    }
}
