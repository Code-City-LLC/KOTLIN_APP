package com.ga.airdrop.feature.cart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.shipments.CustomsNoticeSheet
import com.ga.airdrop.feature.shipments.isCustomsDutyCharge
import java.util.Locale

/** One shared Charges surface for both Order Summary info affordances. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderSummaryChargesSheet(
    breakdown: OrderChargeBreakdown,
    loading: Boolean,
    failedShipmentCount: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCif by remember { mutableStateOf<ShipmentCifBreakdown?>(null) }
    var showingCustomsNotice by remember { mutableStateOf(false) }

    LaunchedEffect(breakdown.cifs.map(ShipmentCifBreakdown::cartKey)) {
        selectedCif = selectedCif?.let { selected ->
            breakdown.cifs.firstOrNull { it.cartKey == selected.cartKey }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.gray150,
        shape = RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = selectedCif == null,
        ),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm)
                    .width(100.dp)
                    .height(6.dp)
                    .background(colors.gray300, RoundedCornerShape(Radius.full)),
            )
        },
    ) {
        OrderSummaryChargesSheetContent(
            breakdown = breakdown,
            loading = loading,
            failedShipmentCount = failedShipmentCount,
            selectedCif = selectedCif,
            onRetry = onRetry,
            onCifClick = { selectedCif = it },
            onCifBack = { selectedCif = null },
            onCustomsNoticeClick = { showingCustomsNotice = true },
        )
    }

    if (showingCustomsNotice) {
        CustomsNoticeSheet(onDismiss = { showingCustomsNotice = false })
    }
}

/** Constraint-driven sheet body, exposed within the cart package for compact UI tests. */
@Composable
internal fun OrderSummaryChargesSheetContent(
    breakdown: OrderChargeBreakdown,
    loading: Boolean,
    failedShipmentCount: Int,
    selectedCif: ShipmentCifBreakdown?,
    onRetry: () -> Unit,
    onCifClick: (ShipmentCifBreakdown) -> Unit,
    onCifBack: () -> Unit,
    onCustomsNoticeClick: () -> Unit,
) {
    // This must live inside the modal content. Material installs its own root
    // sheet Back handler; registering the CIF handler here gives the topmost
    // drilldown first refusal without weakening root-sheet Back dismissal.
    BackHandler(enabled = selectedCif != null, onBack = onCifBack)
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedCif?.cartKey) {
        scrollState.scrollTo(0)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .testTag("order-summary-charges-sheet"),
    ) {
        val containerHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = containerHeight)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
                .navigationBarsPadding()
                .testTag("order-summary-charges-sheet-scroll"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            val cif = selectedCif
            if (cif == null) {
                ChargesOverview(
                    breakdown = breakdown,
                    loading = loading,
                    failedShipmentCount = failedShipmentCount,
                    onRetry = onRetry,
                    onCifClick = onCifClick,
                    onCustomsNoticeClick = onCustomsNoticeClick,
                )
            } else {
                CifDrilldown(
                    cif = cif,
                    onBack = onCifBack,
                    onCustomsNoticeClick = onCustomsNoticeClick,
                )
            }
        }
    }
}

@Composable
private fun ChargesOverview(
    breakdown: OrderChargeBreakdown,
    loading: Boolean,
    failedShipmentCount: Int,
    onRetry: () -> Unit,
    onCifClick: (ShipmentCifBreakdown) -> Unit,
    onCustomsNoticeClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Text(
        text = "Charges",
        style = AirdropType.h5,
        color = colors.textDarkTitle,
        modifier = Modifier.testTag("order-summary-charges-sheet-title"),
    )
    Text(
        text = "Package charges come from the shipment details captured for this checkout. " +
            "Sale items remain separate.",
        style = AirdropType.body2,
        color = colors.textDescription,
    )

    if (loading) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100, RoundedCornerShape(Radius.xs))
                .padding(Spacing.sm1)
                .testTag("order-summary-charges-loading"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = BrandPalette.OrangeMain,
                strokeWidth = 2.dp,
            )
            Text(
                "Loading package charge details…",
                style = AirdropType.body2,
                color = colors.textDarkTitle,
            )
        }
    }

    if (failedShipmentCount > 0) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.peachLight, RoundedCornerShape(Radius.xs))
                .padding(Spacing.sm1)
                .testTag("order-summary-charges-partial-failure"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = if (failedShipmentCount == 1) {
                    "One package charge breakdown could not be loaded. Its captured total is shown."
                } else {
                    "$failedShipmentCount package charge breakdowns could not be loaded. " +
                        "Their captured totals are shown."
                },
                style = AirdropType.body2,
                color = colors.textDarkTitle,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("order-summary-charges-retry"),
            ) {
                Text("Retry", style = AirdropType.button, color = BrandPalette.OrangeMain)
            }
        }
    }

    if (breakdown.hasShipments) {
        ChargesSectionTitle("Shipment Items", "order-summary-shipment-section")
        breakdown.shipments.forEach { shipment ->
            ShipmentChargeCard(
                shipment = shipment,
                cifEnabled = breakdown.shipmentHydrationComplete,
                onCifClick = onCifClick,
                onCustomsNoticeClick = onCustomsNoticeClick,
            )
        }
        if (!breakdown.shipmentHydrationComplete) {
            Text(
                "CIF becomes available after charge details load successfully for every " +
                    "shipment in this order.",
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.testTag("order-summary-cif-incomplete"),
            )
        }
    }

    if (breakdown.hasSales) {
        ChargesSectionTitle("Sale Items", "order-summary-sale-section")
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100, RoundedCornerShape(Radius.xs))
                .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.xs))
                .padding(Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            breakdown.sales.forEach { sale ->
                SheetAmountRow(sale.title, formatUsd(sale.subtotalUsd))
            }
            SheetDivider()
            SheetAmountRow(
                label = "Sale subtotal",
                value = formatUsd(breakdown.saleSubtotalUsd),
                emphasized = true,
                modifier = Modifier.testTag("order-summary-sale-subtotal"),
            )
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.s))
            .testTag("order-summary-charges-sheet-summary"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (breakdown.hasShipments) {
                SheetAmountRow(
                    "Captured shipment subtotal",
                    formatUsd(breakdown.shipmentSubtotalUsd),
                )
            }
            if (breakdown.hasSales) {
                SheetAmountRow("Sale subtotal", formatUsd(breakdown.saleSubtotalUsd))
            }
            breakdown.deliveryFeeUsd?.let {
                SheetAmountRow("Delivery fee", formatUsd(it))
            }
        }
        SheetDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.peachLight)
                .padding(Spacing.sm1)
                .testTag("order-summary-charges-sheet-total"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total Charges", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Text(
                formatCurrency(breakdown.paymentCurrency, breakdown.displayTotal),
                style = AirdropType.subtitle1,
                color = BrandPalette.OrangeMain,
                textAlign = TextAlign.End,
            )
        }
    }
    Text(
        "Total Charges remains the checkout-captured payment amount. Package-detail rows " +
            "are disclosure only.",
        style = AirdropType.body3,
        color = colors.textDescription,
        modifier = Modifier.testTag("order-summary-captured-total-notice"),
    )
}

@Composable
private fun ShipmentChargeCard(
    shipment: ShipmentOrderCharge,
    cifEnabled: Boolean,
    onCifClick: (ShipmentCifBreakdown) -> Unit,
    onCustomsNoticeClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.xs))
            .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.xs))
            .padding(Spacing.sm1)
            .testTag("order-summary-shipment-charge-${shipment.packageId}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            shipment.title,
            style = AirdropType.subtitle2,
            color = colors.textDarkTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        shipment.rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    row.label,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.weight(1f),
                )
                if (isCustomsDutyCharge(row.label)) {
                    SheetInfoButton(
                        contentDescription = "Customs charge information",
                        testTag = "order-summary-customs-info-${shipment.packageId}",
                        onClick = onCustomsNoticeClick,
                    )
                }
                Text(
                    formatUsd(row.amountUsd),
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.End,
                )
            }
        }
        SheetDivider()
        SheetAmountRow(
            label = when (shipment.source) {
                ShipmentChargeSource.ITEMIZED_BACKEND -> "Itemized charge subtotal"
                ShipmentChargeSource.BACKEND_TOTAL_FALLBACK -> "Backend package total"
                ShipmentChargeSource.CAPTURED_TOTAL_FALLBACK -> "Captured package total"
            },
            value = formatUsd(shipment.disclosedSubtotalUsd),
            emphasized = true,
        )
        when (shipment.source) {
            ShipmentChargeSource.ITEMIZED_BACKEND -> Unit
            ShipmentChargeSource.BACKEND_TOTAL_FALLBACK -> Text(
                "The backend package total is shown because itemized rows were unavailable.",
                style = AirdropType.body3,
                color = colors.textDescription,
            )
            ShipmentChargeSource.CAPTURED_TOTAL_FALLBACK -> Text(
                "The checkout-captured package total is shown until package details load.",
                style = AirdropType.body3,
                color = colors.textDescription,
            )
        }
        if (shipment.itemizedTotalDiffersFromBackend) {
            Text(
                "Itemized rows differ from the backend package total; the visible rows are " +
                    "summed without adding an invented adjustment.",
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.testTag("order-summary-charge-mismatch-${shipment.packageId}"),
            )
        }
        if (shipment.disclosureDiffersFromCaptured) {
            Text(
                "This disclosure subtotal differs from the captured checkout subtotal " +
                    "(${formatUsd(shipment.capturedSubtotalUsd)}). Total Charges keeps the " +
                    "captured amount.",
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.testTag(
                    "order-summary-captured-charge-mismatch-${shipment.packageId}",
                ),
            )
        }
        shipment.cif?.takeIf { cifEnabled }?.let { cif ->
            TextButton(
                onClick = { onCifClick(cif) },
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("order-summary-cif-open-${shipment.packageId}"),
            ) {
                Text("View CIF breakdown", style = AirdropType.button, color = BrandPalette.OrangeMain)
            }
        }
    }
}

@Composable
private fun CifDrilldown(
    cif: ShipmentCifBreakdown,
    onBack: () -> Unit,
    onCustomsNoticeClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clickable(onClick = onBack)
                .testTag("order-summary-cif-back"),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow_back_figma),
                contentDescription = "Back to charges",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text("CIF Value", style = AirdropType.h5, color = colors.textDarkTitle)
            Text(
                cif.title,
                style = AirdropType.body2,
                color = colors.textDescription,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Text(
        "CIF is the shipment invoice amount plus Insurance and Freight only. " +
            "Fuel, customs charges, GCT, and sale items are excluded.",
        style = AirdropType.body2,
        color = colors.textDarkTitle,
        modifier = Modifier.testTag("order-summary-cif-formula"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.xs))
            .border(1.dp, colors.cardHairline, RoundedCornerShape(Radius.xs))
            .padding(Spacing.sm1)
            .testTag("order-summary-cif-table"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("", modifier = Modifier.weight(1.35f))
            Text(
                "USD",
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            Text(
                "JMD",
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        CifAmountRow("Invoice amount", cif.invoiceAmountUsd, cif)
        CifAmountRow("Insurance", cif.insuranceUsd, cif)
        CifAmountRow("Freight", cif.freightUsd, cif)
        SheetDivider()
        CifAmountRow("CIF total", cif.totalUsd, cif, emphasized = true)
    }
    if (cif.exchangeRateUsdToJmd == null) {
        Text(
            "The package response did not include an exchange rate, so local values are " +
                "left unavailable.",
            style = AirdropType.body3,
            color = colors.textDescription,
            modifier = Modifier.testTag("order-summary-cif-rate-unavailable"),
        )
    } else {
        Text(
            String.format(
                Locale.US,
                "Package exchange rate: USD 1 = JMD %.2f",
                cif.exchangeRateUsdToJmd,
            ),
            style = AirdropType.body3,
            color = colors.textDescription,
        )
    }
    TextButton(
        onClick = onCustomsNoticeClick,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .testTag("order-summary-customs-notice"),
    ) {
        Text("Read customs notice", style = AirdropType.button, color = BrandPalette.OrangeMain)
    }
}

@Composable
private fun CifAmountRow(
    label: String,
    amountUsd: Double,
    cif: ShipmentCifBreakdown,
    emphasized: Boolean = false,
) {
    val colors = AirdropTheme.colors
    val style = if (emphasized) AirdropType.subtitle2 else AirdropType.body2
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1.35f),
        )
        Text(
            String.format(Locale.US, "%.2f", amountUsd),
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
        Text(
            cif.toJmd(amountUsd)?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
            style = style,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ChargesSectionTitle(text: String, tag: String) {
    Text(
        text = text,
        style = AirdropType.subtitle1,
        color = AirdropTheme.colors.textDarkTitle,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun SheetAmountRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (emphasized) AirdropType.subtitle2 else AirdropType.body2,
            color = if (emphasized) colors.textDarkTitle else colors.textDescription,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = AirdropType.subtitle2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AirdropTheme.colors.cardHairline))
}

@Composable
private fun SheetInfoButton(
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(AirdropTheme.colors.iconSelected),
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun formatUsd(amount: Double): String =
    String.format(Locale.US, "USD %.2f", amount)

private fun formatCurrency(currency: String, amount: Double): String =
    String.format(Locale.US, "%s %.2f", currency, amount)
