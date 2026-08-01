package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.model.CheckoutSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stripe checkout STATUS-MAPPING through the real return function.
 *
 * ⚠️ THIS FILE TESTS NO CARDS AND NO GATEWAY. Read that before citing it.
 *
 * It was originally written as a "gateway test-card matrix" and claimed NCB
 * coverage. That claim was FALSE and BrightHarbor caught it: every case here
 * calls [verifySession], which is **Stripe-only** — `PaymentReturnViewModel`
 * routes it to `payments.checkoutSessionStatus` and requires a Stripe session
 * id. The real NCB path is `createNcbSession` / `completeNcbPayment(spiToken)`
 * with an entirely different DTO (`spi_token` + `redirect_data` -> `invoice_id`,
 * not status/payment_status). The NCB-labelled cases therefore executed **zero
 * NCB production code** and have been deleted rather than renamed.
 *
 * The card-number loops are gone too. Card numbers never reach client code —
 * Stripe's hosted page owns entry — so iterating them while sending the same
 * hand-chosen status pair was duplicate proof wearing a card-testing label.
 *
 * What remains is real and worth having: the mapping from Stripe's
 * (status, payment_status) pair to a terminal client decision. Payloads use the
 * shape Laravel actually sends — `session_id` ABSENT — the same discipline as
 * [PaymentReturnRealServerShapeTest], whose header records that an idealised
 * fixture kept the suite green while every real payment failed to confirm.
 *
 * Scenario names below reference the Stripe test card that PRODUCES each status
 * pair, as documentation of provenance only. **No card is exercised.**
 */
class StripeStatusMappingTest {

    /** The real payload: `session_id` absent, exactly as Laravel sends it. */
    private fun payload(
        status: String?,
        paymentStatus: String?,
        packageIds: List<Int> = listOf(7),
    ) = CheckoutSessionStatus(
        sessionId = null,
        status = status,
        paymentStatus = paymentStatus,
        invoiceId = null,
        amountTotal = 125.50,
        currency = "usd",
        packageIds = packageIds,
    )

    private fun verify(status: String?, paymentStatus: String?) = runBlocking {
        verifySession("cs_test_matrix", { 0L }) {
            Result.success(payload(status, paymentStatus))
        }
    }

    // ── STRIPE — the success card ───────────────────────────────────────────

    /**
     * `4242 4242 4242 4242` — the baseline approve. Completes the hosted page
     * and Stripe reports `status=complete, payment_status=paid`.
     */
    @Test
    fun `stripe 4242 success card confirms and clears the cart`() {
        val r = verify(status = "complete", paymentStatus = "paid")
        assertTrue(
            "4242…4242 is Stripe's approve card; it must confirm. Got: $r",
            r is PaymentReturnResult.Success,
        )
        assertEquals(listOf(7), (r as PaymentReturnResult.Success).packageIds)
    }

    // ── STRIPE — declines ───────────────────────────────────────────────────

    /**
     * Every decline card leaves the SESSION open — Stripe does not close a
     * checkout because one card was refused, the customer may simply try
     * another. So the honest client state is "still pending", NOT "failed".
     *
     * ⚠️ Releasing the pending latch here would be the bug: it would let the
     * customer start a second checkout while the first session is still live
     * and payable, which is how a double-charge happens.
     */
    @Test
    fun `stripe decline cards leave the session open and must NOT be treated as terminal`() {
        // Produced by every Stripe decline card (generic_decline,
        // insufficient_funds, lost_card, expired_card, incorrect_cvc). They all
        // yield the SAME pair, so one assertion covers them — iterating the card
        // numbers proved nothing extra and implied testing that never happened.
        val r = verify(status = "open", paymentStatus = "unpaid")
        assertTrue(
            "a declined card leaves the Stripe session OPEN — the customer can " +
                "still retry on it, so this must NOT be terminal. Releasing the " +
                "latch here would let a SECOND checkout start against a session " +
                "that is still payable, which is how a double-charge happens. " +
                "Got: $r",
            r is PaymentReturnResult.NotPaid && !r.terminal,
        )
    }

    /**
     * The one decline shape that IS terminal: the customer abandoned and the
     * session expired. Only here may the latch be released.
     */
    @Test
    fun `an EXPIRED stripe session is terminal and releases the pending latch`() {
        val r = verify(status = "expired", paymentStatus = "unpaid")
        assertTrue(
            "an expired session can never be paid, so it must release. Got: $r",
            r is PaymentReturnResult.NotPaid && r.terminal,
        )
    }

    // ── STRIPE — 3D Secure ──────────────────────────────────────────────────

    /**
     * `4000 0000 0000 3220`, `4000 0084 0000 0027`, `4000 0025 0000 3155`,
     * `4000 0027 6000 3184` — all require a 3DS challenge.
     *
     * Mid-challenge the session is `open/unpaid`, and the client must NOT read
     * that as a decline: the customer is still authenticating in the browser.
     * Treating it as failure would abandon a payment that is about to succeed.
     */
    @Test
    fun `stripe 3DS cards stay pending DURING the challenge, then confirm after it`() {
        // The pair Stripe reports while a 3DS challenge is in flight.
        val during = verify(status = "open", paymentStatus = "unpaid")
        assertTrue(
            "mid-3DS the customer is still authenticating; this is NOT a terminal " +
                "decline, and treating it as one abandons a payment that is about " +
                "to succeed. Got: $during",
            during is PaymentReturnResult.NotPaid && !during.terminal,
        )
        val after = verify(status = "complete", paymentStatus = "paid")
        assertTrue(
            "a completed 3DS challenge must confirm like any other paid session. Got: $after",
            after is PaymentReturnResult.Success,
        )
    }

    /**
     * The nastiest 3DS shape, and the reason `isTerminalNotPaid` checks the
     * PAIR rather than either field alone: Stripe can report `complete` while
     * `payment_status` is still `unpaid` when authentication fails at the end.
     *
     * Reading `status` alone would confirm a payment that never happened —
     * clearing the customer's cart for money that was never taken.
     */
    @Test
    fun `a 3DS failure reported as complete-but-unpaid must NOT confirm`() {
        val r = verify(status = "complete", paymentStatus = "unpaid")
        assertTrue(
            "complete+unpaid means authentication failed. Confirming here would " +
                "clear the cart for an unpaid order. Got: $r",
            r is PaymentReturnResult.NotPaid && r.terminal,
        )
    }

    // ── The shape that is neither ───────────────────────────────────────────

    /**
     * An unreadable answer is NOT a decline. Same rule as everywhere else in
     * this repo: absence of information is not information.
     */
    @Test
    fun `an unreadable gateway answer stays pending rather than guessing`() {
        val r = verify(status = null, paymentStatus = null)
        assertTrue(
            "null/null means we could not find out. Confirming would clear a cart " +
                "for nothing; releasing would strand a payment that may have gone " +
                "through. Got: $r",
            r is PaymentReturnResult.Unconfirmed ||
                (r is PaymentReturnResult.NotPaid && !r.terminal),
        )
    }
}
