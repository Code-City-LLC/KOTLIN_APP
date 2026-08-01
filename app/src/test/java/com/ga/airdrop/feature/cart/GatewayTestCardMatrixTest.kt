package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.model.CheckoutSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway test-card matrix, driven through the REAL return function.
 *
 * Kemar asked for the checkout tested "in full" against Stripe's and NCB's
 * published test cards, including the return path. This covers the half that
 * can be proven without a live merchant session: **for each card, does the
 * client reach the correct terminal state?**
 *
 * ## ⚠️ WHAT THIS DOES NOT DO — read before trusting it
 *
 * It does **not** place a live authorisation. No card is sent to Stripe or to
 * PowerTranz here. What it pins is the client's handling of the OUTCOME each
 * card produces, using the real server payload shape
 * (see [PaymentReturnRealServerShapeTest] for why the shape matters — the old
 * fixture invented a field Laravel never sends, so the suite stayed green while
 * every real payment failed to confirm).
 *
 * A live sandbox charge remains unverified and is the one thing a human still
 * has to do. Saying so here so nobody reads a green suite as "cards tested".
 *
 * ## Why outcome-mapping is the right unit
 *
 * `verifySession` is what decides, after the customer returns from the gateway,
 * whether their money moved. Card *numbers* never reach this code — Stripe's
 * hosted page and PowerTranz's 3DS page own that. What reaches it is a status
 * pair, and each published test card is a machine for producing one specific
 * pair. So the card matrix IS a status matrix, and this is where it belongs.
 *
 * Card numbers and expected outcomes transcribed 2026-08-01 from
 * docs.stripe.com/testing. PowerTranz's card list is behind a login wall, so
 * the NCB cases below are built from the ISO-8583 codes the rail actually
 * returns rather than from card numbers we cannot cite.
 */
class GatewayTestCardMatrixTest {

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
        // generic_decline · insufficient_funds · lost_card · expired_card · incorrect_cvc
        listOf(
            "4000000000000002" to "generic_decline",
            "4000000000009995" to "insufficient_funds",
            "4000000000009987" to "lost_card",
            "4000000000000069" to "expired_card",
            "4000000000000127" to "incorrect_cvc",
        ).forEach { (card, reason) ->
            val r = verify(status = "open", paymentStatus = "unpaid")
            assertTrue(
                "card $card ($reason) leaves the Stripe session OPEN — the customer " +
                    "can still retry on the same session, so this must NOT be " +
                    "terminal. Got: $r",
                r is PaymentReturnResult.NotPaid && !r.terminal,
            )
        }
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
        listOf(
            "4000000000003220",
            "4000008400000027",
            "4000002500003155",
            "4000002760003184",
        ).forEach { card ->
            val during = verify(status = "open", paymentStatus = "unpaid")
            assertTrue(
                "card $card is mid-3DS — the customer is still authenticating, " +
                    "this is NOT a terminal decline. Got: $during",
                during is PaymentReturnResult.NotPaid && !during.terminal,
            )
        }
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

    // ── NCB / PowerTranz ────────────────────────────────────────────────────

    /**
     * The JMD rail returns ISO-8583 codes, and Laravel normalises them into the
     * same status pair before the client ever sees them. PowerTranz's card list
     * is behind a login wall, so these are keyed on the ISO outcomes rather than
     * on card numbers I cannot cite — inventing plausible-looking PowerTranz
     * numbers would be worse than omitting them.
     */
    @Test
    fun `NCB approval - ISO 00 - confirms`() {
        val r = verify(status = "complete", paymentStatus = "paid")
        assertTrue("ISO 00 is Approved and must confirm. Got: $r", r is PaymentReturnResult.Success)
    }

    @Test
    fun `NCB declines - ISO 05 51 54 - are terminal and must NOT hold the latch`() {
        // 05 Do Not Honour · 51 Insufficient Funds · 54 Expired Card.
        // Unlike Stripe, a PowerTranz decline ends that authorisation outright —
        // there is no reusable open session — so holding the latch would brick
        // the rail exactly as the no-TTL bug did in #209.
        listOf("05", "51", "54").forEach { iso ->
            val r = verify(status = "failed", paymentStatus = "unpaid")
            assertTrue(
                "ISO $iso is a hard decline; the latch must release or the customer " +
                    "cannot retry at all. Got: $r",
                r is PaymentReturnResult.NotPaid && r.terminal,
            )
        }
    }

    @Test
    fun `NCB 3DS challenge in flight stays pending`() {
        val r = verify(status = "pending", paymentStatus = "unpaid")
        assertTrue(
            "the customer is on the PowerTranz 3DS page; neither confirm nor " +
                "release until it resolves. Got: $r",
            r is PaymentReturnResult.NotPaid && !r.terminal,
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
