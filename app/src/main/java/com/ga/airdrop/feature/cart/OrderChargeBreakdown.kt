package com.ga.airdrop.feature.cart

import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.Serializable

/**
 * Package-detail values captured for one shipment row in the exact checkout flow.
 *
 * Auction/sale rows never receive this model. Values are copied from
 * `GET /packages/{id}` without deriving or filling absent backend fields.
 */
@Serializable
data class CheckoutShipmentChargeSnapshot(
    val cartKey: CartStore.CartLineKey,
    val packageId: Int,
    val declaredValueUsd: Double? = null,
    val additionalCharges: Map<String, Double> = emptyMap(),
    val additionalChargesTotalUsd: Double? = null,
    val exchangeRateUsdToJmd: Double? = null,
)

internal data class CheckoutChargeCaptureIdentity(
    val flowId: String,
    val ownerSessionId: String,
    val ownerAccountId: Int?,
    val cartKeys: List<CartStore.CartLineKey>,
    val packageIds: List<Int>,
)

internal fun CheckoutFlow.chargeCaptureIdentity(): CheckoutChargeCaptureIdentity =
    CheckoutChargeCaptureIdentity(
        flowId = id,
        ownerSessionId = ownerSessionId,
        ownerAccountId = ownerAccountId,
        cartKeys = cartKeys,
        packageIds = packageIds,
    )

enum class OrderChargeKind {
    FREIGHT,
    INSURANCE,
    FUEL,
    CUSTOMS_DUTY,
    GCT,
    OTHER,
}

enum class ShipmentChargeSource {
    ITEMIZED_BACKEND,
    BACKEND_TOTAL_FALLBACK,
    CAPTURED_TOTAL_FALLBACK,
}

data class OrderChargeRow(
    val label: String,
    val amountUsd: Double,
    val kind: OrderChargeKind,
)

data class ShipmentOrderCharge(
    val cartKey: CartStore.CartLineKey,
    val packageId: Int,
    val title: String,
    val rows: List<OrderChargeRow>,
    /** Canonical checkout amount; package-detail disclosure never mutates it. */
    val capturedSubtotalUsd: Double,
    /** Sum of the visible backend rows (or the transparent detail fallback). */
    val disclosedSubtotalUsd: Double,
    val source: ShipmentChargeSource,
    val backendTotalUsd: Double?,
    val itemizedTotalDiffersFromBackend: Boolean,
    val disclosureDiffersFromCaptured: Boolean,
    val cif: ShipmentCifBreakdown?,
)

data class SaleOrderCharge(
    val cartKey: CartStore.CartLineKey,
    val title: String,
    val subtotalUsd: Double,
)

data class ShipmentCifBreakdown(
    val cartKey: CartStore.CartLineKey,
    val packageId: Int,
    val title: String,
    val invoiceAmountUsd: Double,
    val insuranceUsd: Double,
    val freightUsd: Double,
    val totalUsd: Double,
    val exchangeRateUsdToJmd: Double?,
) {
    fun toJmd(amountUsd: Double): Double? = exchangeRateUsdToJmd?.let { amountUsd * it }
}

/** Pure order-summary projection. No network, store, or Compose dependencies. */
data class OrderChargeBreakdown(
    val shipments: List<ShipmentOrderCharge>,
    val sales: List<SaleOrderCharge>,
    val shipmentSubtotalUsd: Double,
    val saleSubtotalUsd: Double,
    val deliveryFeeUsd: Double?,
    val paymentCurrency: String,
    val checkoutExchangeRateUsdToJmd: Double?,
    val calculatedTotalUsd: Double,
    val displayTotal: Double,
    val canonicalFlowAvailable: Boolean,
    val shipmentHydrationComplete: Boolean,
) {
    val hasShipments: Boolean get() = shipments.isNotEmpty()
    val hasSales: Boolean get() = sales.isNotEmpty()
    val cifs: List<ShipmentCifBreakdown> get() = shipments.mapNotNull(ShipmentOrderCharge::cif)

    companion object {
        fun calculate(
            lines: List<CartStore.CartLine>,
            snapshots: List<CheckoutShipmentChargeSnapshot>,
            paymentCurrency: String,
            checkoutExchangeRateUsdToJmd: Double?,
            deliveryFee: Double?,
            deliveryFeeCurrency: String?,
            canonicalFlowAvailable: Boolean,
            fallbackDisplayTotal: Double,
            unavailableShipmentKeys: Set<CartStore.CartLineKey> = emptySet(),
        ): OrderChargeBreakdown {
            val validRate = checkoutExchangeRateUsdToJmd.validPositiveOrNull()
            val normalizedCurrency = paymentCurrency.trim().uppercase(Locale.US)
                .takeIf { it == "USD" || it == "JMD" }
                ?: "USD"
            val snapshotByKey = snapshots
                .filter { it.cartKey.kind == CartStore.CartLineKind.PACKAGE }
                .associateBy(CheckoutShipmentChargeSnapshot::cartKey)

            val shipmentLines = lines
                .filter { it.resolvedKind == CartStore.CartLineKind.PACKAGE }
            val rawShipments = shipmentLines
                .map { line -> shipmentBreakdown(line, snapshotByKey[line.key]) }
            val shipmentHydrationComplete = shipmentLines.isNotEmpty() && shipmentLines.all { line ->
                line.key !in unavailableShipmentKeys && snapshotByKey[line.key]?.let { snapshot ->
                    snapshot.cartKey == line.key && snapshot.packageId == line.packageId
                } == true
            }
            // Swift parity: a CIF disclosure is complete-order information.
            // Keep every per-package candidate hidden until all shipment
            // package-detail requests in this capture have succeeded.
            val shipments = if (shipmentHydrationComplete) {
                rawShipments
            } else {
                rawShipments.map { it.copy(cif = null) }
            }
            val sales = lines
                .filter { it.resolvedKind == CartStore.CartLineKind.AUCTION }
                .map { line ->
                    SaleOrderCharge(
                        cartKey = line.key,
                        title = line.title,
                        subtotalUsd = line.capturedSubtotalUsd(),
                    )
                }

            val normalizedDelivery = normalizeDeliveryFeeUsd(
                amount = deliveryFee,
                currency = deliveryFeeCurrency,
                exchangeRateUsdToJmd = validRate,
            )
            val shipmentSubtotal = shipments.sumOf(ShipmentOrderCharge::capturedSubtotalUsd)
            val saleSubtotal = sales.sumOf(SaleOrderCharge::subtotalUsd)
            val calculatedUsd = shipmentSubtotal + saleSubtotal + (normalizedDelivery ?: 0.0)
            val calculatedDisplay = when (normalizedCurrency) {
                "JMD" -> validRate?.let { calculatedUsd * it }
                else -> calculatedUsd
            }
            val capturedDisplayTotal = fallbackDisplayTotal.takeIf {
                it.isFinite() && it >= 0.0
            } ?: calculatedDisplay ?: 0.0

            return OrderChargeBreakdown(
                shipments = shipments,
                sales = sales,
                shipmentSubtotalUsd = shipmentSubtotal,
                saleSubtotalUsd = saleSubtotal,
                deliveryFeeUsd = normalizedDelivery,
                paymentCurrency = normalizedCurrency,
                checkoutExchangeRateUsdToJmd = validRate,
                calculatedTotalUsd = calculatedUsd,
                // `fallbackDisplayTotal` is the checkout-captured payment
                // amount supplied by the merged checkout owner. Package
                // detail rows are disclosure only and cannot change it.
                displayTotal = capturedDisplayTotal,
                canonicalFlowAvailable = canonicalFlowAvailable,
                shipmentHydrationComplete = shipmentHydrationComplete,
            )
        }

        private fun shipmentBreakdown(
            line: CartStore.CartLine,
            candidate: CheckoutShipmentChargeSnapshot?,
        ): ShipmentOrderCharge {
            val snapshot = candidate?.takeIf {
                it.cartKey == line.key && it.packageId == line.packageId
            }
            val itemizedRows = snapshot?.additionalCharges
                .orEmpty()
                .entries
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
                .map { (name, amount) ->
                    OrderChargeRow(
                        label = name,
                        amountUsd = amount,
                        kind = classifyOrderCharge(name),
                    )
                }
            val source: ShipmentChargeSource
            val rows: List<OrderChargeRow>
            when {
                itemizedRows.isNotEmpty() -> {
                    source = ShipmentChargeSource.ITEMIZED_BACKEND
                    rows = itemizedRows
                }
                snapshot?.additionalChargesTotalUsd != null -> {
                    source = ShipmentChargeSource.BACKEND_TOTAL_FALLBACK
                    rows = listOf(
                        OrderChargeRow(
                            label = "Package charges (breakdown unavailable)",
                            amountUsd = snapshot.additionalChargesTotalUsd,
                            kind = OrderChargeKind.OTHER,
                        ),
                    )
                }
                else -> {
                    source = ShipmentChargeSource.CAPTURED_TOTAL_FALLBACK
                    rows = listOf(
                        OrderChargeRow(
                            label = "Captured package total (details unavailable)",
                            amountUsd = line.capturedSubtotalUsd(),
                            kind = OrderChargeKind.OTHER,
                        ),
                    )
                }
            }
            val disclosedSubtotal = rows.sumOf(OrderChargeRow::amountUsd)
            val capturedSubtotal = line.capturedSubtotalUsd()
            val backendTotal = snapshot?.additionalChargesTotalUsd
            val differs = source == ShipmentChargeSource.ITEMIZED_BACKEND &&
                backendTotal != null && abs(disclosedSubtotal - backendTotal) > MONEY_EPSILON
            val cif = snapshot?.declaredValueUsd?.let { invoice ->
                val insurance = itemizedRows
                    .filter { it.kind == OrderChargeKind.INSURANCE }
                    .sumOf(OrderChargeRow::amountUsd)
                val freight = itemizedRows
                    .filter { it.kind == OrderChargeKind.FREIGHT }
                    .sumOf(OrderChargeRow::amountUsd)
                ShipmentCifBreakdown(
                    cartKey = line.key,
                    packageId = snapshot.packageId,
                    title = line.title,
                    invoiceAmountUsd = invoice,
                    insuranceUsd = insurance,
                    freightUsd = freight,
                    totalUsd = invoice + insurance + freight,
                    exchangeRateUsdToJmd = snapshot.exchangeRateUsdToJmd.validPositiveOrNull(),
                )
            }
            return ShipmentOrderCharge(
                cartKey = line.key,
                packageId = line.packageId ?: line.id,
                title = line.title,
                rows = rows,
                capturedSubtotalUsd = capturedSubtotal,
                disclosedSubtotalUsd = disclosedSubtotal,
                source = source,
                backendTotalUsd = backendTotal,
                itemizedTotalDiffersFromBackend = differs,
                disclosureDiffersFromCaptured =
                    abs(disclosedSubtotal - capturedSubtotal) > MONEY_EPSILON,
                cif = cif,
            )
        }

        private fun normalizeDeliveryFeeUsd(
            amount: Double?,
            currency: String?,
            exchangeRateUsdToJmd: Double?,
        ): Double? {
            val validAmount = amount?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            return when (currency?.trim()?.uppercase(Locale.US)) {
                "USD" -> validAmount
                "JMD" -> exchangeRateUsdToJmd?.let { validAmount / it }
                else -> null
            }
        }
    }
}

fun classifyOrderCharge(name: String): OrderChargeKind {
    val normalized = name.trim().lowercase(Locale.US)
    return when {
        "insurance" in normalized -> OrderChargeKind.INSURANCE
        "freight" in normalized -> OrderChargeKind.FREIGHT
        "fuel" in normalized -> OrderChargeKind.FUEL
        "gct" in normalized || "general consumption tax" in normalized -> OrderChargeKind.GCT
        "custom" in normalized && "duty" in normalized -> OrderChargeKind.CUSTOMS_DUTY
        else -> OrderChargeKind.OTHER
    }
}

private fun CartStore.CartLine.capturedSubtotalUsd(): Double =
    (priceUsd * qty).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

private fun Double?.validPositiveOrNull(): Double? =
    this?.takeIf { it.isFinite() && it > 0.0 }

private const val MONEY_EPSILON = 0.005
