package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * Packages list — Figma node 40001666:42198, behavior from
 * FigmaPackagesViewController: paginated /packages with search, filter sheet
 * (status + shipment method), status-colored rows, cart toggles.
 */
@Composable
fun PackagesScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: PackagesViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    // Shared cart membership — Swift FigmaCartStore; drives the +/check icons.
    val cartLines by com.ga.airdrop.feature.cart.CartStore.items.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { com.ga.airdrop.feature.cart.CartStore.init(context) }
    val listState = rememberLazyListState()

    // Infinite scroll: request the next page when the tail comes into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Gate on the data list, not layout rows — empty/error states render
            // rows too, which kept re-firing loadNextPage after a failed first
            // load (Swift FigmaPackagesViewController requires !allPackages.isEmpty).
            state.items.isNotEmpty() &&
                info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
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
                    onSubmit = viewModel::onSearchSubmit,
                    // Swift FigmaPackagesViewController:287-292.
                    placeholder = "Search by Airdrop Tracking # or Courier #",
                    testTag = "packages-search-field",
                    iconTestTag = "packages-search-icon",
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            if (state.loading && state.items.isEmpty()) {
                item(key = "loading") { ShipmentsLoadingIndicator() }
            } else if (state.visibleItems.isEmpty()) {
                item(key = "empty") { ShipmentsEmptyLabel("No packages found") }
            } else {
                items(state.visibleItems, key = { it.id }) { pkg ->
                    PackageCard(
                        pkg = pkg,
                        exchangeRate = state.exchangeRate,
                        onClick = { onNavigate(Routes.packageDetails(pkg.id.toString())) },
                        onToggleCart = { viewModel.toggleCart(pkg) },
                        inCart = cartLines.any { it.id == pkg.id },
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

        ShipmentsDetailHeader(
            title = "Packages",
            onBack = onBack,
            rightIconRes = R.drawable.ic_shipments_more_square,
            onRightClick = viewModel::openFilterSheet,
            rightIconContentDescription = "Filter",
            rightIconTestTag = "packages-filter-button",
            // Swift §B.4: sort trigger sits left of the filter square.
            secondaryRightIconRes = R.drawable.ic_sort_arrows,
            onSecondaryRightClick = viewModel::openSortSheet,
            secondaryRightIconContentDescription = "Sort packages",
            secondaryRightIconTestTag = "packages-sort-button",
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (state.showFilterSheet) {
            PackagesFilterSheet(
                statuses = state.statuses,
                selectedStatus = state.statusFilter,
                selectedMethod = state.methodFilter,
                onSelectStatus = viewModel::selectStatus,
                onSelectMethod = viewModel::selectMethod,
                onClose = viewModel::closeFilterSheet,
            )
        }

        if (state.showSortSheet) {
            PackagesSortSheet(
                selected = state.sort,
                onSelect = viewModel::applySort,
                onDismiss = viewModel::closeSortSheet,
            )
        }
    }
}

/**
 * "Sort by" picker — Swift openSortMenu presents FigmaDropdownPicker with the
 * four §B.4 options; the active one renders orange.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackagesSortSheet(
    selected: PackagesSort,
    onSelect: (PackagesSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.gray150,
        shape = RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm)
                    .size(width = 100.dp, height = 6.dp)
                    .background(colors.gray300, RoundedCornerShape(Radius.full))
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Sort by",
                style = AirdropType.title1,
                color = colors.textDarkTitle,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            PackagesSort.entries.forEach { option ->
                val isSelected = option == selected
                Text(
                    text = option.title,
                    style = if (isSelected) AirdropType.title2 else AirdropType.subtitle1,
                    color = if (isSelected) BrandPalette.OrangeMain else colors.textDarkTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = Spacing.sm),
                )
            }
        }
    }
}
