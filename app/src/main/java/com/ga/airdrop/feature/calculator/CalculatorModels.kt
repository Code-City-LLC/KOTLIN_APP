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
        label = "Airdrop",
        apiValue = "airdrop_standard",
        info = "2 to 3 business days after items are delivered to our warehouse.",
    ),

    // Swift FigmaCalculatorViewController.swift:26 — "2 to 4 weeks…" (Swift wins over Figma 40001464:30381)
    SEADROP(
        label = "Seadrop",
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

/**
 * A customs duty rate — the catalogue the Shipping Calculator actually needs.
 *
 * ⚠️ THIS USED TO BE `CalcProduct`, READ FROM THE AUCTION CATALOGUE, WITH A
 * PRICE. That was the wrong business object, not a broken query: the field
 * searched `GET /products?in_stock=1` and rendered a shop price next to each
 * row, so the customer picked a *product for sale* while the calculator needed
 * a *duty classification*. The selected id then went nowhere, because
 * `calculateShipment` had no parameter to carry it.
 *
 * There is deliberately NO price here. `/custom-duty-rates` returns
 * `id, item_name, duty_percentage` and nothing else, and the calculator must
 * never fabricate or autofill money it was not given.
 *
 * Root-caused by @Codex-LavenderGlen (ORC #99765) and @Codex-MobileReleaseQC
 * (#99763), independently and to the same lines.
 */
data class CalcDutyRate(
    val id: Int,
    val itemName: String,
    val dutyPercentage: Double?,
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
    /** Non-zero only when the delivery address was flagged bad. */
    val badAddressFee: Double? = null,
    /** SeaDrop only. */
    val tariff: Double? = null,
    /** SeaDrop only — a real 30.00 line the app was dropping. */
    val billOfLadingProcessing: Double? = null,
)

/**
 * Everything the results screens need — the calculator screen builds this and
 * publishes it on the graph-scoped [CalculatorViewModel] (Android counterpart
 * of the Swift results-VC initializer arguments). `live` is the server's own
 * breakdown from POST /shipping/calculate and is always present — the screen
 * is never reached without it.
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
    val badAddressFee: Double? = null,
    val tariff: Double? = null,
    val billOfLadingProcessing: Double? = null,
)

/**
 * Port of FigmaCalculatorResultsViewController.resolveCharges(): live API
 * payload for SeaDrop/Express, offline Airdrop Standard formula otherwise.
 */
fun resolveCharges(result: CalculationResult): Charges {
    // `live` is now always present: every method goes through
    // POST /shipping/calculate and a failure raises an error instead of
    // pushing this screen. The client-side ShippingCalculator fallback that
    // used to sit here is gone — it under-quoted by up to 86% because it
    // ignored the package count, and it rendered indistinguishably from a
    // real quote.
    val live = result.live ?: return Charges(
        totalWeightLbs = result.weightLbs,
        invoiceAmount = result.invoiceUsd,
    )
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
        badAddressFee = live.badAddressFee,
        tariff = live.tariff,
        billOfLadingProcessing = live.billOfLadingProcessing,
    )
}
