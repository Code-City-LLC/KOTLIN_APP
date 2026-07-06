package com.ga.airdrop.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore

/**
 * Auction full list — Figma "Auction" 40001846:54117, behavior from
 * FigmaAuctionViewController (pagination + search + filters).
 */
@Composable
fun AuctionScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AuctionViewModel = viewModel(),
) {
    ProductListScreen(
        title = "Auction",
        searchPlaceholder = "Search Auction Items",
        emptyText = "No auction items right now",
        featured = false,
        viewModel = viewModel,
        onNavigate = onNavigate,
        onBack = onBack,
    )
}

/**
 * Feature Products full list — Figma 40001846:54396 (+ sort sheet
 * 40001846:54756), behavior from FigmaFeatureProductsViewController
 * (GET /featured-products paginated).
 */
@Composable
fun FeaturedProductsScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: FeaturedProductsViewModel = viewModel(),
) {
    ProductListScreen(
        title = "Feature Products",
        searchPlaceholder = "Paste Any Amazon Product Link",
        emptyText = "No Products Found",
        featured = true,
        viewModel = viewModel,
        onNavigate = onNavigate,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductListScreen(
    title: String,
    searchPlaceholder: String,
    emptyText: String,
    featured: Boolean,
    viewModel: ProductListViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val cartLines by CartStore.items.collectAsState()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) { CartStore.init(context) }

    // Infinite scroll — Swift triggers 4 items from the end
    // (RN onEndReachedThreshold={3}).
    LaunchedEffect(gridState, state.products.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (state.products.isNotEmpty() && lastVisible >= state.products.size - 4) {
                    viewModel.loadNextPage()
                }
            }
    }

    Column(Modifier.fillMaxSize().background(colors.gray150)) {
        ShopInnerHeader(
            title = title,
            onBack = onBack,
            trailing = {
                ShopHeaderCartIcon(count = cartLines.size, onClick = { onNavigate(Routes.CART) })
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm1)
        ) {
            ShopSearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = searchPlaceholder,
                onFilterClick = { viewModel.setSortSheetVisible(true) },
            )
        }

        val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.loadFirstPage(refreshing = true) },
            state = ptrState,
            modifier = Modifier.weight(1f),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = ptrState,
                    isRefreshing = state.refreshing,
                    color = BrandPalette.OrangeMain,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            if (!state.loading && state.products.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.md), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyText,
                        style = AirdropType.body1,
                        color = colors.textDescription,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("product-list-grid"),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.sm,
                        bottom = ProductListBottomClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.loading && state.products.isEmpty()) {
                        // Initial skeleton grid (Swift seedGrid parity).
                        itemsIndexed(List(6) { it }, key = { _, i -> "skeleton-$i" }) { _, _ ->
                            ShopSkeletonCard()
                        }
                    }
                    itemsIndexed(state.products, key = { _, p -> p.id }) { _, product ->
                        ShopProductCard(
                            product = product,
                            inCart = cartLines.any { it.id == product.id },
                            onClick = {
                                onNavigate(Routes.auctionProductDetails(product.routeSlug, featured))
                            },
                            onToggleCart = { CartStore.toggle(product.toCartLine()) },
                            modifier = Modifier.testTag("product-list-card-${product.id}"),
                        )
                    }
                    if (state.loading && state.products.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = BrandPalette.OrangeMain,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.height(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showSortSheet) {
        ShopSortSheet(
            selected = state.sort,
            onSelect = viewModel::applySort,
            onDismiss = { viewModel.setSortSheetVisible(false) },
        )
    }
}

private val ProductListBottomClearance = 124.dp
