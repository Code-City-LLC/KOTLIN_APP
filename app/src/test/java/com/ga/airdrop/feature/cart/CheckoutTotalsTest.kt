package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutTotalsTest {

    private val lines = listOf(
        CartStore.CartLine(id = 1, packageId = 101, qty = 2, priceUsd = 10.0),
    )

    @Test
    fun `authoritative USD fee wins while raw JMD remains exact`() {
        val result = available(
            resolveCheckoutTotals(
                lines,
                CheckoutDeliveryQuote(true, 800.0, "JMD", 6.25),
                verifiedExchangeUsdToJmd = 160.0,
            ),
        )

        assertEquals(20.0, result.itemsUsd, 0.0)
        assertEquals(3_200.0, result.itemsJmd ?: -1.0, 0.0)
        assertEquals(6.25, result.deliveryUsd, 0.0)
        assertEquals(800.0, result.deliveryJmd ?: -1.0, 0.0)
        assertEquals(26.25, result.totalUsd, 0.0)
        assertEquals(4_000.0, result.totalJmd ?: -1.0, 0.0)
        assertTrue(result.showsDeliveryRow)
    }

    @Test
    fun `JMD fee falls back to a verified server rate only`() {
        val result = available(
            resolveCheckoutTotals(
                lines,
                CheckoutDeliveryQuote(true, 800.0, "JMD", null),
                verifiedExchangeUsdToJmd = 160.0,
            ),
        )

        assertEquals(5.0, result.deliveryUsd, 0.0)
        assertEquals(800.0, result.deliveryJmd ?: -1.0, 0.0)
    }

    @Test
    fun `unverified seed cannot resolve a missing USD fee`() {
        val result = resolveCheckoutTotals(
            lines,
            CheckoutDeliveryQuote(true, 800.0, "JMD", null),
            verifiedExchangeUsdToJmd = null,
        )

        assertEquals(
            DELIVERY_FEE_UNAVAILABLE_REASON,
            (result as CheckoutTotalsResult.Unavailable).reason,
        )
    }

    @Test
    fun `authoritative USD fee remains payable without a JMD rate`() {
        val resolved = resolveCheckoutTotals(
            lines,
            CheckoutDeliveryQuote(true, null, "USD", 5.0),
            verifiedExchangeUsdToJmd = null,
        )
        val result = available(resolved)

        assertEquals(25.0, result.totalUsd, 0.0)
        assertNull(result.totalJmd)
        assertTrue(checkoutTotalsPermitPayment("USD", resolved))
        assertFalse(checkoutTotalsPermitPayment("JMD", resolved))
    }

    @Test
    fun `pickup is zero fee and has no delivery row`() {
        val result = available(
            resolveCheckoutTotals(
                lines,
                CheckoutDeliveryQuote(false, null, null, null),
                verifiedExchangeUsdToJmd = 160.0,
            ),
        )

        assertEquals(0.0, result.deliveryUsd, 0.0)
        assertEquals(20.0, result.totalUsd, 0.0)
        assertFalse(result.showsDeliveryRow)
    }

    @Test
    fun `negative delivery quote fails closed`() {
        val result = resolveCheckoutTotals(
            lines,
            CheckoutDeliveryQuote(true, -1.0, "JMD", 5.0),
            verifiedExchangeUsdToJmd = 160.0,
        )

        assertTrue(result is CheckoutTotalsResult.Unavailable)
    }

    @Test
    fun `exact money pair formatter never reconverts raw JMD`() {
        assertEquals("JMD 800.00 / USD 6.25", formatCheckoutMoneyPair(6.25, 800.0))
        assertEquals("JMD — / USD 6.25", formatCheckoutMoneyPair(6.25, null))
    }

    private fun available(result: CheckoutTotalsResult): CheckoutTotalsBreakdown =
        (result as CheckoutTotalsResult.Available).breakdown
}
