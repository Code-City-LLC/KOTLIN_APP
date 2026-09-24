package com.ga.airdrop.feature.calculator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.CifValueSheet
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Calculator Results — Figma 40001817:19439 (Standard) / 40001817:20391
 * (Express) / 40001817:20537 (SeaDrop). Behavior from
 * FigmaCalculatorResultsViewController; the CIF info button opens the
 * Figma "CIF Value" bottom sheet (40001817:20191). The disclaimer's
 * "Click the link" span navigates to Government Charges (Figma
 * 40001817:20681) — Figma ships that screen even though Swift does not.
 */
@Composable
fun CalculatorResultsScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onDropAlert: () -> Unit,
    onMakePayment: () -> Unit,
    onGovernmentCharges: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val result by viewModel.result.collectAsState()
    val usdToJmd by viewModel.usdToJmd.collectAsState()
    val current = result
    if (current == null) {
        // Result lost (e.g. process recreation) — nothing to show, go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val charges = remember(current) { resolveCharges(current) }
    val tierQuote = current.tierQuote
    var showCifSheet by remember { mutableStateOf(false) }
    var quoteClockTick by remember(tierQuote?.expiresAt, tierQuote?.isExpired) { mutableStateOf(0) }

    LaunchedEffect(tierQuote) {
        if (tierQuote == null) {
            viewModel.loadExchangeRate()
            return@LaunchedEffect
        }
        while (true) {
            delay(60_000)
            quoteClockTick++
        }
    }
    val tierQuoteExpired = remember(tierQuote, quoteClockTick) { tierQuote?.isExpiredNow() ?: false }

    val title = when (current.method) {
        ShippingMethod.EXPRESS,
        ShippingMethod.SEADROP -> "${current.method.label} Results"
        ShippingMethod.STANDARD -> "Results"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        InnerScreenHeader(title = title, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            // Swift contentStack — flat 10 (Spacing.sm) between every group.
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (tierQuote != null) {
                TierQuoteResultsContent(
                    result = current,
                    totalWeightLbs = charges.totalWeightLbs,
                    quote = tierQuote,
                    expired = tierQuoteExpired,
                    actionLoading = state.tierQuoteActionLoading,
                    onRefresh = viewModel::refreshTierQuote,
                    onKeepInsurance = { viewModel.selectTierInsurance(true) },
                    onDeclineInsurance = { viewModel.selectTierInsurance(false) },
                )
            } else {
                LegacyResultsContent(
                    result = current,
                    charges = charges,
                    onShowCif = { showCifSheet = true },
                    onGovernmentCharges = onGovernmentCharges,
                )
            }
        }

        // Footer — Swift buildFooter: Drop Alert (outline) + Make Payment.
        // The Figma frame shows the template "Calculate" button here; the
        // Swift VC's two-CTA footer is the shipped behavior.
        // Swift FigmaCalculatorResultsViewController.swift:231-238 — gray150
        // footer with 1dp iconShape top border.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray150)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
            Row(
                Modifier
                    .padding(Spacing.md)
                    .navigationBarsPadding(),
                // Swift footer row spacing 12 (:252).
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlineButton(text = "Drop Alert", onClick = onDropAlert, modifier = Modifier.weight(1f))
                GradientButton(
                    text = "Make Payment",
                    onClick = { if (viewModel.canProceedToPayment()) onMakePayment() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showCifSheet && tierQuote == null) {
        CifValueSheet(
            rows = charges.cifRows(),
            exchangeRate = usdToJmd,
            onDismiss = { showCifSheet = false },
        )
    }

    state.alert?.let { alert ->
        SimpleAlertDialog(title = alert.title, message = alert.message, onDismiss = viewModel::dismissAlert)
    }
}

@Composable
private fun LegacyResultsContent(
    result: CalculationResult,
    charges: Charges,
    onShowCif: () -> Unit,
    onGovernmentCharges: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SummaryCard(title = primarySummaryTitle(result), value = primarySummaryValue(result, charges))
        SummaryCard(
            title = "Invoice Amount (Declared Value/Cost)",
            value = formatPrice(charges.invoiceAmount),
        )
        SummaryCard(
            title = "CIF Value",
            value = formatPrice(charges.cifValue),
            onInfoClick = onShowCif,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChargesHeader()
        ChargesCard {
            ChargeRow("Insurance", charges.insurance)
            ChargeRow("Freight", charges.freight)
            ChargeRow("Fuel", charges.fuelSurcharge)
            if (charges.customsDuty > 0) {
                ChargeRow("Customs Duty", charges.customsDuty)
            }
            charges.badAddressFee?.takeIf { it > 0 }?.let {
                ChargeRow("Bad Address Fee", it)
            }
        }
    }

    TotalPill(label = "Total Airdrop Charges", amount = charges.airdropCharges)
    if (charges.totalWithDuty > 0 && abs(charges.totalWithDuty - charges.airdropCharges) > 0.005) {
        TotalPill(label = "Total with Duty", amount = charges.totalWithDuty)
    }

    DisclaimerCard(onLinkClick = onGovernmentCharges)
}

@Composable
private fun TierQuoteResultsContent(
    result: CalculationResult,
    /** Every package ([resolveCharges]); [CalculationResult.weightLbs] is one package's. */
    totalWeightLbs: Double,
    quote: TierQuote,
    expired: Boolean,
    actionLoading: Boolean,
    onRefresh: () -> Unit,
    onKeepInsurance: () -> Unit,
    onDeclineInsurance: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SummaryCard(title = "Total Weight", value = String.format(Locale.US, "%.2f lbs", totalWeightLbs))
        SummaryCard(
            title = "Invoice Amount (Declared Value/Cost)",
            value = formatPrice(result.invoiceUsd),
        )
        quote.customerTier?.takeIf { it.isNotBlank() }?.let { tier ->
            SummaryCard(title = "Service Tier", value = tier)
        }
    }

    if (expired) {
        TierQuoteExpiryCard(onRefresh = onRefresh, loading = actionLoading)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChargesHeader(title = "Charges", currency = quote.currency ?: "USD")
        ChargesCard {
            val cashItems = quote.lineItems.filter { it.code != "aircoins_credit" }
            if (cashItems.isEmpty()) {
                Text(
                    text = "No charges returned for this quote.",
                    style = AirdropType.subtitle2,
                    color = AirdropTheme.colors.textDescription,
                )
            } else {
                cashItems.forEach { item ->
                    ChargeRow(
                        label = item.label?.takeIf { it.isNotBlank() } ?: item.code.ifBlank { "Charge" },
                        amount = item.amount,
                    )
                }
            }
        }
    }

    TotalPill(label = "Total Due", amount = quote.totalDue)

    if (quote.aircoinsEarned > 0) {
        SummaryCard(
            title = "AirCoins earned on this shipment",
            value = String.format(Locale.US, "%.1f", quote.aircoinsEarned),
        )
    }

    quote.insuranceOptions?.takeIf {
        quote.insuranceChoiceRequired || it.explicitRequired || it.canDecline
    }?.let { options ->
        TierInsuranceChoiceCard(
            options = options,
            choice = result.insuranceChoice,
            loading = actionLoading,
            onKeepInsurance = onKeepInsurance,
            onDeclineInsurance = onDeclineInsurance,
        )
    }

    TierQuoteMeta(quote = quote, expired = expired)
}

@Composable
private fun TierQuoteExpiryCard(onRefresh: () -> Unit, loading: Boolean) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(BrandPalette.OrangeTertiary6, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.orangeMain, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "This quote has expired. Refresh to get current pricing before payment.",
            style = AirdropType.subtitle2,
            color = colors.orangeMain,
        )
        GradientButton(text = "Refresh Quote", onClick = onRefresh, loading = loading)
    }
}

@Composable
private fun TierInsuranceChoiceCard(
    options: TierInsuranceOptions,
    choice: Boolean?,
    loading: Boolean,
    onKeepInsurance: () -> Unit,
    onDeclineInsurance: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray150, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Insurance (optional for your tier)", style = AirdropType.title2, color = colors.textDarkTitle)
        Text(
            text = "Premium ${formatPrice(options.premium)} covers ${formatPrice(options.coveredValue)} of declared value. " +
                "You must select or decline insurance before payment.",
            style = AirdropType.body2,
            color = colors.textDescription,
        )
        val stateText = when (choice) {
            true -> "Insurance selected"
            false -> "Insurance declined"
            null -> "Choice required"
        }
        Text(
            text = stateText,
            style = AirdropType.subtitle2,
            color = if (choice == null) colors.orangeMain else colors.textDarkTitle,
        )
        if (loading) {
            Text("Updating quote...", style = AirdropType.subtitle3, color = colors.textDescription)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton(
                text = "Keep Insurance",
                onClick = onKeepInsurance,
                modifier = Modifier.weight(1f),
            )
            OutlineButton(
                text = "Decline",
                onClick = onDeclineInsurance,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TierQuoteMeta(quote: TierQuote, expired: Boolean) {
    val lines = buildList {
        quote.quoteReference?.takeIf { it.isNotBlank() }?.let { add("Quote $it") }
        quote.expiresAt?.let { raw ->
            val date = formatTierQuoteExpiry(raw) ?: raw
            add(
                if (expired) {
                    "Expired $date - refresh required before payment."
                } else {
                    "Valid until $date. Prices are held until then."
                },
            )
        }
    }
    if (lines.isNotEmpty()) {
        Text(
            text = lines.joinToString("\n"),
            style = AirdropType.body2,
            color = AirdropTheme.colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatTierQuoteExpiry(raw: String): String? = runCatching {
    OffsetDateTime.parse(raw)
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
}.getOrNull()

/** Card 1 title — RN useCalculatorResults switch on `form.action`. */
private fun primarySummaryTitle(result: CalculationResult): String = when (result.method) {
    ShippingMethod.EXPRESS -> "Total Volumetric Weight (DIM)"
    ShippingMethod.SEADROP -> "Total Dimensions"
    ShippingMethod.STANDARD -> "Total Weight"
}

private fun primarySummaryValue(result: CalculationResult, charges: Charges): String =
    when (result.method) {
        ShippingMethod.EXPRESS -> String.format(Locale.US, "%.2f lbs", charges.totalWeightLbs)
        ShippingMethod.SEADROP -> {
            val l = result.lengthIn
            val w = result.widthIn
            val h = result.heightIn
            if (l != null && w != null && h != null && l > 0 && w > 0 && h > 0) {
                String.format(Locale.US, "%.2f ft³", (l * w * h) / 1728.0)
            } else {
                "0.00 ft³"
            }
        }
        ShippingMethod.STANDARD ->
            if (result.weightUnit == WeightUnit.KG) {
                String.format(Locale.US, "%.2f kg", charges.totalWeightLbs * 0.453592)
            } else {
                String.format(Locale.US, "%.2f lbs", charges.totalWeightLbs)
            }
    }

// ─── Result cards (shared with GovernmentChargesScreen) ───

/**
 * Figma "Caouning Card" (sic): gray150, iconShape border, radius 10,
 * 20/15 padding — SubTitle2 label over H5 value, optional info button.
 */
@Composable
internal fun SummaryCard(
    title: String,
    value: String,
    onInfoClick: (() -> Unit)? = null,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray150, RoundedCornerShape(Radius.s)) // Swift FigmaCalculatorResultsViewController.swift:409/:553 — Radius.s
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            // Swift makeSummaryCard :431/438 — vertical 10 (top/bottom Spacing.sm).
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            if (onInfoClick != null) {
                Image(
                    painter = painterResource(R.drawable.ic_calc_info_circle),
                    contentDescription = "What is this?",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onInfoClick),
                )
            }
        }
        Text(text = value, style = AirdropType.h5, color = colors.textDarkTitle)
    }
}

/** "Charges" + "(USD)" header row — no card chrome. */
@Composable
internal fun ChargesHeader(title: String = "Charges", currency: String = "USD") {
    val colors = AirdropTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
                text = title,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
        Text(text = "($currency)", style = AirdropType.subtitle2, color = colors.textDescription)
    }
}

/** Figma "Result Caouning Card" 40001464:31301 — rows gap 10, 20/15 padding. */
@Composable
internal fun ChargesCard(content: @Composable () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray150, RoundedCornerShape(Radius.s)) // Swift FigmaCalculatorResultsViewController.swift:409/:553 — Radius.s
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        // Swift makeChargesCard :560 — inter-row 5 (Spacing.xs).
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        content()
    }
}

@Composable
internal fun ChargeRow(label: String, amount: Double) {
    val colors = AirdropTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            // Swift FigmaCalculatorResultsViewController.swift:586 — subtitle2 (semibold).
            style = AirdropType.subtitle2,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatPrice(amount),
            style = AirdropType.subtitle2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Right,
        )
    }
}

/** Orange total pill — Figma 40001817:19560: #FAF6F5 fill, Title2 orange. */
@Composable
internal fun TotalPill(label: String, amount: Double) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(BrandPalette.OrangeTertiary6, RoundedCornerShape(Radius.s)) // Swift FigmaCalculatorResultsViewController.swift:615 — Radius.s
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            // Swift makeTotalPill :636/639-640 — min 56 height, vertical 10.
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = label,
            style = AirdropType.title2,
            color = BrandPalette.OrangeMain,
            modifier = Modifier.weight(1f),
        )
        Text(
            // The headline totals ("Total Airdrop Charges", "Total with Duty").
            // JMD / USD, never one currency — Kemar 2026-07-26.
            text = formatPrice(amount),
            style = AirdropType.title2,
            color = BrandPalette.OrangeMain,
        )
    }
}

/**
 * Blue disclaimer — Figma 40001817:19439: the orange underlined "Click the
 * link" span is an interactive link that navigates to Government Charges
 * (Figma 40001817:20681) to show the customs-duty estimate. Figma ships that
 * screen (Swift does not); per the source-of-truth hierarchy Figma wins here.
 */
@Composable
private fun DisclaimerCard(onLinkClick: () -> Unit) {
    val colors = AirdropTheme.colors
    BlueInfoCard(
        text = {
            val text = buildAnnotatedString {
                append("The price indicated above is an estimate and does not include customs duties or taxes. ")
                pushStringAnnotation(tag = "link", annotation = "governmentCharges")
                withStyle(
                    SpanStyle(
                        color = BrandPalette.OrangeMain,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append("Click the link")
                }
                pop()
                append(" to get an estimate of these additional charges.")
            }
            ClickableText(
                text = text,
                style = AirdropType.body2.copy(color = colors.textDarkTitle),
                onClick = { offset ->
                    text.getStringAnnotations("link", offset, offset).firstOrNull()?.let { onLinkClick() }
                },
            )
        },
    )
}
