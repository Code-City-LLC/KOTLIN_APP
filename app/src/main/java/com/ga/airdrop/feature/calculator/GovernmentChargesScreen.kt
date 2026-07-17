package com.ga.airdrop.feature.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.frostedGlassSurface

/**
 * Government Charges — Figma 40001817:20681, reached from the results
 * disclaimer link. Shows the duty-inclusive view of the same calculation:
 * Cost + CIF cards, Insurance/Freight/Customs Duties, total pill, and the
 * restricted-shipments advisory. Customs duty comes straight from the
 * /shipping/calculate breakdown (offline Standard has none → $0.00); rates
 * are never computed client-side.
 */
@Composable
fun GovernmentChargesScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onBackToCalculator: () -> Unit,
    onRestrictedItems: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val result by viewModel.result.collectAsState()
    val usdToJmd by viewModel.usdToJmd.collectAsState()
    val current = result
    if (current == null) {
        // Result lost (e.g. process recreation) — nothing to show, go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val charges = remember(current) { resolveCharges(current) }
    var showCifSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadExchangeRate() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        InnerScreenHeader(title = "Government Charges", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SummaryCard(
                    title = "Cost (Declared Value/Invoice Amount)",
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
                    ChargeRow("Customs Duties", charges.customsDuty)
                }
            }

            TotalPill(label = "Total Airdrop Charges", amount = charges.airdropCharges)

            RestrictedShipmentsCard(onLinkClick = onRestrictedItems)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.frostedGlassSurface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider)
            )
            GradientButton(
                text = "Back to Calculator",
                onClick = onBackToCalculator,
                modifier = Modifier
                    .padding(Spacing.md)
                    .navigationBarsPadding(),
            )
        }
    }

    if (showCifSheet) {
        CifValueSheet(
            charges = charges,
            usdToJmd = usdToJmd,
            onDismiss = { showCifSheet = false },
        )
    }
}

@Composable
private fun RestrictedShipmentsCard(onLinkClick: () -> Unit) {
    val colors = AirdropTheme.colors
    BlueInfoCard(
        text = {
            val text = buildAnnotatedString {
                append(
                    "The price indicated above is an estimate and is subjected to change. " +
                        "Final costs may vary depending on the actual customs declarations " +
                        "and/or other charges such as special handling on consumer commodities " +
                        "and a select number of import requirements on certain items. "
                )
                pushStringAnnotation(tag = "link", annotation = "restrictedItems")
                withStyle(
                    SpanStyle(
                        color = AirdropTheme.colors.orangeMain,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append("See restricted shipments for more information")
                }
                pop()
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
