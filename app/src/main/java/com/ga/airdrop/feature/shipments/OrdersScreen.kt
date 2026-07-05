package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * Orders list — Figma node 40001753:19595, behavior from
 * FigmaOrdersViewController: paginated /orders, debounced search, image cards.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: OrdersViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val refreshing = state.loading && state.items.isNotEmpty()
    Box(Modifier.fillMaxSize().background(colors.gray100)) {
      androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = ptrState,
                isRefreshing = refreshing,
                color = BrandPalette.OrangeMain,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = shipmentsHeaderClearance()),
            )
        },
      ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = shipmentsHeaderClearance() + Spacing.md,
                bottom = Spacing.xl,
            ),
        ) {
            item(key = "search") {
                ShipmentsSearchField(
                    value = state.searchText,
                    onValueChange = viewModel::onSearchTextChange,
                    placeholder = "Search by Order Description",
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            if (state.loading && state.items.isEmpty()) {
                item(key = "loading") { ShipmentsLoadingIndicator() }
            } else if (state.items.isEmpty()) {
                item(key = "empty") { ShipmentsEmptyLabel("No orders found") }
            } else {
                items(state.items, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        onClick = { onNavigate(Routes.orderDetails(order.id.toString())) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.sm),
                    )
                }
                if (state.loadingMore) {
                    item(key = "loadingMore") { ShipmentsLoadingIndicator() }
                }
            }
        }
      }

        ShipmentsDetailHeader(
            title = "Orders",
            titleStyle = AirdropType.title2,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
