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
        sessionId: String,
        paymentStatus: String? = null,
        status: String? = null,
        amount: Double? = null,
        currency: String? = null,
        packageIds: List<Int> = emptyList(),
    ) = CheckoutSessionStatus(
        sessionId = sessionId,
        status = status,
        paymentStatus = paymentStatus,
        invoiceId = null,
        amountTotal = amount,
        currency = currency,
        packageIds = packageIds,
    )

    @Test
    fun `paid formats major-unit amount and returns success`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_1", { 0L }) {
            calls++
            Result.success(status(it, paymentStatus = "paid", amount = 125.5, currency = "usd"))
        }
        assertTrue(result is PaymentReturnResult.Success)
        result as PaymentReturnResult.Success
        // JMD / USD, never one currency (Kemar 2026-07-26) — this used to assert
        // the single-code "USD 125.50". The major-units rule it was really
        // guarding still holds: 125.5 renders as 125.50, NOT 1.26 (no /100).
        val expectedJmd = com.ga.airdrop.feature.shipments.ShipmentsFormat.money(
            125.5 * com.ga.airdrop.core.prefs.ExchangeRateStore.current,
        )
        assertEquals(
            "JMD $expectedJmd / USD 125.50",
            result.formattedAmount,
        )
        assertEquals("cs_1", result.orderReference)
        assertEquals(1, calls)
    }

    /**
     * ⚠️ THE SEAM BETWEEN DECODE AND RESULT, which nothing covered.
     *
     * `CheckoutSessionStatus.packageIds` had a decode test, and
     * `PaymentReturnContent -> onPaid` had a handoff test, but NOTHING asserted
     * that `verifySession` carries the decoded ids onto `Success.packageIds`.
     * Dropped there, the post-checkout screen would show "nothing to show yet"
     * — indistinguishable from an unfulfilled payment — with every other test
     * still green. BrightHarbor #179 block 1/3.
     */
    @Test
    fun `the verified package ids reach Success exactly as decoded`() = runBlocking {
        val result = verifySession("cs_pkg", { 0L }) {
            Result.success(
                status(it, paymentStatus = "paid", amount = 12.0, currency = "usd",
                    packageIds = listOf(153901, 153902, 153903)),
            )
        }

        assertTrue(result is PaymentReturnResult.Success)
        result as PaymentReturnResult.Success
        assertEquals(listOf(153901, 153902, 153903), result.packageIds)
    }

    /**
     * An unfulfilled payment returns `package_ids: []`. That must arrive as an
     * empty list — not null, not dropped — so the screen can tell "paid, none
     * yet" apart from "we could not read them".
     */
    @Test
    fun `an empty package id list survives as empty rather than being lost`() = runBlocking {
        val result = verifySession("cs_empty", { 0L }) {
            Result.success(status(it, paymentStatus = "paid", amount = 1.0, currency = "usd"))
        }

        assertTrue(result is PaymentReturnResult.Success)
        result as PaymentReturnResult.Success
        assertEquals(emptyList<Int>(), result.packageIds)
    }

    @Test
    fun `authoritative not-paid never retries`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_2", { 0L }) {
            calls++
            Result.success(status(it, paymentStatus = "unpaid"))
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
                Result.success(status(it, status = "paid"))
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
            Result.success(status("unused", paymentStatus = "paid"))
        }
        assertTrue(result is PaymentReturnResult.Unconfirmed)
        assertEquals(0, calls)
    }

    @Test
    fun `mismatched response session is unconfirmed without retry`() = runBlocking {
        var calls = 0
        val result = verifySession("cs_expected", { 0L }) {
            calls++
            Result.success(status("cs_other", paymentStatus = "paid"))
        }
        assertTrue(result is PaymentReturnResult.Unconfirmed)
        assertEquals(1, calls)
    }

    /**
     * ⚠️ THIS TEST USED TO ASSERT THE OPPOSITE, AND THE ASSERTION WAS THE BUG.
     *
     * It pinned "missing session id -> Unconfirmed" on the belief that the
     * server echoes `session_id` back. It does not, and never did:
     * `StripePaymentService::getSessionStatus()` returns only status,
     * invoice_id, payment_status and owner_user_id, and the controller adds
     * package_ids. So this condition was not an edge case — it was EVERY real
     * card payment. Customers were charged, told the response "did not match",
     * and left with an uncleared cart they could pay from again.
     *
     * The expectation is inverted deliberately. A missing id is not evidence of
     * a mismatch; `mismatched response session is unconfirmed without retry`
     * above still guards the case that genuinely is one.
     */
    @Test
    fun `missing response session still confirms a paid checkout`() = runBlocking {
        val result = verifySession("cs_expected", { 0L }) {
            Result.success(
                CheckoutSessionStatus(
                    sessionId = null,
                    status = "paid",
                    paymentStatus = "paid",
                    invoiceId = null,
                    amountTotal = null,
                    currency = null,
                ),
            )
        }
        assertTrue(
            "the server never sends session_id, so requiring it rejected every " +
                "real payment. Got: $result",
            result is PaymentReturnResult.Success,
        )
    }
}
