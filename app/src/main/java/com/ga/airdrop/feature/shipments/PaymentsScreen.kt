package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * Payments list — Figma node 40001753:18909, behavior from
 * FigmaPaymentsViewController: paginated /payments, debounced search,
 * All/Package/Product filter, HTML-decoded descriptions, per-row invoice
 * download opening the invoice viewer.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: PaymentsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val invoiceEvent by viewModel.invoiceEvents.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(invoiceEvent) {
        invoiceEvent?.let { event ->
            viewModel.consumeInvoiceEvent()
            onNavigate(Routes.invoiceViewer(event.url, event.title))
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Gate on the data list, not layout rows — empty/error states render
            // rows too, which kept re-firing loadNextPage after a failed first
            // load (Swift FigmaPaymentsViewController requires !payments.isEmpty).
            state.items.isNotEmpty() &&
                info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.items.size, state.hasMorePages) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    fun openPayment(payment: ShipmentPayment) {
        if (payment.paymentType.equals("product", ignoreCase = true)) {
            onNavigate(Routes.productPaymentDetails(payment.id.toString()))
        } else {
            onNavigate(Routes.paymentPackageDetails(payment.id.toString()))
        }
    }

    // Swift attaches a UIRefreshControl (orange) that resets to page 1.
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
                    placeholder = "Search by payment description",
                    iconPlacement = ShipmentsSearchIconPlacement.Trailing,
                    iconSize = 18.dp,
                    horizontalPadding = 14.dp,
                    iconTextGap = 8.dp,
                    testTag = "payments-search-field",
                    iconTestTag = "payments-search-icon",
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            if (state.loading && state.items.isEmpty()) {
                item(key = "loading") { ShipmentsLoadingIndicator() }
            } else if (state.items.isEmpty()) {
                item(key = "empty") { ShipmentsEmptyLabel("No payments found") }
            } else {
                items(state.items, key = { it.id }) { payment ->
                    PaymentCard(
                        payment = payment,
                        onClick = { openPayment(payment) },
                        onDownloadInvoice = { viewModel.downloadInvoice(payment) },
                        downloadingInvoice = state.downloadingInvoiceId == payment.id,
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
            title = "Payments",
            titleStyle = AirdropType.title2,
            onBack = onBack,
            rightIconRes = R.drawable.ic_shipments_more_square,
            onRightClick = { viewModel.showTypeFilter(true) },
            rightIconContentDescription = "Filter payments",
            rightIconTestTag = "payments-header-more",
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (state.showTypeFilter) {
            PaymentTypeFilterDialog(
                selected = state.typeFilter,
                onSelect = viewModel::selectTypeFilter,
                onDismiss = { viewModel.showTypeFilter(false) },
            )
        }
        state.error?.let { message ->
            ShipmentsAlertDialog(
                title = "Download failed",
                message = message,
                confirmText = "OK",
                onConfirm = viewModel::consumeError,
                onDismiss = viewModel::consumeError,
            )
        }
    }
}

/** Swift UIAlertController action sheet — All / Package / Product. */
@Composable
private fun PaymentTypeFilterDialog(
    selected: PaymentTypeFilter,
    onSelect: (PaymentTypeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.30f))
                .testTag("payments-type-filter-root"),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("payments-type-filter-sheet"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.s))
                        .background(colors.gray100)
                        .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
                ) {
                    Text(
                        text = "Filter Payments",
                        style = AirdropType.title2,
                        color = colors.textDarkTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, start = Spacing.md, end = Spacing.md),
                    )
                    Text(
                        text = "Show payments for",
                        style = AirdropType.body2,
                        color = colors.textDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.md, end = Spacing.md, bottom = 8.dp),
                    )
                    PaymentTypeFilter.entries.forEachIndexed { index, filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clickable { onSelect(filter) }
                                .padding(horizontal = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = filter.label,
                                style = AirdropType.subtitle1,
                                color = if (filter == selected) BrandPalette.OrangeMain else colors.textDarkTitle,
                                modifier = Modifier.weight(1f),
                            )
                            if (filter == selected) {
                                Image(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (index != PaymentTypeFilter.entries.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.divider)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(Radius.s))
                        .background(colors.gray100)
                        .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                        .clickable(onClick = onDismiss)
                        .testTag("payments-type-filter-cancel"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        style = AirdropType.subtitle1,
                        color = BrandPalette.OrangeMain,
                    )
                }
            }
        }
    }
}
