package com.ga.airdrop.feature.more2

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

// Fallback rates table — canonical /api/v1/shipping-rates baseline, kept so
// the page stays useful when the backend is unreachable (Swift parity).
private val FALLBACK_STANDARD_RATES: List<Pair<String, Double>> = listOf(
    "1" to 5.00, "2" to 6.00, "3" to 7.00, "4" to 8.00, "5" to 9.00,
    "6" to 10.00, "7" to 11.00, "8" to 12.00, "9" to 13.00, "10" to 14.00,
    "11" to 16.00, "12" to 18.00, "13" to 20.00, "14" to 22.00, "15" to 24.00,
    "16" to 26.00, "17" to 28.00, "18" to 30.00, "19" to 32.00, "20" to 34.00,
)

// Grouped like every other money string in the app (was ungrouped "%.2f",
// rendering $1500.00 here vs $1,500.00 everywhere else). Delegates to core Money.
private fun currency(value: Double): String =
    com.ga.airdrop.core.designsystem.Money.usd(value)

/**
 * Shipping Rates — Figma node 40001567:54206, behavior from
 * FigmaShippingRatesViewController: standard-rates table, In & Out fee card,
 * estimate/other/customs info sections, pinned "Calculate Now" CTA.
 */
@Composable
fun ShippingRatesScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ShippingRatesViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .testTag("shipping-rates-root")
            .background(colors.gray100)
    ) {
        More2InnerHeader(title = "Shipping Rates", onBack = onBack)

        Box(Modifier.weight(1f)) {
            if (state.loading) {
                More2Loading()
            } else {
                val rates = state.rates
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("shipping-rates-scroll")
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = "AirDrop Standard Rates",
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        modifier = Modifier.testTag("shipping-rates-standard-title"),
                    )
                    StandardRatesTable(
                        rows = rates?.airdropStandard?.rates
                            ?.takeIf { it.isNotEmpty() }
                            ?.map { rate ->
                                (rate.weightLbs?.let { formatWeight(it) } ?: "0") to (rate.rateUsd ?: 0.0)
                            }
                            ?: FALLBACK_STANDARD_RATES,
                        overTwentyRate = rates?.airdropStandard?.overTwentyLbRate ?: 3.0,
                    )

                    InOutFeeCard()

                    InfoSection(
                        title = "Calculate your Shipping Estimate",
                        body = "These rates do not include any additional import fees or duty " +
                            "for shipments coming into Jamaica. There is a fuel surcharge of " +
                            "${currency(rates?.additionalFees?.fuelSurcharge ?: 1.5)} " +
                            "applicable to each package.",
                        footer = "Note — Rates are subject to change without prior notice.",
                        tag = "shipping-rates-estimate-card",
                    )
                    InfoSection(
                        title = "Other Fees",
                        body = "Shipments that require special handling are subject to additional " +
                            "charges. Fee for TVs requiring over-packing: $20.00 per package may " +
                            "apply. If package weight exceeds 50 lbs. or the package length + " +
                            "width + height exceeds 100 inches, a US $15.00 oversize handling " +
                            "fee per piece may apply. If the dimensional weight exceeds the " +
                            "actual weight, then the dimensional weight may be used to determine " +
                            "the package’s charges.",
                        footer = null,
                        tag = "shipping-rates-other-fees-card",
                    )
                    InfoSection(
                        title = "Customs Fees",
                        body = "Packages classified by Customs as personal shipments with an " +
                            "assessed value exceeding US $" +
                            String.format(java.util.Locale.US, "%.0f", rates?.additionalFees?.customsThreshold ?: 100.0) +
                            " (C.I.F.) may be subject to Customs Charges. Customers are " +
                            "encouraged to stay informed about the latest Customs regulations, " +
                            "including applicable duties, taxes, and import requirements for " +
                            "goods shipped to Jamaica.",
                        footer = null,
                        tag = "shipping-rates-customs-card",
                    )
                }
            }
        }

        More2BottomBar(verticalPadding = 14.dp) {
            // Swift uses a solid orange radius-10 CTA on this screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("shipping-rates-calculate")
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(BrandPalette.OrangeMain)
                    .clickable { onNavigate(Routes.CALCULATOR) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Calculate Now", style = AirdropType.button, color = BrandPalette.White)
            }
        }
    }
}

private fun formatWeight(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun StandardRatesTable(rows: List<Pair<String, Double>>, overTwentyRate: Double) {
    More2OuterCard(Modifier.testTag("shipping-rates-standard-card")) {
        TableHeader(left = "Package Weight (LBS)", right = "Rates (USD)")
        rows.forEachIndexed { index, (weight, rate) ->
            TableRow(
                left = weight,
                right = currency(rate),
                alt = index % 2 == 1,
                tag = "shipping-rates-standard-row-$index",
            )
        }
        TableRow(
            left = "21 & Up",
            right = "${currency(overTwentyRate)} each additional lbs.",
            alt = rows.size % 2 == 0,
            isLast = true,
            tag = "shipping-rates-standard-row-over-20",
        )
    }
}

@Composable
private fun InOutFeeCard() {
    val colors = AirdropTheme.colors
    More2OuterCard(Modifier.testTag("shipping-rates-inout-card")) {
        Column(Modifier.padding(16.dp)) {
            Text("In & Out Fee", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A handling fee applies if your package arrives at our warehouse and " +
                    "you request to return it to the Shipper, as outlined in the fee table below.",
                style = AirdropType.body2,
                color = colors.textDescription,
            )
            Spacer(Modifier.height(14.dp))
            // RECONCILE: AdditionalFees has no nested in_out_fee breakdown yet;
            // documented RN defaults used until ShippingRates is extended.
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("shipping-rates-inout-table")
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            ) {
                TableHeader(left = "Packages", right = "Rates (USD)")
                TableRow(
                    left = "First Pound",
                    right = currency(5.0),
                    alt = false,
                    tag = "shipping-rates-inout-row-first",
                )
                TableRow(
                    left = "Additional",
                    right = "${currency(0.5)} per pound",
                    alt = true,
                    tag = "shipping-rates-inout-row-additional",
                )
                TableRow(
                    left = "100 lbs. or more",
                    right = currency(50.0),
                    alt = false,
                    isLast = true,
                    tag = "shipping-rates-inout-row-100",
                )
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String, footer: String?, tag: String) {
    val colors = AirdropTheme.colors
    More2OuterCard(Modifier.testTag(tag)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Text(body, style = AirdropType.body2, color = colors.textDescription)
            if (footer != null) {
                Text(footer, style = AirdropType.body2, color = colors.textDescription)
            }
        }
    }
}

@Composable
private fun TableHeader(left: String, right: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(BrandPalette.OrangeMain)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = left,
            style = AirdropType.subtitle2,
            color = BrandPalette.White,
            modifier = Modifier.weight(1f),
        )
        Text(text = right, style = AirdropType.subtitle2, color = BrandPalette.White)
    }
}

@Composable
private fun TableRow(
    left: String,
    right: String,
    alt: Boolean,
    isLast: Boolean = false,
    tag: String? = null,
) {
    val colors = AirdropTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .background(if (alt) colors.gray200 else colors.gray100)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = left,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            Text(text = right, style = AirdropType.body2, color = colors.textDarkTitle)
        }
        if (!isLast) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
        }
    }
}
