package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CheckoutSessionStatus
import com.ga.airdrop.data.model.CreateNcbSessionRequest
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.NcbSessionResponse
import com.ga.airdrop.feature.cart.PaymentReturnResult
import com.ga.airdrop.feature.cart.verifySession
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * A payment the fraud rules HELD (Laravel v1.19, 2026-09-15) must reach the
 * screens as "under review", never as a failure, on every path it can arrive:
 *
 *  - a 422 body `{success:false, code:"payment_under_review", message}` from
 *    create-checkout / NCB session / NCB complete (the HttpException path);
 *  - a 200 envelope carrying the same code (the DataEnvelope path);
 *  - the `review` object on the hosted-checkout verification response.
 *
 * The one thing none of these may do is invite a second payment.
 */
class PaymentUnderReviewContractTest {

    private val heldBody =
        """{"success":false,"code":"payment_under_review","review_id":12,"review_status":"pending_review","review_label":"Under review","message":"Your payment is being reviewed."}"""

    private fun http422(body: String): HttpException =
        HttpException(Response.error<Any>(422, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `a 422 hold becomes the typed exception with the server's message`() = runBlocking {
        val result = apiResult<Unit> { throw http422(heldBody) }
        val e = result.exceptionOrNull()
        assertTrue("expected PaymentUnderReviewException, got $e", e is PaymentUnderReviewException)
        assertEquals("Your payment is being reviewed.", e?.message)
        assertTrue(e!!.isPaymentUnderReview)
    }

    @Test
    fun `an ordinary 422 stays a plain ApiException`() = runBlocking {
        val result = apiResult<Unit> { throw http422("""{"success":false,"message":"Do not honour"}""") }
        val e = result.exceptionOrNull()
        assertTrue(e is ApiException)
        assertFalse(e!!.isPaymentUnderReview)
        assertEquals("Do not honour", e.message)
    }

    @Test
    fun `the envelope decodes code and error_code`() {
        val held = AirdropJson.decodeFromString<DataEnvelope<NcbSessionResponse>>(heldBody)
        assertEquals("payment_under_review", held.code)
        assertEquals(false, held.success)
        assertNull(held.data?.spiToken)

        val tier = AirdropJson.decodeFromString<DataEnvelope<NcbSessionResponse>>(
            """{"success":false,"error_code":"NO_RATE_CARD","message":"no card"}""",
        )
        assertEquals("NO_RATE_CARD", tier.code)
    }

    @Test
    fun `a 200 envelope carrying the hold code fails the NCB session as under review`() = runBlocking {
        val service = Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "createNcbSession" -> DataEnvelope<NcbSessionResponse>(
                    success = false,
                    message = "Your payment is being reviewed.",
                    data = null,
                    code = "payment_under_review",
                )
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService

        val result = PaymentsRepository(service).createNcbSession(
            request = CreateNcbSessionRequest(
                packageIds = listOf(41),
                currency = "JMD",
                isAuction = false,
                firstName = "Test",
                lastName = "Customer",
                address = "1 Test Street",
                city = "Kingston",
                country = "JM",
                cardName = "Test Customer",
                cardNumber = "4111111111111111",
                cardMonth = "12",
                cardYear = "2030",
                cardCvv = "123",
                deliveryMode = "pickup",
                pickupLocation = "Kingston",
            ),
            expectedSession = AuthTokenStore.RequestProvenance(
                revision = 7,
                sessionId = "review-contract-session",
                accountId = 9,
            ),
        )
        val e = result.exceptionOrNull()
        assertTrue("expected PaymentUnderReviewException, got $e", e is PaymentUnderReviewException)
        assertEquals("Your payment is being reviewed.", e?.message)
    }

    /**
     * ⚠️ THE FIXTURE USED TO SAY `"payment_status":"unpaid"`, AND THAT SHAPE
     * CANNOT HAPPEN. Laravel only processes a Checkout Session whose
     * payment_status is `paid` (StripeWebhookController ~:302), and the hold is
     * raised INSIDE that processing (StripePaymentService ~:1162) with the money
     * already captured. So a held session always reads paid + pending_review —
     * and against that real shape the old paid-first check said "Your payment
     * was successful" and cleared the cart.
     */
    @Test
    fun `a held hosted checkout is paid on Stripe and still verifies as UnderReview`() = runBlocking {
        val status = AirdropJson.decodeFromString<CheckoutSessionStatus>(
            """{"status":"complete","payment_status":"paid","invoice_id":null,"package_ids":[],
               "review":{"id":12,"status":"pending_review","label":"Under review","message":"Your payment is being reviewed.","amount":120,"currency":"USD"}}""",
        )
        assertTrue(status.review?.isPending == true)

        val outcome = verifySession("cs_test_held", retryDelayMs = { 0L }) { Result.success(status) }
        assertTrue("a held payment is not a success; got $outcome", outcome is PaymentReturnResult.UnderReview)
        assertEquals("Under review", (outcome as PaymentReturnResult.UnderReview).label)
        assertEquals("Your payment is being reviewed.", outcome.message)

        // Staff asked for more information: still open, still under review.
        val infoRequired = verifySession("cs_test_info", retryDelayMs = { 0L }) {
            Result.success(status.copy(review = status.review?.copy(status = "info_required")))
        }
        assertTrue("info_required is still open; got $infoRequired", infoRequired is PaymentReturnResult.UnderReview)

        // Without a review object the same paid session is an ordinary success,
        // and an unpaid one is still NotPaid.
        val plain = verifySession("cs_test_plain", retryDelayMs = { 0L }) {
            Result.success(status.copy(review = null))
        }
        assertTrue(plain is PaymentReturnResult.Success)
        val unpaid = verifySession("cs_test_unpaid", retryDelayMs = { 0L }) {
            Result.success(status.copy(paymentStatus = "unpaid", review = null))
        }
        assertTrue(unpaid is PaymentReturnResult.NotPaid)
    }

    /**
     * Declined, cancelled and expired close the review and Laravel reverses the
     * charge — but Stripe still reports the Checkout Session as paid. None of
     * them may celebrate; each carries the review's own label and message.
     */
    @Test
    fun `a refused hosted checkout verifies as NotAccepted with the review's words`() = runBlocking {
        for ((reviewStatus, label) in listOf(
            "declined" to "Payment Declined",
            "cancelled" to "Payment Cancelled",
            "expired" to "Review Expired",
        )) {
            val status = AirdropJson.decodeFromString<CheckoutSessionStatus>(
                """{"status":"complete","payment_status":"paid","invoice_id":null,"package_ids":[],
                   "review":{"id":13,"status":"$reviewStatus","label":"$label","message":"We could not approve it; the charge was reversed.","amount":120,"currency":"USD"}}""",
            )

            val outcome = verifySession("cs_test_refused", retryDelayMs = { 0L }) { Result.success(status) }
            assertTrue(
                "$reviewStatus is refused, never a success; got $outcome",
                outcome is PaymentReturnResult.NotAccepted,
            )
            assertEquals(label, (outcome as PaymentReturnResult.NotAccepted).label)
            assertEquals("We could not approve it; the charge was reversed.", outcome.message)
        }
    }

    /** A refusal with no text must not borrow the "being reviewed" fallback. */
    @Test
    fun `a refused review with no message never says it is still being reviewed`() = runBlocking {
        val status = AirdropJson.decodeFromString<CheckoutSessionStatus>(
            """{"status":"complete","payment_status":"paid","package_ids":[],
               "review":{"id":14,"status":"declined"}}""",
        )

        val outcome = verifySession("cs_test_refused", retryDelayMs = { 0L }) { Result.success(status) }
        assertTrue("got $outcome", outcome is PaymentReturnResult.NotAccepted)
        val refused = outcome as PaymentReturnResult.NotAccepted
        assertFalse(refused.message, refused.message.contains("being reviewed"))
        assertTrue(refused.message, refused.message.isNotBlank())
    }

    /** Approval is the one review outcome that is paid: the normal success path. */
    @Test
    fun `an approved hosted checkout is an ordinary success`() = runBlocking {
        val status = AirdropJson.decodeFromString<CheckoutSessionStatus>(
            """{"status":"complete","payment_status":"paid","invoice_id":88,"package_ids":[41,42],
               "review":{"id":15,"status":"approved","label":"Payment Approved","message":"Good news — approved.","can_retry":false}}""",
        )

        val outcome = verifySession("cs_test_approved", retryDelayMs = { 0L }) { Result.success(status) }
        assertTrue("got $outcome", outcome is PaymentReturnResult.Success)
        assertEquals(listOf(41, 42), (outcome as PaymentReturnResult.Success).packageIds)
    }
}
