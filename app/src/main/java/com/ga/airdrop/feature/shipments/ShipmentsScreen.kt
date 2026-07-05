package com.ga.airdrop.feature.shipments

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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.components.AirdropHeaderStyle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.SessionStore

/**
 * Shipments tab root — Figma node 40000823:9633, behavior from
 * FigmaShipmentsViewController: 2x2 summary tiles (/shipments/summary),
 * recent packages strip, payments preview, orders preview; each section
 * links into its full list.
 */
@Composable
fun ShipmentsScreen(
    onNavigate: (String) -> Unit,
    viewModel: ShipmentsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val headerInfo by SessionStore.header.collectAsState()
    // Shared cart membership — Swift FigmaCartStore; drives the +/check icons.
    val cartLines by com.ga.airdrop.feature.cart.CartStore.items.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.ga.airdrop.feature.cart.CartStore.init(context)
    }

    fun openPayment(payment: ShipmentPayment) {
        if (payment.paymentType.equals("product", ignoreCase = true)) {
            onNavigate(Routes.productPaymentDetails(payment.id.toString()))
        } else {
            onNavigate(Routes.paymentPackageDetails(payment.id.toString()))
        }
    }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Swift: first content at y=126 (header 106 + 20).
            Spacer(Modifier.height(126.dp))
            Column(
                Modifier.fillMaxWidth(),
                // Swift contentStack spacing 24 between sections.
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SummarySection(state = state, onNavigate = onNavigate)

                // Packages preview — horizontal cards scroll edge-to-edge
                // with 20 content padding (Swift scroll full-bleed).
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(horizontal = Spacing.md)) {
                        SectionHeaderRow(
                            title = "Packages",
                            actionText = "View More",
                            onAction = { onNavigate(Routes.PACKAGES) },
                            actionTestTag = "shipments-packages-view-more",
                        )
                    }
                    if (state.packages.isEmpty()) {
                        if (state.loading) ShipmentsLoadingIndicator()
                        else ShipmentsEmptyLabel("No packages found")
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.md),
                        ) {
                            items(state.packages, key = { it.id }) { pkg ->
                                PackageCard(
                                    pkg = pkg,
                                    exchangeRate = state.exchangeRate,
                                    onClick = { onNavigate(Routes.packageDetails(pkg.id.toString())) },
                                    onToggleCart = { viewModel.toggleCart(pkg) },
                                    inCart = cartLines.any { it.id == pkg.id },
                                    testTag = "shipments-package-card-${pkg.id}",
                                    cartToggleTestTag = "shipments-package-cart-toggle-${pkg.id}",
                                    // Swift: 280-wide fixed cards.
                                    modifier = Modifier.width(280.dp),
                                )
                            }
                        }
                    }
                }

                // Payments preview — Swift stack spacing 12, inset 20.
                Column(
                    Modifier.padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeaderRow(
                        title = "Payments",
                        actionText = "View More",
                        onAction = { onNavigate(Routes.PAYMENTS) },
                        actionTestTag = "shipments-payments-view-more",
                    )
                    if (state.payments.isEmpty()) {
                        if (state.loading) ShipmentsLoadingIndicator()
                        else ShipmentsEmptyLabel("No payments found")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.payments.forEach { payment ->
                                PaymentCard(
                                    payment = payment,
                                    onClick = { openPayment(payment) },
                                    testTag = "shipments-payment-card-${payment.id}",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // Orders preview — horizontal cards, full-bleed scroll.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(horizontal = Spacing.md)) {
                        SectionHeaderRow(
                            title = "Orders",
                            actionText = "View More",
                            onAction = { onNavigate(Routes.ORDERS) },
                            actionTestTag = "shipments-orders-view-more",
                        )
                    }
                    if (state.orders.isEmpty()) {
                        if (state.loading) ShipmentsLoadingIndicator()
                        else Box(Modifier.padding(horizontal = Spacing.md)) { NoOrdersCard() }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.md),
                        ) {
                            items(state.orders, key = { it.id }) { order ->
                                OrderCard(
                                    order = order,
                                    onClick = { onNavigate(Routes.orderDetails(order.id.toString())) },
                                    testTag = "shipments-order-card-${order.id}",
                                    modifier = Modifier.width(280.dp),
                                )
                            }
                        }
                    }
                }

                // Tail clears the tab bar (Swift :169-171 — 130).
                Spacer(Modifier.height(130.dp))
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
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// ─── Shipments Summary — 2x2 "Card Page" tiles ─────────────────────────────

private data class SummaryTile(
    val label: String,
    val baseIconRes: Int,
    val accentIconRes: Int,
    val route: String,
    val testTag: String,
)

@Composable
private fun SummarySection(state: ShipmentsUiState, onNavigate: (String) -> Unit) {
    val colors = AirdropTheme.colors
    val tiles = listOf(
        SummaryTile(
            "Track Shipment",
            R.drawable.ic_joinery_base,
            R.drawable.ic_joinery_accent,
            Routes.PACKAGES,
            "track-shipment",
        ) to state.summary.totalShipments,
        SummaryTile(
            "Packages",
            R.drawable.ic_packages_base,
            R.drawable.ic_packages_accent,
            Routes.PACKAGES,
            "packages",
        ) to state.summary.totalPackages,
        SummaryTile(
            "Payments",
            R.drawable.ic_payments_base,
            R.drawable.ic_payments_accent,
            Routes.PAYMENTS,
            "payments",
        ) to state.summary.totalPayments,
        SummaryTile(
            "Orders",
            R.drawable.ic_orders_base,
            R.drawable.ic_orders_accent,
            Routes.ORDERS,
            "orders",
        ) to state.summary.totalOrders,
    )
    Column(
        Modifier.padding(horizontal = Spacing.md),
        // Swift: header → grid gap 12, grid rows gap 10.
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            // Swift makeSectionHeader — Title1 (Bold 18).
            text = "Shipments Summary",
            style = AirdropType.title1,
            color = colors.textDarkTitle,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { (tile, count) ->
                        SummaryTileCard(
                            tile = tile,
                            count = count,
                            onClick = { onNavigate(tile.route) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTileCard(
    tile: SummaryTile,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    // Swift makeStatTile: gray150 card, 93pt tall, radius 15, icon 22 +
    // subtitle2 title at top (12/14 inset), h5 value pinned bottom-left.
    Box(
        modifier = modifier
            .height(93.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .testTag("shipments-summary-${tile.testTag}")
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 14.dp),
        ) {
            SummaryTileIcon(tile)
            Text(
                text = tile.label,
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
                maxLines = 1,
            )
        }
        Text(
            text = count?.toString() ?: "0",
            style = AirdropType.h5,
            color = colors.textDarkTitle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun SummaryTileIcon(tile: SummaryTile) {
    val colors = AirdropTheme.colors
    // Swift FigmaShipmentsViewController.makeStatTile uses orange primary
    // strokes plus textDarkTitle secondary strokes. Split the vector so the
    // secondary paths follow the app's in-process ThemeController dark mode;
    // @color/icon_duotone only follows Android resource-night configuration.
    Box(Modifier.size(22.dp).testTag("shipments-summary-icon-${tile.testTag}")) {
        Image(
            painter = painterResource(tile.baseIconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(tile.accentIconRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NoOrdersCard() {
    val colors = AirdropTheme.colors
    // Swift makeEmptyOrdersCard: 280x120 gray100 card, centered Body1.
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "No orders", style = AirdropType.body1, color = colors.textDescription)
    }
}
