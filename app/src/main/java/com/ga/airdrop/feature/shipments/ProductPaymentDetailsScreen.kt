package com.ga.airdrop.feature.shipments

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Product payment details — Figma node 40004950:25064 ("Auction Order
 * Details"), behavior from FigmaProductPaymentDetailsViewController: hero
 * product image, product + payment summaries, dual-currency total.
 */
@Composable
fun ProductPaymentDetailsScreen(
    paymentId: String,
    onBack: () -> Unit,
    viewModel: ProductPaymentDetailsViewModel = viewModel(key = "productPayment/$paymentId") {
        ProductPaymentDetailsViewModel(paymentId)
    },
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val payment = state.payment
    val order = state.order
    val rate = state.effectiveRate

    Box(Modifier.fillMaxSize().background(colors.gray150)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(ShipmentsHeaderClearance))

            when {
                state.loading -> ShipmentsLoadingIndicator(Modifier.padding(Spacing.xl))
                payment == null -> ShipmentsEmptyLabel(state.error ?: "Payment not found")
                else -> {
                    // Hero product image — 245x149 in a 30dp padded panel.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        SubcomposeAsyncImage(
                            model = order?.productImage,
                            contentDescription = order?.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(width = 245.dp, height = 149.dp),
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

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        // Product summary
                        ShipmentsSectionCard(
                            title = "Product Summary",
                            titleStyle = AirdropType.subtitle1,
                        ) {
                            ShipmentsListRow(
                                "Invoice Number",
                                payment.invoiceId ?: order?.invoiceId ?: order?.orderNumber ?: "-",
                            )
                            ShipmentsListRow(
                                "Product Name",
                                order?.productName ?: order?.title ?: "-",
                            )
                            ShipmentsListRow("Regular Price", ShipmentsFormat.price(order?.regularPriceUsd))
                            ShipmentsListRow("Sale Price", ShipmentsFormat.price(order?.salePriceUsd))
                            ShipmentsListRow(
                                "Purchased At",
                                ShipmentsFormat.date(order?.purchasedAt ?: order?.createdAt),
                            )
                            ShipmentsListRow(
                                "Status",
                                ShipmentsFormat.capitalizeFirstWord(
                                    order?.productStatus ?: order?.orderStatus ?: order?.status
                                ),
                                valueColor = AlertPalette.Pending,
                                valueStyle = AirdropType.title2,
                                showDivider = false,
                            )
                        }

                        // Payment summary
                        ShipmentsSectionCard(
                            title = "Payment Summary",
                            titleStyle = AirdropType.subtitle1,
                        ) {
                            ShipmentsListRow(
                                "Invoice Number",
                                payment.invoiceId ?: order?.invoiceId ?: order?.orderNumber ?: "-",
                            )
                            ShipmentsListRow(
                                "Payment Method",
                                ShipmentsFormat.capitalizeFirstWord(
                                    payment.method ?: order?.paymentMethod
                                ),
                            )
                            ShipmentsListRow(
                                "Amount Paid",
                                ShipmentsFormat.usdJmd(
                                    payment.totalAmount ?: order?.salePriceUsd ?: order?.invoiceAmountUsd,
                                    rate,
                                ),
                            )
                            ShipmentsListRow(
                                "Exchange Rate",
                                if (rate > 0) "USD 1 = JMD ${ShipmentsFormat.money(rate)}" else "-",
                                showDivider = false,
                            )
                        }

                        TotalChargesBox(value = ShipmentsFormat.usdJmd(state.totalUsd, rate))

                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }
        }

        ShipmentsDetailHeader(
            title = "Auction Order Details",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
