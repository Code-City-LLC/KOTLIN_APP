package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Order details — Figma node 40001761:28814, behavior from
 * FigmaOrderDetailsViewController: hero product image, Order Summary card,
 * JMD-formatted Total box.
 */
@Composable
fun OrderDetailsScreen(
    orderId: String,
    onBack: () -> Unit,
    viewModel: OrderDetailsViewModel = viewModel(key = "orderDetails/$orderId") {
        OrderDetailsViewModel(orderId)
    },
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val order = state.order

    Box(Modifier.fillMaxSize().background(colors.gray150)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(shipmentsHeaderClearance()))
            when {
                state.loading -> ShipmentsLoadingIndicator(Modifier.padding(Spacing.xl))
                order == null -> ShipmentsEmptyLabel(state.error ?: "Order not found")
                else -> {
                    // Swift FigmaOrderDetailsViewController.swift: 209pt wrap,
                    // 20pt insets, image fills remaining width.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(209.dp)
                            .testTag("order-details-hero-wrap"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(209.dp)
                                .padding(Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            SubcomposeAsyncImage(
                                model = order.productImage,
                                contentDescription = order.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(169.dp)
                                    .testTag("order-details-hero-image"),
                                error = {
                                    Image(
                                        painter = painterResource(R.drawable.ic_shop),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colors.gray400),
                                        modifier = Modifier.size(80.dp),
                                    )
                                },
                            )
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ShipmentsSectionCard(title = "Order Summary") {
                            ShipmentsListRow("Order Description", order.title ?: "-")
                            ShipmentsListRow("Merchant/Shipper", order.customerName ?: "-")
                            ShipmentsListRow(
                                "Weight (lbs.)",
                                order.weightLbs?.let { ShipmentsFormat.money(it) } ?: "-",
                            )
                            ShipmentsListRow("Package Value", ShipmentsFormat.usd(order.invoiceAmountUsd))
                            ShipmentsListRow("Date", ShipmentsFormat.date(order.createdAt))
                            ShipmentsListRow(
                                "Status",
                                ShipmentsFormat.titleCase(order.orderStatus ?: order.status),
                                valueColor = AlertPalette.Pending,
                                valueStyle = AirdropType.title2,
                                showDivider = false,
                            )
                        }

                        // "USD 403.35 / JMD 64,680.20" — comma-grouped, 2 decimals.
                        TotalChargesBox(
                            value = ShipmentsFormat.usdJmd(order.invoiceAmountUsd, state.effectiveRate),
                            textColor = BrandPalette.OrangeTertiary1,
                        )

                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }
        }

        ShipmentsDetailHeader(
            title = "Order Details",
            titleStyle = AirdropType.title2,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
