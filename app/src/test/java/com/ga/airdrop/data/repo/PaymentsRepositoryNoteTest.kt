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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsRepositoryNoteTest {

    @Test
    fun `local cart note is never serialized onto the checkout wire`() = runBlocking {
        val captured = AtomicReference<CreateCheckoutRequest>()
        val service = checkoutService(captured)
        val result = PaymentsRepository(service).createCheckout(
            packageIds = listOf(7),
            currency = "USD",
            isAuction = false,
            userNote = "Leave at reception",
            expectedSession = AuthTokenStore.RequestProvenance(
                revision = 12,
                sessionId = "note-test-session",
                accountId = 9,
            ),
        )

        assertTrue(result.isSuccess)
        val body = AirdropJson.parseToJsonElement(
            AirdropJson.encodeToString(requireNotNull(captured.get())),
        ).jsonObject
        assertFalse(body.containsKey("user_note"))
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
