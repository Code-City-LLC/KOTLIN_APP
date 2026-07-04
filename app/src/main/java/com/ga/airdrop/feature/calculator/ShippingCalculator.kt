package com.ga.airdrop.feature.calculator

import kotlin.math.ceil

/**
 * Offline Airdrop Standard shipping formula — 1:1 port of
 * SWIFT_APP/Airdrop/ShippingCalculator.swift (itself validated against RN
 * `calculateFreight`/`calculateInsurance`/`calculateCIF` in AD RN 2026
 * Utils.ts). Do NOT change the rates here without revalidating against the
 * live API; the Swift file documents a historical fuel/total bug that this
 * port already fixes (fuel = 1.0, grand total includes the invoice).
 */
object ShippingCalculator {

    /** All values in USD. */
    data class AirdropStandardResult(
        val costValue: Double,      // invoice total in USD
        val freightValue: Double,
        val insuranceValue: Double,
        val fuelValue: Double,
        val cifValue: Double,       // freight + insurance + invoice
        val grandTotal: Double,     // cif + fuel
        val airdropValue: Double,   // grandTotal - invoice (markup over goods)
    )

    class InputException(message: String) : IllegalArgumentException(message)

    /**
     * @param weightLbs package weight in pounds, must be > 0
     * @param invoiceUsd invoice / product price in USD, must be > 0
     * @throws InputException when either input is non-positive
     */
    fun airdropStandard(weightLbs: Double, invoiceUsd: Double): AirdropStandardResult {
        if (weightLbs <= 0) throw InputException("Weight must be greater than zero.")
        if (invoiceUsd <= 0) throw InputException("Invoice amount must be greater than zero.")

        val fuelValue = 1.0

        // Insurance: $1.50 per $100 of invoice value, rounded UP to nearest $100.
        // React: `1.5 * Math.ceil(invoiceTotalUs / 100)`
        val insurance = 1.5 * ceil(invoiceUsd / 100.0)

        // Freight: tiered by weight.
        val freight = when {
            weightLbs <= 0.5 -> 3.0
            weightLbs < 2 -> 6.0
            else -> 3 + (weightLbs * 3)
        }

        val cif = freight + insurance + invoiceUsd
        val grandTotal = freight + insurance + invoiceUsd + fuelValue
        val airdrop = grandTotal - invoiceUsd

        return AirdropStandardResult(
            costValue = invoiceUsd,
            freightValue = freight,
            insuranceValue = insurance,
            fuelValue = fuelValue,
            cifValue = cif,
            grandTotal = grandTotal,
            airdropValue = airdrop,
        )
    }
}
