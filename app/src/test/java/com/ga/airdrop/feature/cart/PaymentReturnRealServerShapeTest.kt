package com.ga.airdrop.feature.cart

import com.ga.airdrop.data.model.CheckoutSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the Stripe verify bug, written from the **real** server
 * payload instead of an idealised one.
 *
 * The existing suite passed throughout because its fixture echoed the requested
 * id straight back (`status(it, …)` — PaymentReturnVerifyTest:42), fabricating
 * the one field Laravel never sends. A fixture built from what we WISH the
 * contract were cannot catch a wrong contract, so the tests stayed green while
 * every real card payment failed to confirm.
 *
 * The payload below is transcribed from the actual producers:
 *   StripePaymentService::getSessionStatus()  -> status, invoice_id,
 *                                                payment_status, owner_user_id
 *   PaymentController::getStatus()            -> strips owner_user_id,
 *                                                adds package_ids
 * i.e. **`session_id` is absent**, so the decoded `sessionId` is null.
 */
class PaymentReturnRealServerShapeTest {

    /** Exactly what the server sends today — note sessionId is NOT set. */
    private fun realServerPayload(
        paymentStatus: String? = "paid",
        status: String? = "complete",
        amount: Double? = null,
        currency: String? = null,
        packageIds: List<Int> = emptyList(),
    ) = CheckoutSessionStatus(
        sessionId = null,
        status = status,
        paymentStatus = paymentStatus,
        invoiceId = null,
        amountTotal = amount,
        currency = currency,
        packageIds = packageIds,
    )

    /**
     * THE bug. Before the fix this returned Unconfirmed for a genuinely paid
     * checkout — customer charged, app says it failed, cart not cleared.
     */
    @Test
    fun `a paid checkout confirms even though the server omits session_id`() = runBlocking {
        val result = verifySession("cs_test_abc123", { 0L }) {
            Result.success(realServerPayload(amount = 125.5, currency = "usd"))
        }

        assertTrue(
            "a paid Stripe checkout must confirm; the server never echoes session_id, " +
                "and requiring it charged customers without ever clearing their cart. " +
                "Got: $result",
            result is PaymentReturnResult.Success,
        )
        assertEquals("USD 125.50", (result as PaymentReturnResult.Success).formattedAmount)
    }

    /** The paid-commit path must carry the package ids through untouched. */
    @Test
    fun `package ids survive a payload with no session_id`() = runBlocking {
        val result = verifySession("cs_test_abc123", { 0L }) {
            Result.success(realServerPayload(packageIds = listOf(7, 9)))
        }

        assertEquals(listOf(7, 9), (result as PaymentReturnResult.Success).packageIds)
    }

    /**
     * The mismatch guard is NOT deleted, only narrowed. A response that DOES
     * carry an id and disagrees is still a real mismatch and must be rejected —
     * otherwise a stale or crossed response could commit the wrong checkout.
     */
    @Test
    fun `a PRESENT but different session id is still rejected`() = runBlocking {
        val result = verifySession("cs_test_abc123", { 0L }) {
            Result.success(
                CheckoutSessionStatus(
                    sessionId = "cs_test_SOMEONE_ELSE",
                    status = "complete",
                    paymentStatus = "paid",
                    invoiceId = null,
                    amountTotal = 10.0,
                    currency = "usd",
                    packageIds = emptyList(),
                ),
            )
        }

        assertTrue(
            "a mismatched id that IS present must still be refused — narrowing " +
                "the guard must not remove it. Got: $result",
            result is PaymentReturnResult.Unconfirmed,
        )
    }

    /** A matching id keeps working — the day the server starts echoing it. */
    @Test
    fun `a PRESENT matching session id still confirms`() = runBlocking {
        val result = verifySession("cs_test_abc123", { 0L }) {
            Result.success(
                CheckoutSessionStatus(
                    sessionId = "cs_test_abc123",
                    status = "complete",
                    paymentStatus = "paid",
                    invoiceId = null,
                    amountTotal = 10.0,
                    currency = "usd",
                    packageIds = emptyList(),
                ),
            )
        }

        assertTrue(result is PaymentReturnResult.Success)
    }

    /** An unpaid session must NOT be waved through by the narrowed guard. */
    @Test
    fun `an unpaid checkout is still reported as not paid`() = runBlocking {
        val result = verifySession("cs_test_abc123", { 0L }) {
            Result.success(realServerPayload(paymentStatus = "unpaid", status = "open"))
        }

        assertTrue(
            "relaxing the id check must not turn an unpaid session into a success. Got: $result",
            result is PaymentReturnResult.NotPaid,
        )
    }

    /** An expired session must still be terminal, so it can be released. */
    @Test
    fun `an expired checkout is still terminal`() = runBlocking {
        val result = verifySession("cs_test_abc123", { 0L }) {
            Result.success(realServerPayload(paymentStatus = "unpaid", status = "expired"))
        }

        assertTrue((result as PaymentReturnResult.NotPaid).terminal)
    }
}
