package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderChargeBreakdownTest {

    @Test
    fun `shipment CIF uses invoice insurance and freight only`() {
        val shipment = packageLine(id = 71, priceUsd = 25.0)
        val snapshot = snapshot(
            shipment,
            declaredValueUsd = 100.0,
            charges = linkedMapOf(
                "Freight" to 10.0,
                "Insurance" to 2.0,
                "Fuel" to 3.0,
                "Customs Duty" to 4.0,
                "Customs GCT" to 5.0,
                "Handling" to 1.0,
            ),
            totalUsd = 25.0,
            rate = 160.0,
        )

        val result = calculate(listOf(shipment), listOf(snapshot))

        assertEquals(25.0, result.shipmentSubtotalUsd, 0.0)
        assertEquals(0.0, result.saleSubtotalUsd, 0.0)
        assertEquals(25.0, result.displayTotal, 0.0)
        val cif = requireNotNull(result.shipments.single().cif)
        assertEquals(100.0, cif.invoiceAmountUsd, 0.0)
        assertEquals(2.0, cif.insuranceUsd, 0.0)
        assertEquals(10.0, cif.freightUsd, 0.0)
        assertEquals(112.0, cif.totalUsd, 0.0)
        assertEquals(17_920.0, requireNotNull(cif.toJmd(cif.totalUsd)), 0.0)
        assertEquals(
            setOf(
                OrderChargeKind.FREIGHT,
                OrderChargeKind.INSURANCE,
                OrderChargeKind.FUEL,
                OrderChargeKind.CUSTOMS_DUTY,
                OrderChargeKind.GCT,
                OrderChargeKind.OTHER,
            ),
            result.shipments.single().rows.map(OrderChargeRow::kind).toSet(),
        )
    }

    @Test
    fun `sale-only order has a separate sale subtotal and no CIF`() {
        val sale = saleLine(id = 81, priceUsd = 15.0, qty = 2)
        val invalidSaleSnapshot = CheckoutShipmentChargeSnapshot(
            cartKey = sale.key,
            packageId = requireNotNull(sale.packageId),
            declaredValueUsd = 500.0,
            additionalCharges = mapOf("Freight" to 99.0, "Customs Duty" to 88.0),
            exchangeRateUsdToJmd = 160.0,
        )

        val result = calculate(listOf(sale), listOf(invalidSaleSnapshot))

        assertFalse(result.hasShipments)
        assertTrue(result.hasSales)
        assertEquals(30.0, result.saleSubtotalUsd, 0.0)
        assertEquals(30.0, result.displayTotal, 0.0)
        assertTrue(result.cifs.isEmpty())
    }

    @Test
    fun `mixed order keeps shipment charges and sale subtotal separate`() {
        val shipment = packageLine(id = 91, priceUsd = 5.0)
        val sale = saleLine(id = 92, priceUsd = 20.0, qty = 2)
        val result = OrderChargeBreakdown.calculate(
            lines = listOf(shipment, sale),
            snapshots = listOf(
                snapshot(
                    shipment,
                    declaredValueUsd = 75.0,
                    charges = mapOf("Freight" to 5.0),
                    totalUsd = 5.0,
                ),
            ),
            paymentCurrency = "USD",
            checkoutExchangeRateUsdToJmd = 160.0,
            deliveryFee = 10.0,
            deliveryFeeCurrency = "USD",
            canonicalFlowAvailable = true,
            fallbackDisplayTotal = 55.0,
        )

        assertEquals(5.0, result.shipmentSubtotalUsd, 0.0)
        assertEquals(40.0, result.saleSubtotalUsd, 0.0)
        assertEquals(10.0, result.deliveryFeeUsd ?: error("missing delivery"), 0.0)
        assertEquals(55.0, result.displayTotal, 0.0)
        assertEquals("Sale 92", result.sales.single().title)
        assertEquals(listOf("Freight"), result.shipments.single().rows.map(OrderChargeRow::label))
        assertTrue(result.canonicalFlowAvailable)
    }

    @Test
    fun `missing itemization uses backend total then captured total transparently`() {
        val withBackendTotal = packageLine(id = 101, priceUsd = 50.0)
        val withoutDetails = packageLine(id = 102, priceUsd = 7.0, qty = 2)
        val result = calculate(
            lines = listOf(withBackendTotal, withoutDetails),
            snapshots = listOf(
                snapshot(
                    withBackendTotal,
                    charges = emptyMap(),
                    totalUsd = 18.0,
                ),
            ),
        )

        assertEquals(64.0, result.shipmentSubtotalUsd, 0.0)
        assertEquals(18.0, result.shipments[0].disclosedSubtotalUsd, 0.0)
        assertEquals(14.0, result.shipments[1].disclosedSubtotalUsd, 0.0)
        assertEquals(
            ShipmentChargeSource.BACKEND_TOTAL_FALLBACK,
            result.shipments[0].source,
        )
        assertEquals(
            "Package charges (breakdown unavailable)",
            result.shipments[0].rows.single().label,
        )
        assertEquals(
            ShipmentChargeSource.CAPTURED_TOTAL_FALLBACK,
            result.shipments[1].source,
        )
        assertEquals(
            "Captured package total (details unavailable)",
            result.shipments[1].rows.single().label,
        )
        assertNull(result.shipments[0].cif)
    }

    @Test
    fun `itemized mismatch cannot mutate captured shipment or payment total`() {
        val shipment = packageLine(id = 111, priceUsd = 50.0)
        val result = calculate(
            listOf(shipment),
            listOf(snapshot(shipment, charges = mapOf("Freight" to 9.0), totalUsd = 12.0)),
        )

        val item = result.shipments.single()
        assertEquals(50.0, result.shipmentSubtotalUsd, 0.0)
        assertEquals(50.0, result.displayTotal, 0.0)
        assertEquals(50.0, item.capturedSubtotalUsd, 0.0)
        assertEquals(9.0, item.disclosedSubtotalUsd, 0.0)
        assertEquals(12.0, item.backendTotalUsd ?: error("missing backend total"), 0.0)
        assertTrue(item.itemizedTotalDiffersFromBackend)
        assertTrue(item.disclosureDiffersFromCaptured)
        assertEquals(1, item.rows.size)
    }

    @Test
    fun `CIF stays hidden until every shipment detail succeeds`() {
        val first = packageLine(id = 121, priceUsd = 20.0)
        val second = packageLine(id = 122, priceUsd = 30.0)
        val sale = saleLine(id = 123, priceUsd = 40.0)
        val firstSnapshot = snapshot(
            first,
            declaredValueUsd = 100.0,
            charges = mapOf("Insurance" to 2.0, "Freight" to 8.0),
            totalUsd = 10.0,
            rate = 160.0,
        )

        val partial = calculate(listOf(first, second, sale), listOf(firstSnapshot))

        assertFalse(partial.shipmentHydrationComplete)
        assertTrue(partial.cifs.isEmpty())
        assertNull(partial.shipments.first().cif)

        val secondSnapshot = snapshot(
            second,
            declaredValueUsd = 50.0,
            charges = mapOf("Freight" to 5.0, "Fuel" to 1.0),
            totalUsd = 6.0,
            rate = 160.0,
        )
        val failedRefresh = calculate(
            lines = listOf(first, second, sale),
            snapshots = listOf(firstSnapshot, secondSnapshot),
            unavailableShipmentKeys = setOf(second.key),
        )
        assertFalse(failedRefresh.shipmentHydrationComplete)
        assertTrue(failedRefresh.cifs.isEmpty())

        val complete = calculate(
            listOf(first, second, sale),
            listOf(firstSnapshot, secondSnapshot),
        )

        assertTrue(complete.shipmentHydrationComplete)
        assertEquals(2, complete.cifs.size)
        assertEquals(setOf(first.key, second.key), complete.cifs.map { it.cartKey }.toSet())
        assertTrue(complete.cifs.none { it.cartKey == sale.key })
    }

    private fun calculate(
        lines: List<CartStore.CartLine>,
        snapshots: List<CheckoutShipmentChargeSnapshot>,
        unavailableShipmentKeys: Set<CartStore.CartLineKey> = emptySet(),
    ): OrderChargeBreakdown = OrderChargeBreakdown.calculate(
        lines = lines,
        snapshots = snapshots,
        paymentCurrency = "USD",
        checkoutExchangeRateUsdToJmd = 160.0,
        deliveryFee = null,
        deliveryFeeCurrency = null,
        canonicalFlowAvailable = true,
        fallbackDisplayTotal = lines.sumOf { it.priceUsd * it.qty },
        unavailableShipmentKeys = unavailableShipmentKeys,
    )

    private fun packageLine(
        id: Int,
        priceUsd: Double,
        qty: Int = 1,
    ) = CartStore.CartLine(
        id = id,
        packageId = id,
        title = "Package $id",
        qty = qty,
        priceUsd = priceUsd,
        kind = CartStore.CartLineKind.PACKAGE,
        statusCode = 7,
        serverConfirmed = true,
    )

    private fun saleLine(
        id: Int,
        priceUsd: Double,
        qty: Int = 1,
    ) = CartStore.CartLine(
        id = id,
        packageId = id + 1_000,
        title = "Sale $id",
        qty = qty,
        priceUsd = priceUsd,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )

    private fun snapshot(
        line: CartStore.CartLine,
        declaredValueUsd: Double? = null,
        charges: Map<String, Double>,
        totalUsd: Double? = null,
        rate: Double? = null,
    ) = CheckoutShipmentChargeSnapshot(
        cartKey = line.key,
        packageId = requireNotNull(line.packageId),
        declaredValueUsd = declaredValueUsd,
        additionalCharges = charges,
        additionalChargesTotalUsd = totalUsd,
        exchangeRateUsdToJmd = rate,
    )
}
