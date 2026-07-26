package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `package_ids` on the payment-verify response (BronzeMountain `92e739ce`).
 *
 * This is the field Kemar's "fetch the real packages instead of a static rail"
 * post-checkout screen is built on, so the decode is pinned against the exact
 * body published for the contract rather than a shape I assumed.
 *
 * The reason this gets its own test: four separate endpoints on this codebase
 * have shipped a decode that NEVER threw and silently produced null or an empty
 * list, and the screen then rendered a plausible-looking constant. An empty
 * `packageIds` is indistinguishable from "nothing was paid for" at the call
 * site, so a mis-keyed field here would quietly resurrect the static rail with
 * no error anywhere.
 */
class PaymentVerifyPackageIdsTest {

    /** The exact body from the contract message. */
    @Test
    fun `the published verify body decodes its package ids`() {
        val body = """
            {
              "status": "succeeded",
              "payment_status": "paid",
              "amount": 12500,
              "currency": "USD",
              "invoice_id": 778899,
              "package_ids": [153901, 153902, 153903]
            }
        """.trimIndent()

        val decoded = AirdropJson.decodeFromString(PaymentIntentStatus.serializer(), body)

        assertEquals(listOf(153901, 153902, 153903), decoded.packageIds)
        assertEquals("succeeded", decoded.status)
        assertEquals("paid", decoded.paymentStatus)
        // invoice_id arrives as a NUMBER here but is modelled as a String.
        assertEquals("778899", decoded.invoiceId)
    }

    /**
     * Unpaid or not-yet-fulfilled returns `[]`, never null — so callers may
     * iterate without a null check, which is the whole point of the contract.
     */
    @Test
    fun `an unfulfilled payment decodes to an empty list, not null`() {
        val body = """{"status":"processing","payment_status":"unpaid","package_ids":[]}"""

        val decoded = AirdropJson.decodeFromString(PaymentIntentStatus.serializer(), body)

        assertTrue(decoded.packageIds.isEmpty())
    }

    /**
     * A 404 returns no `package_ids` key at all, and older servers predate the
     * field entirely. Neither may throw — a decode failure here would turn a
     * successful payment into an error screen.
     */
    @Test
    fun `a body with no package_ids key still decodes`() {
        val body = """{"status":"succeeded","payment_status":"paid","invoice_id":"778899"}"""

        val decoded = AirdropJson.decodeFromString(PaymentIntentStatus.serializer(), body)

        assertTrue(decoded.packageIds.isEmpty())
        assertEquals("succeeded", decoded.status)
    }

    /**
     * ⚠️ THE PATH THE APP ACTUALLY USES.
     *
     * `PaymentReturnViewModel` verifies through `checkoutSessionStatus`
     * (`GET payments/{sessionId}/status`), NOT `paymentIntentStatus`. I first
     * added `package_ids` only to [PaymentIntentStatus], which would have left
     * the post-checkout screen with an empty list on the live path — and empty
     * is indistinguishable from "nothing was paid for", so the static rail
     * would have stayed with no error anywhere. Caught by BrightHarbor's review
     * of the contract before it shipped.
     */
    @Test
    fun `the session-status body the app actually verifies through decodes its ids`() {
        val body = """
            {
              "session_id": "cs_test_a1b2c3",
              "status": "complete",
              "payment_status": "paid",
              "invoice_id": 778899,
              "amount_total": 125.00,
              "currency": "USD",
              "package_ids": [153901, 153902]
            }
        """.trimIndent()

        val decoded = AirdropJson.decodeFromString(CheckoutSessionStatus.serializer(), body)

        assertEquals(listOf(153901, 153902), decoded.packageIds)
        assertEquals("complete", decoded.status)
        assertEquals("paid", decoded.paymentStatus)
        assertEquals(778899, decoded.invoiceId)
    }

    /** Same tolerance on the session path: absent key and empty array both decode. */
    @Test
    fun `the session-status body tolerates an absent or empty package_ids`() {
        val absent = AirdropJson.decodeFromString(
            CheckoutSessionStatus.serializer(),
            """{"session_id":"cs_x","status":"complete","payment_status":"paid"}""",
        )
        assertTrue(absent.packageIds.isEmpty())

        val empty = AirdropJson.decodeFromString(
            CheckoutSessionStatus.serializer(),
            """{"session_id":"cs_x","status":"open","payment_status":"unpaid","package_ids":[]}""",
        )
        assertTrue(empty.packageIds.isEmpty())
    }

    /**
     * The ids are read from the response, never from the Stripe `packages`
     * metadata. That metadata carries TRACKING CODES and is written at checkout
     * — before payment — so it describes what the customer intended to buy; on
     * a partial fulfilment it names packages they did not pay for. Asserting
     * the type here is the cheap guard: tracking codes are strings like
     * `ARDQA1784731781163` and would not survive decoding into Int.
     */
    @Test
    fun `the ids are numeric package ids, not tracking codes`() {
        val body = """{"status":"succeeded","package_ids":[153901]}"""

        val decoded = AirdropJson.decodeFromString(PaymentIntentStatus.serializer(), body)

        assertEquals(1, decoded.packageIds.size)
        assertEquals(153901, decoded.packageIds.single())
    }
}
