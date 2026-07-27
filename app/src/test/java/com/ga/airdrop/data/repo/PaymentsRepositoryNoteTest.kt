package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.data.model.CreateCheckoutRequest
import com.ga.airdrop.data.model.DataEnvelope
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsRepositoryNoteTest {

    private fun checkout(note: String?): CreateCheckoutRequest {
        val captured = AtomicReference<CreateCheckoutRequest>()
        val result = runBlocking {
            PaymentsRepository(checkoutService(captured)).createCheckout(
                packageIds = listOf(7),
                currency = "USD",
                isAuction = false,
                userNote = note,
                expectedSession = AuthTokenStore.RequestProvenance(
                    revision = 12,
                    sessionId = "note-test-session",
                    accountId = 9,
                ),
            )
        }
        assertTrue(result.isSuccess)
        return requireNotNull(captured.get())
    }

    private fun body(r: CreateCheckoutRequest) =
        AirdropJson.parseToJsonElement(AirdropJson.encodeToString(r)).jsonObject

    /**
     * ⚠️ THIS TEST USED TO ASSERT THE OPPOSITE — `assertFalse(containsKey("user_note"))`,
     * under the name "local cart note is never serialized onto the checkout wire".
     * The assertion was the bug, and it made the bug look deliberate.
     *
     * It is not deliberate anywhere else in the system. Laravel implements the
     * field and says so, `PaymentController.php:335-339`:
     *
     *     // Cart "Your Note" (workroom #25169): the mobile clients send an
     *     // optional user_note with checkout; it rides Stripe metadata and
     *     // persists onto the invoice's payment rows at fulfillment.
     *     $userNote = trim((string) $request->input('user_note', ''));
     *
     * — "the mobile clients", plural. iOS is one of them
     * (`AirdropAPI.swift:6031/6039`). Android accepted the argument, never put
     * it in the body, and hid the unused-parameter warning with a `@Suppress`.
     *
     * So a customer typed a delivery instruction, Android stored it, trimmed
     * it, threaded it through three layers and dropped it — while the same
     * note typed on an iPhone reached the invoice.
     */
    @Test
    fun `the cart note is sent to the server under user_note`() {
        val sent = checkout("Leave at reception")

        assertEquals("Leave at reception", sent.userNote)
        assertTrue(
            "user_note must be on the wire — Laravel reads it and iOS sends it",
            body(sent).containsKey("user_note"),
        )
    }

    /** No note → the key is absent entirely, matching iOS's nil. */
    @Test
    fun `no note omits the key rather than sending an empty one`() {
        assertFalse(body(checkout(null)).containsKey("user_note"))
    }

    /**
     * Whitespace-only must be treated as no note, not persisted as a blank
     * instruction on the invoice. iOS does the same
     * (`(trimmedNote?.isEmpty == false) ? trimmedNote : nil`).
     */
    @Test
    fun `a whitespace-only note is not sent`() {
        assertEquals(null, checkout("   \n  ").userNote)
        assertFalse(body(checkout("   \n  ")).containsKey("user_note"))
    }

    /** Surrounding whitespace is trimmed, not shipped to the invoice. */
    @Test
    fun `a note is trimmed before it is sent`() {
        assertEquals("Ring the bell", checkout("  Ring the bell  ").userNote)
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkoutService(
        captured: AtomicReference<CreateCheckoutRequest>,
    ): AirdropApiService = Proxy.newProxyInstance(
        AirdropApiService::class.java.classLoader,
        arrayOf(AirdropApiService::class.java),
    ) { _, method, args ->
        when (method.name) {
            "createCheckout" -> {
                captured.set(args?.getOrNull(2) as CreateCheckoutRequest)
                DataEnvelope(
                    success = true,
                    data = CheckoutResponse(
                        checkoutUrl = "https://checkout.stripe.com/test",
                        sessionId = "cs_note_test",
                    ),
                )
            }
            else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
        }
    } as AirdropApiService
}
