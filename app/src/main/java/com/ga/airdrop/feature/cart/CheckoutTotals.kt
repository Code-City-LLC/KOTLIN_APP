package com.ga.airdrop.feature.cart

import java.util.Locale

internal const val DELIVERY_FEE_UNAVAILABLE_REASON =
    "We couldn't get a delivery fee for this address. Go back to Delivery Method " +
        "and reselect your location, then try again."

internal const val CHECKOUT_TOTALS_UNAVAILABLE_REASON =
    "We couldn't calculate this checkout total. Return to your cart and restart checkout."

/** The exact delivery quote frozen when Delivery Method advances. */
internal data class CheckoutDeliveryQuote(
    val isDelivery: Boolean,
    val feeLocal: Double?,
    val feeCurrency: String?,
    val feeUsd: Double?,
)

/**
 * One money breakdown shared by Order Summary and the NCB payment boundary.
 *
 * USD is the canonical item currency. A raw JMD delivery fee remains verbatim;
 * it is never converted to USD and back for display. JMD item/total values are
 * nullable because the app's seed rate is not authoritative.
 */
data class CheckoutTotalsBreakdown(
    val itemsUsd: Double,
    val itemsJmd: Double?,
    val deliveryUsd: Double,
    val deliveryJmd: Double?,
    val totalUsd: Double,
    val totalJmd: Double?,
    val showsDeliveryRow: Boolean,
)

sealed interface CheckoutTotalsResult {
    data class Available(val breakdown: CheckoutTotalsBreakdown) : CheckoutTotalsResult
    data class Unavailable(val reason: String) : CheckoutTotalsResult
}

/** Prevent a last-known seed from becoming a customer-facing or payment rate. */
internal fun verifiedCheckoutExchangeRate(value: Double, verified: Boolean): Double? =
    value.takeIf { verified && it.isFinite() && it > 0.0 }

internal fun checkoutTotalsPermitPayment(
    currency: String,
    result: CheckoutTotalsResult,
): Boolean {
    val breakdown = (result as? CheckoutTotalsResult.Available)?.breakdown ?: return false
    return !currency.equals("JMD", ignoreCase = true) || breakdown.totalJmd != null
}

internal fun resolveCheckoutTotals(
    lines: List<CartStore.CartLine>,
    flow: CheckoutFlow?,
    exchangeUsdToJmd: Double,
    exchangeRateVerified: Boolean,
): CheckoutTotalsResult {
    if (flow == null) return CheckoutTotalsResult.Unavailable(CHECKOUT_TOTALS_UNAVAILABLE_REASON)
    return resolveCheckoutTotals(
        lines = lines,
        quote = CheckoutDeliveryQuote(
            isDelivery = flow.deliveryMode.equals("delivery", ignoreCase = true),
            feeLocal = flow.deliveryFee,
            feeCurrency = flow.deliveryFeeCurrency,
            feeUsd = flow.deliveryFeeUsd,
        ),
        verifiedExchangeUsdToJmd = verifiedCheckoutExchangeRate(
            exchangeUsdToJmd,
            exchangeRateVerified,
        ),
    )
}

internal fun resolveCheckoutTotals(
    lines: List<CartStore.CartLine>,
    quote: CheckoutDeliveryQuote,
    verifiedExchangeUsdToJmd: Double?,
): CheckoutTotalsResult {
    if (lines.isEmpty() || lines.any {
            it.qty <= 0 || !it.priceUsd.isFinite() || it.priceUsd < 0.0
        }
    ) {
        return CheckoutTotalsResult.Unavailable(CHECKOUT_TOTALS_UNAVAILABLE_REASON)
    }
    if (verifiedExchangeUsdToJmd != null &&
        (!verifiedExchangeUsdToJmd.isFinite() || verifiedExchangeUsdToJmd <= 0.0)
    ) {
        return CheckoutTotalsResult.Unavailable(CHECKOUT_TOTALS_UNAVAILABLE_REASON)
    }

    val itemsUsd = lines.sumOf { it.priceUsd * it.qty }
    if (!itemsUsd.isFinite() || itemsUsd < 0.0) {
        return CheckoutTotalsResult.Unavailable(CHECKOUT_TOTALS_UNAVAILABLE_REASON)
    }
    val itemsJmd = verifiedExchangeUsdToJmd
        ?.let { rate -> (itemsUsd * rate).takeIf(Double::isFinite) }

    val deliveryUsd: Double
    val deliveryJmd: Double?
    if (!quote.isDelivery) {
        deliveryUsd = 0.0
        deliveryJmd = 0.0
    } else {
        if (quote.feeUsd != null && (!quote.feeUsd.isFinite() || quote.feeUsd < 0.0)) {
            return CheckoutTotalsResult.Unavailable(DELIVERY_FEE_UNAVAILABLE_REASON)
        }
        if (quote.feeLocal != null && (!quote.feeLocal.isFinite() || quote.feeLocal < 0.0)) {
            return CheckoutTotalsResult.Unavailable(DELIVERY_FEE_UNAVAILABLE_REASON)
        }

        val feeCurrency = quote.feeCurrency?.trim()?.uppercase(Locale.US)
        deliveryUsd = quote.feeUsd ?: if (
            feeCurrency == "JMD" && quote.feeLocal != null && verifiedExchangeUsdToJmd != null
        ) {
            quote.feeLocal / verifiedExchangeUsdToJmd
        } else {
            return CheckoutTotalsResult.Unavailable(DELIVERY_FEE_UNAVAILABLE_REASON)
        }
        if (!deliveryUsd.isFinite() || deliveryUsd < 0.0) {
            return CheckoutTotalsResult.Unavailable(DELIVERY_FEE_UNAVAILABLE_REASON)
        }

        deliveryJmd = when {
            feeCurrency == "JMD" && quote.feeLocal != null -> quote.feeLocal
            verifiedExchangeUsdToJmd != null ->
                (deliveryUsd * verifiedExchangeUsdToJmd).takeIf(Double::isFinite)
            else -> null
        }
    }

    val totalUsd = itemsUsd + deliveryUsd
    if (!totalUsd.isFinite()) {
        return CheckoutTotalsResult.Unavailable(CHECKOUT_TOTALS_UNAVAILABLE_REASON)
    }
    val totalJmd = if (itemsJmd != null && deliveryJmd != null) {
        (itemsJmd + deliveryJmd).takeIf(Double::isFinite)
    } else {
        null
    }
    return CheckoutTotalsResult.Available(
        CheckoutTotalsBreakdown(
            itemsUsd = itemsUsd,
            itemsJmd = itemsJmd,
            deliveryUsd = deliveryUsd,
            deliveryJmd = deliveryJmd,
            totalUsd = totalUsd,
            totalJmd = totalJmd,
            showsDeliveryRow = deliveryUsd > 0.0,
        ),
    )
}

/** Format an already-resolved pair without re-converting either amount. */
internal fun formatCheckoutMoneyPair(usd: Double, jmd: Double?): String {
    val jmdText = jmd?.takeIf(Double::isFinite)
        ?.let { String.format(Locale.US, "%,.2f", it) }
        ?: "—"
    val usdText = usd.takeIf(Double::isFinite)
        ?.let { String.format(Locale.US, "%,.2f", it) }
        ?: "—"
    return "JMD $jmdText / USD $usdText"
}
