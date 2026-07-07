package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.model.CheckoutSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payment-return verify semantics — Swift SceneDelegate.swift:432-620 parity.
 * The load-bearing rules (from docs/PARITY_GAP_SPECS.md, do not collapse):
 *  - authoritative not-paid on a SUCCESSFUL response → NotPaid immediately, NO retry;
 *  - only thrown/network failures retry (3 attempts);
 *  - blank session id → Unconfirmed without any fetch;
 *  - amount_total is MAJOR units (no /100);
 *  - after 3 failures → Unconfirmed (never implies failure).
 */
class PaymentReturnVerifyTest {

    private fun status(
        paymentStatus: String? = null,
        status: String? = null,
        amount: Double? = null,
        currency: String? = null,
    ) = CheckoutSessionStatus(
        sessionId = "cs_test",
        status = status,
        paymentStatus = paymentStatus,
        invoiceId = null,
        amountTotal = amount,
        currency = currency,
    )

    @Test
    fun `paid formats major-unit amount and returns success`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_1", { 0L }) {
            calls++
            Result.success(status(paymentStatus = "paid", amount = 125.5, currency = "usd"))
        }
        assertTrue(result is PaymentReturnResult.Success)
        result as PaymentReturnResult.Success
        assertEquals("USD 125.50", result.formattedAmount) // major units, no /100
        assertEquals("cs_1", result.orderReference)
        assertEquals(1, calls)
    }

    @Test
    fun `authoritative not-paid never retries`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_2", { 0L }) {
            calls++
            Result.success(status(paymentStatus = "unpaid"))
        }
        assertTrue(result is PaymentReturnResult.NotPaid)
        assertEquals("unpaid", (result as PaymentReturnResult.NotPaid).statusText)
        assertEquals(1, calls) // no retry on an authoritative answer
    }

    @Test
    fun `network failures retry three times then unconfirmed`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_3", { 0L }) {
            calls++
            Result.failure(RuntimeException("boom"))
        }
        assertTrue(result is PaymentReturnResult.Unconfirmed)
        assertEquals(3, calls)
        assertEquals("boom", (result as PaymentReturnResult.Unconfirmed).detail)
    }

    @Test
    fun `failure then paid recovers on retry`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_4", { 0L }) {
            calls++
            if (calls == 1) {
                Result.failure(RuntimeException("flake"))
            } else {
                Result.success(status(status = "paid"))
            }
        }
        assertTrue(result is PaymentReturnResult.Success)
        assertEquals(2, calls)
        // no amount/currency → null formatted amount (subline falls back)
        assertEquals(null, (result as PaymentReturnResult.Success).formattedAmount)
    }

    @Test
    fun `blank session id is unconfirmed without fetching`() = runBlocking {
        var calls = 0
        val result = verifySession("", { 0L }) {
            calls++
            Result.success(status(paymentStatus = "paid"))
        }
        assertTrue(result is PaymentReturnResult.Unconfirmed)
        assertEquals(0, calls)
    }
}
