package com.ga.airdrop.feature.shipments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Payment package details — behavior from
 * FigmaPaymentPackageDetailsViewController; the "View History" timeline is
 * Figma node 40001761:29389 (Metro Step Card with the 7 canonical stops).
 */
@Composable
fun PaymentPackageDetailsScreen(
    paymentId: String,
    onBack: () -> Unit,
    viewModel: PaymentPackageDetailsViewModel = viewModel(key = "paymentPackage/$paymentId") {
        PaymentPackageDetailsViewModel(paymentId)
    },
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    // Hardware back closes the history overlay before popping the route.
    BackHandler(enabled = state.showHistory) { viewModel.showHistory(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("payment-package-root")
    ) {
        if (state.showHistory) {
            PaymentShipmentTimeline(state = state)
            ShipmentsDetailHeader(
                title = "View History",
                onBack = { viewModel.showHistory(false) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("payment-package-scroll")
            ) {
                Spacer(Modifier.height(shipmentsHeaderClearance()))
                when {
                    state.loading -> ShipmentsLoadingIndicator(Modifier.padding(Spacing.xl))
                    state.payment == null -> ShipmentsEmptyLabel(state.error ?: "Payment not found")
                    else -> PaymentPackageDetailsContent(
                        state = state,
                        onCifInfo = { viewModel.showCifInfo(true) },
                    )
                }
            }
            if (state.payment != null) {
                PaymentPackageDetailsFooter(
                    onViewHistory = { viewModel.showHistory(true) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            ShipmentsDetailHeader(
                title = "Packages Payment Details",
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (state.showCifInfo) {
            ShipmentsAlertDialog(
                title = "CIF Value",
                message = "CIF = Cost + Insurance + Freight — the package value used to compute customs charges.",
                confirmText = "OK",
                onConfirm = { viewModel.showCifInfo(false) },
                onDismiss = { viewModel.showCifInfo(false) },
            )
        }
    }
}

@Composable
internal fun PaymentPackageDetailsContent(
    state: PaymentPackageDetailsUiState,
    onCifInfo: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val payment = state.payment ?: return
    val detail = state.detail
    val rate = state.effectiveRate

    Column(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Package summary
        ShipmentsSectionCard(title = "Summary") {
            ShipmentsListRow("Invoice Number", payment.invoiceId ?: "-")
            ShipmentsListRow(
                "Drop Number",
                ShipmentsFormat.trackingCode(detail?.trackingCode ?: payment.trackingCode),
            )
            ShipmentsListRow("Shipping Method", detail?.shippingMethod ?: "-")
            ShipmentsListRow("Merchant/Shipper", detail?.shipper ?: detail?.store ?: "-")
            ShipmentsListRow("Courier Tracking", detail?.courierNumber ?: "-")
            ShipmentsListRow(
                "Description",
                ShipmentsFormat.titleCase(
                    detail?.description
                        ?: ShipmentsFormat.decodeHtmlEntities(payment.packageDescription)
                ),
            )
            ShipmentsListRow("Number of Pieces", detail?.numberOfPieces?.toString() ?: "-")
            ShipmentsListRow(
                "Weight/Volume",
                ShipmentsFormat.weight(detail?.weightLbs, detail?.weightKg, detail?.weight),
            )
            ShipmentsListRow(
                // Swift FigmaPaymentPackageDetailsViewController.swift:794.
                "Invoice Amount (Declared Value/Cost)",
                when {
                    (detail?.amount ?: 0.0) > 0.0 -> ShipmentsFormat.price(detail?.amount)
                    (detail?.originalPrice ?: 0.0) > 0.0 -> ShipmentsFormat.price(detail?.originalPrice)
                    else -> "-"
                },
            )
            ShipmentsListRow(
                "Status",
                detail?.statusName ?: payment.packageStatusName ?: "-",
                valueColor = AlertPalette.Completed,
                showDivider = false,
            )
        }

        // Payment summary
        ShipmentsSectionCard(title = "Payment Summary") {
            ShipmentsListRow("Invoice Number", payment.invoiceId ?: "-")
            ShipmentsListRow("Payment Method", ShipmentsFormat.capitalizeFirstWord(payment.method))
            ShipmentsListRow("Amount Paid", ShipmentsFormat.usdJmdPlain(payment.totalAmount, rate))
            ShipmentsListRow(
                "Exchange Rate",
                if (rate > 0) "USD 1 = JMD ${ShipmentsFormat.moneyPlain(rate)}" else "-",
                showDivider = false,
            )
        }

        // CIF pill — Swift makeCIFInfoPill (FigmaPaymentPackageDetailsViewController
        // .swift:470-508): 48pt tall, radius 10, 16pt insets, infoCircle 20pt
        // tinted textDarkTitle. (Was 59dp / radius 15 / 24dp iconSelected.)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .clickable(onClick = onCifInfo)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "CIF Value", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = "CIF info",
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier.size(20.dp),
            )
        }

        // Breakdown of Charges
        val charges = detail?.additionalCharges.orEmpty()
        val chargesTotal = detail?.additionalChargesTotal
            ?: charges.values.sum().takeIf { charges.isNotEmpty() }
        if (charges.isNotEmpty() || (chargesTotal ?: 0.0) > 0.0) {
            ShipmentsSectionCard(title = "Breakdown of Charges", showChevron = false) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ChargeTableRow("Services", "USD", "Local (JMD)", colors.textDescription)
                    charges.entries.sortedBy { it.key }.forEach { (name, amount) ->
                        ChargeTableRow(
                            name,
                            "$" + ShipmentsFormat.moneyPlain(amount),
                            "$" + ShipmentsFormat.moneyPlain(amount * rate),
                            colors.textDarkTitle,
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = "Subtotal",
                            style = AirdropType.title2,
                            color = colors.textDarkTitle,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$" + ShipmentsFormat.moneyPlain(chargesTotal ?: 0.0),
                            style = AirdropType.title2,
                            color = colors.textDarkTitle,
                        )
                        Spacer(Modifier.size(Spacing.md))
                        Text(
                            text = "$" + ShipmentsFormat.moneyPlain((chargesTotal ?: 0.0) * rate),
                            style = AirdropType.title2,
                            color = colors.textDarkTitle,
                        )
                    }
                }
            }
        }

        TotalChargesBox(value = ShipmentsFormat.usdJmdPlain(state.totalUsd, rate))

        Spacer(Modifier.height(116.dp))
    }
}

@Composable
internal fun PaymentPackageDetailsFooter(
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(colors.gray100)
            .testTag("payment-package-footer"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.gray300)
        )
        PaymentPackageHistoryButton(
            onClick = onViewHistory,
            modifier = Modifier
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md)
                .testTag("payment-package-view-history-button"),
        )
    }
}

@Composable
private fun PaymentPackageHistoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Swift FigmaPaymentPackageDetailsViewController.swift:297-298.
            .height(50.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "View History", style = AirdropType.button, color = BrandPalette.OrangeMain)
    }
}

@Composable
private fun ChargeTableRow(name: String, usd: String, jmd: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(text = name, style = AirdropType.body2, color = color, modifier = Modifier.weight(1f))
        Text(text = usd, style = AirdropType.body2, color = color)
        Spacer(Modifier.size(Spacing.md))
        Text(text = jmd, style = AirdropType.body2, color = color)
    }
}

// ─── View History — Figma 40001761:29389 (Metro Step Card) ─────────────────

/** The 7 canonical stops of the payment shipment timeline (Swift order). */
private val timelineStops = listOf(
    1 to "Drop Alerted",
    2 to "Shipment Received",
    3 to "Port of Departure MIA",
    4 to "Arrived at Port JAM",
    7 to "Ready for Pickup",
    18 to "Paid and Ready for Pick Up",
    8 to "Delivered",
)

/** Deep orange used for the in-flight step while still overseas (<= JAM). */
private val InFlightOrange = Color(0xFFF07F17)

@Composable
internal fun PaymentShipmentTimeline(state: PaymentPackageDetailsUiState) {
    val colors = AirdropTheme.colors
    val detail = state.detail
    val statusInt = detail?.status?.toIntOrNull() ?: 0
    val history = detail?.history.orEmpty()

    fun historyDate(statusId: Int): String? =
        history.firstOrNull { it.status == statusId }?.changedDate

    // Progress index: furthest stop reached via history or the current status.
    val reachedIndex = timelineStops.indexOfLast { (id, _) ->
        id == statusInt || historyDate(id) != null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("payment-history-root")
    ) {
        Spacer(Modifier.height(shipmentsHeaderClearance()))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.s))
                    .background(colors.gray100)
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                    .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm),
            ) {
                timelineStops.forEachIndexed { index, (statusId, title) ->
                    val date = historyDate(statusId)
                    val color = when {
                        index < reachedIndex -> AlertPalette.Completed
                        index == reachedIndex && statusInt <= 4 -> InFlightOrange
                        index == reachedIndex -> AlertPalette.Completed
                        else -> colors.textPlaceholder
                    }
                    MetroStep(
                        iconRes = ShipmentStatusCatalog.iconRes(statusId),
                        title = title,
                        titleColor = color,
                        date = timelineDateOrDash(date),
                        showConnector = index != timelineStops.lastIndex,
                        connectorColor = color,
                        modifier = Modifier.testTag("payment-history-step-$statusId"),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

private fun timelineDateOrDash(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return "-"
    return ShipmentsFormat.timelineDate(value)
        .takeUnless { it == "N/A" || it.isBlank() }
        ?: "-"
}
