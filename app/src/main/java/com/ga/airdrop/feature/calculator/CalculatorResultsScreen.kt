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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.util.Locale
import kotlin.math.abs

/**
 * Calculator Results — Figma 40001817:19439 (Standard) / 40001817:20391
 * (Express) / 40001817:20537 (SeaDrop). Behavior from
 * FigmaCalculatorResultsViewController; the CIF info button opens a plain
 * "CIF Value" alert (Swift onCIFInfoTapped), and the disclaimer's
 * "Click the link" span is decorative/non-interactive — both matching Swift
 * (which ships neither a CIF bottom sheet nor a Government Charges screen).
 */
@Composable
fun CalculatorResultsScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onDropAlert: () -> Unit,
    onMakePayment: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val result by viewModel.result.collectAsState()
    val current = result
    if (current == null) {
        // Result lost (e.g. process recreation) — nothing to show, go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val charges = remember(current) { resolveCharges(current) }
    var showCifSheet by remember { mutableStateOf(false) }

    val title = when (current.method) {
        ShippingMethod.EXPRESS -> "Express Results"
        ShippingMethod.SEADROP -> "SeaDrop Results"
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SummaryCard(title = primarySummaryTitle(current), value = primarySummaryValue(current, charges))
                SummaryCard(
                    title = "Invoice Amount (Declared Value/Cost)",
                    value = formatPrice(charges.invoiceAmount),
                )
                SummaryCard(
                    title = "CIF Value",
                    value = formatPrice(charges.cifValue),
                    onInfoClick = { showCifSheet = true },
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
                }
            }

            TotalPill(label = "Total Airdrop Charges", amount = charges.airdropCharges)
            // RN renders both totals; Figma omits the second (Swift parity).
            if (charges.totalWithDuty > 0 && abs(charges.totalWithDuty - charges.airdropCharges) > 0.005) {
                TotalPill(label = "Total with Duty", amount = charges.totalWithDuty)
            }

            DisclaimerCard()
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlineButton(text = "Drop Alert", onClick = onDropAlert, modifier = Modifier.weight(1f))
                GradientButton(text = "Make Payment", onClick = onMakePayment, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showCifSheet) {
        // Swift FigmaCalculatorResultsViewController.swift:502-515 — CIF info is
        // a plain "CIF Value" alert with the Invoice/Insurance/Freight/CIF
        // breakdown and an OK button (not a bottom sheet).
        SimpleAlertDialog(
            title = "CIF Value",
            message = "CIF (Cost, Insurance, Freight) is the estimated value used to " +
                "calculate customs duty.\n\n" +
                "Invoice Amount: ${formatPrice(charges.invoiceAmount)}\n" +
                "Insurance: ${formatPrice(charges.insurance)}\n" +
                "Freight: ${formatPrice(charges.freight)}\n\n" +
                "CIF Value: ${formatPrice(charges.cifValue)}",
            onDismiss = { showCifSheet = false },
        )
    }
}

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

// ─── Result cards ───

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
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
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
internal fun ChargesHeader() {
    val colors = AirdropTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Charges",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
        Text(text = "(USD)", style = AirdropType.subtitle2, color = colors.textDescription)
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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
            style = AirdropType.body2,
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
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
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
            text = "USD " + formatDecimal(amount),
            style = AirdropType.title2,
            color = BrandPalette.OrangeMain,
        )
    }
}

/**
 * Blue disclaimer — Swift FigmaCalculatorResultsViewController.swift:650-705:
 * "Click the link" is DECORATIVE attributed text on a plain label with NO tap
 * gesture and no navigation (Swift ships no Government Charges screen). The
 * span is styled but non-interactive to match Swift's flow.
 */
@Composable
private fun DisclaimerCard() {
    val colors = AirdropTheme.colors
    BlueInfoCard(
        text = {
            val text = buildAnnotatedString {
                append("The price indicated above is an estimate and does not include customs duties or taxes. ")
                withStyle(
                    SpanStyle(
                        color = BrandPalette.OrangeMain,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append("Click the link")
                }
                append(" to get an estimate of these additional charges.")
            }
            Text(
                text = text,
                style = AirdropType.body2.copy(color = colors.textDarkTitle),
            )
        },
    )
}

// CIF info is a Swift-style alert (see SimpleAlertDialog above) — the Figma
// "CIF Value" bottom sheet is intentionally not used (Swift wins).
