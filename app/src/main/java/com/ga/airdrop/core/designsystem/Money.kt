package com.ga.airdrop.core.designsystem

import java.util.Locale

/**
 * The ONE place money strings are formatted. Every feature-level formatter
 * (ShopComponents.formatUsd, CalculatorUi.formatPrice, ShipmentsFormat.money,
 * formatCalcMoney, formatAmount, …) delegates here so the 8 scattered copies
 * can never drift apart again (the drift class already produced two real bugs:
 * ShippingRates rendering $1500.00 beside screens rendering $1,500.00, and the
 * calculator quoting a different offline JMD rate than every other screen).
 *
 * TWO deliberate styles exist because SWIFT has two — both are parity, keep
 * them distinct:
 *  - GROUPED ("1,234.56"): hub/summary surfaces (Swift NumberFormatter with
 *    grouping — ShipmentsFormat.money, shop cards, calculator results).
 *  - PLAIN ("1234.56"): Swift `String(format: "%.2f")` surfaces — the cart
 *    line prices/total (FigmaCartViewController:1242,1158) and payment-detail
 *    rows. Also mandatory anywhere a value back-fills a text INPUT (grouping
 *    would break parsing).
 */
object Money {

    /** "1,234.56" — grouped, always 2 fraction digits. */
    fun grouped(value: Double): String = String.format(Locale.US, "%,.2f", value)

    /** "1234.56" — Swift String(format:) parity; also for input back-fill. */
    fun plain(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** "$1,234.56" */
    fun usd(value: Double): String = "$" + grouped(value)

    /** "USD 1,234.56" — grouped label style (shipments hub). */
    fun usdLabel(value: Double): String = "USD " + grouped(value)

    /** "USD 1234.56" — Swift cart/payment-detail plain label style. */
    fun usdLabelPlain(value: Double): String = "USD " + plain(value)

    /** "JA$1,234.56" */
    fun jmd(value: Double): String = "JA$" + grouped(value)
}
