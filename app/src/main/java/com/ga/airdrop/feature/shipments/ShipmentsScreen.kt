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
            Spacer(Modifier.height(ShipmentsHeaderClearance))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SummarySection(state = state, onNavigate = onNavigate)

                // Packages preview
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderRow(
                        title = "Packages",
                        actionText = "View More",
                        onAction = { onNavigate(Routes.PACKAGES) },
                    )
                    if (state.packages.isEmpty()) {
                        if (state.loading) ShipmentsLoadingIndicator()
                        else ShipmentsEmptyLabel("No packages found")
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(state.packages, key = { it.id }) { pkg ->
                                PackageCard(
                                    pkg = pkg,
                                    exchangeRate = state.exchangeRate,
                                    onClick = { onNavigate(Routes.packageDetails(pkg.id.toString())) },
                                    onToggleCart = { viewModel.toggleCart(pkg.id) },
                                    inCart = ShipmentsCartStore.contains(pkg.id),
                                    modifier = Modifier.width(281.dp),
                                )
                            }
                        }
                    }
                }

                // Payments preview
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderRow(
                        title = "Payments",
                        actionText = "View More",
                        onAction = { onNavigate(Routes.PAYMENTS) },
                    )
                    if (state.payments.isEmpty()) {
                        if (state.loading) ShipmentsLoadingIndicator()
                        else ShipmentsEmptyLabel("No payments found")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            state.payments.forEach { payment ->
                                PaymentCard(
                                    payment = payment,
                                    onClick = { openPayment(payment) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // Orders preview
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderRow(
                        title = "Orders",
                        actionText = "View More",
                        onAction = { onNavigate(Routes.ORDERS) },
                    )
                    if (state.orders.isEmpty()) {
                        if (state.loading) ShipmentsLoadingIndicator()
                        else NoOrdersCard()
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            items(state.orders, key = { it.id }) { order ->
                                OrderCard(
                                    order = order,
                                    onClick = { onNavigate(Routes.orderDetails(order.id.toString())) },
                                    modifier = Modifier.width(281.dp),
                                )
                            }
                        }
                    }
                }

                // Clearance for the glass bottom bar.
                Spacer(Modifier.height(90.dp))
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

private data class SummaryTile(val label: String, val iconRes: Int, val route: String)

@Composable
private fun SummarySection(state: ShipmentsUiState, onNavigate: (String) -> Unit) {
    val colors = AirdropTheme.colors
    val tiles = listOf(
        SummaryTile("Track Shipment", R.drawable.ic_joinery, Routes.PACKAGES) to state.summary.totalShipments,
        SummaryTile("Packages", R.drawable.ic_packages, Routes.PACKAGES) to state.summary.totalPackages,
        SummaryTile("Payments", R.drawable.ic_payments, Routes.PAYMENTS) to state.summary.totalPayments,
        SummaryTile("Orders", R.drawable.ic_orders, Routes.ORDERS) to state.summary.totalOrders,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Shipments Summary",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
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

@Composable
private fun SummaryTileCard(
    tile: SummaryTile,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(tile.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = tile.label,
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
                maxLines = 1,
            )
        }
        Text(
            text = count?.toString() ?: "—",
            style = AirdropType.h5,
            color = colors.textDarkTitle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoOrdersCard() {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_shop),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.gray400),
            modifier = Modifier.size(40.dp),
        )
        // Swift FigmaShipmentsViewController.swift:830-832 — Body1.
        Text(text = "No orders", style = AirdropType.body1, color = colors.textDescription)
    }
}
