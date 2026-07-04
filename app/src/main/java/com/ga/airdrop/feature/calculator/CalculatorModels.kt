package com.ga.airdrop.feature.calculator

/**
 * Domain models for the Shipping Calculator flow. Behavior mirrors
 * FigmaCalculatorViewController / FigmaCalculatorResultsViewController.
 */

/**
 * RN canonical methods (Translations.ts / CalculatorView). `apiValue` is the
 * `shipping_method` string POSTed to /shipping/calculate — see Swift
 * `ShippingMethod.apiValue`.
 */
enum class ShippingMethod(val label: String, val apiValue: String, val info: String) {
    // Figma 40001464:29102 info copy — "2 to 3 business days…"
    STANDARD(
        label = "AirDrop",
        apiValue = "airdrop_standard",
        info = "2 to 3 business days after items are delivered to our warehouse.",
    ),

    // Swift FigmaCalculatorViewController.swift:26 — "2 to 4 weeks…" (Swift wins over Figma 40001464:30381)
    SEADROP(
        label = "SeaDrop",
        apiValue = "seadrop_standard",
        info = "2 to 4 weeks after items are delivered to our warehouse.",
    ),
    EXPRESS(
        label = "Express",
        apiValue = "airdrop_express",
        info = "1 to 2 business days after items are delivered to our warehouse.",
    ),
}

// RN canonical units (CalculatorView/index.tsx `units`).
enum class LengthUnit(val label: String) { INCH("Inch"), FT("ft") }
enum class WeightUnit(val label: String) { LBS("lbs"), KG("kg") }

/** Product row surfaced by the calculator search (Swift AuctionProduct subset). */
data class CalcProduct(
    val id: Int,
    val title: String,
    val displayPrice: String,
)

/**
 * Live /shipping/calculate payload — Swift `AirdropAPI.ShipmentCalculation`
 * ({data:{shipping_method, breakdown{…}, calculations{…}}}).
 */
data class ShipmentCalculation(
    val shippingMethod: String?,
    val freight: Double,
    val insurance: Double,
    val fuelSurcharge: Double,
    val airdropCharges: Double,
    val customsDuty: Double,
    val totalWithDuty: Double,
    val cifValue: Double,
    val totalWeightLbs: Double?,
)

/**
 * Everything the results screens need — the calculator screen builds this and
 * publishes it on the graph-scoped [CalculatorViewModel] (Android counterpart
 * of the Swift results-VC initializer arguments). `live == null` means
 * AirDrop Standard: the results screen re-runs [ShippingCalculator] itself.
 */
data class CalculationResult(
    val method: ShippingMethod,
    val productName: String?,
    val weightLbs: Double,
    val weightUnit: WeightUnit,
    val invoiceUsd: Double,
    val lengthIn: Double?,
    val widthIn: Double?,
    val heightIn: Double?,
    val live: ShipmentCalculation?,
)

/** Resolved breakdown used by the results + government-charges UI. */
data class Charges(
    val totalWeightLbs: Double = 0.0,
    val invoiceAmount: Double = 0.0,
    val cifValue: Double = 0.0,
    val insurance: Double = 0.0,
    val freight: Double = 0.0,
    val fuelSurcharge: Double = 0.0,
    val customsDuty: Double = 0.0,
    val airdropCharges: Double = 0.0,
    val totalWithDuty: Double = 0.0,
)

/**
 * Port of FigmaCalculatorResultsViewController.resolveCharges(): live API
 * payload for SeaDrop/Express, offline Airdrop Standard formula otherwise.
 */
fun resolveCharges(result: CalculationResult): Charges {
    val live = result.live
    if (live != null) {
        return Charges(
            totalWeightLbs = live.totalWeightLbs ?: result.weightLbs,
            invoiceAmount = result.invoiceUsd,
            cifValue = live.cifValue,
            insurance = live.insurance,
            freight = live.freight,
            fuelSurcharge = live.fuelSurcharge,
            customsDuty = live.customsDuty,
            airdropCharges = live.airdropCharges,
            totalWithDuty = live.totalWithDuty,
        )
    }
    val safeWeight = maxOf(0.5, result.weightLbs)
    val safeInvoice = maxOf(0.01, result.invoiceUsd)
    return runCatching { ShippingCalculator.airdropStandard(safeWeight, safeInvoice) }
        .map { r ->
            Charges(
                totalWeightLbs = safeWeight,
                invoiceAmount = r.costValue,
                cifValue = r.cifValue,
                insurance = r.insuranceValue,
                freight = r.freightValue,
                fuelSurcharge = r.fuelValue,
                customsDuty = 0.0,
                airdropCharges = r.airdropValue,
                totalWithDuty = r.grandTotal,
            )
        }
        .getOrDefault(Charges(totalWeightLbs = safeWeight, invoiceAmount = result.invoiceUsd))
}
