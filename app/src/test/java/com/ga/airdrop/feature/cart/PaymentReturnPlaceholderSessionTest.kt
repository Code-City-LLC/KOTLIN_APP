package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CheckoutSessionStatus
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.repo.PaymentsRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The success return from a server that never substituted the session id.
 *
 * Laravel a4d6940d0 (2026-08-13) built the mobile success_url with
 * http_build_query, i.e. `session_id=%7BCHECKOUT_SESSION_ID%7D`. Stripe only
 * replaces the literal placeholder, so the app is handed
 * `airdrop://payment-success?session_id={CHECKOUT_SESSION_ID}` after a real,
 * successful payment. The return must verify the one pending checkout — and
 * never send or match an id that is not a Stripe `cs_` id.
 */
class PaymentReturnPlaceholderSessionTest {

    private val owner = AuthenticatedSessionOwner("placeholder-return-owner", 52)
    private val requestedSessionIds = mutableListOf<String>()

    @Before
    fun setUp() {
        CartStore.synchronousCommitOverrideForTests = null
        CheckoutFlowStore.synchronousCommitOverrideForTests = null
        CartStore.restoreForTests(TestSharedPreferences(), owner)
        CheckoutFlowStore.restoreForTests(TestSharedPreferences(), owner)
        CartNoteStore.restoreForTests(TestSharedPreferences())
    }

    @After
    fun tearDown() {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartNoteStore.dropProcessStateForTests()
    }

    @Test
    fun `the raw placeholder verifies the one pending checkout`() =
        assertRecoversPendingCheckout("{CHECKOUT_SESSION_ID}")

    @Test
    fun `the percent-encoded placeholder verifies the one pending checkout`() =
        assertRecoversPendingCheckout("%7BCHECKOUT_SESSION_ID%7D")

    @Test
    fun `a missing session id verifies the one pending checkout`() =
        assertRecoversPendingCheckout("")

    @Test
    fun `a real Stripe id keeps its exact match`() = runBlocking {
        val line = sale(3, 913)
        CartStore.add(line)
        pendingCheckout(line, "cs_test_pendingExact01")

        val result = viewModel().verify("cs_test_someOtherSession")

        assertTrue("got $result", result is PaymentReturnResult.Unconfirmed)
        assertEquals(emptyList<String>(), requestedSessionIds)
        assertTrue(CartStore.contains(line.key))
        assertEquals("cs_test_pendingExact01", CheckoutFlowStore.reconcilableSessionId(owner))
    }

    @Test
    fun `a placeholder with nothing pending is never sent to the server`() = runBlocking {
        val result = viewModel().verify("{CHECKOUT_SESSION_ID}")

        assertTrue("got $result", result is PaymentReturnResult.Unconfirmed)
        assertEquals(emptyList<String>(), requestedSessionIds)
    }

    private fun assertRecoversPendingCheckout(returnedId: String) = runBlocking {
        val line = sale(1, 911)
        val other = sale(2, 912)
        CartStore.add(line)
        pendingCheckout(line, "cs_test_placeholderReturn01")
        CartStore.add(other)

        val result = viewModel().verify(returnedId)

        assertTrue("a paid return for \"$returnedId\" must verify; got $result", result is PaymentReturnResult.Success)
        assertEquals("cs_test_placeholderReturn01", (result as PaymentReturnResult.Success).orderReference)
        assertEquals(listOf("cs_test_placeholderReturn01"), requestedSessionIds)
        assertFalse(CartStore.contains(line.key))
        assertTrue(CartStore.contains(other.key))
        assertEquals(null, CheckoutFlowStore.reconcilableSessionId(owner))
    }

    private fun viewModel(): PaymentReturnViewModel {
        val paid = AirdropJson.decodeFromString<CheckoutSessionStatus>(
            """{"status":"complete","payment_status":"paid","invoice_id":77,"package_ids":[911]}""",
        )
        val service = Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "checkoutSessionStatus" -> {
                    requestedSessionIds += args[2] as String
                    DataEnvelope(success = true, data = paid)
                }
                else -> error("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService
        return PaymentReturnViewModel(PaymentsRepository(service), FakeBoundary(owner))
    }

    private fun pendingCheckout(line: CartStore.CartLine, sessionId: String) {
        val flow = requireNotNull(CheckoutFlowStore.start(owner, listOf(line)))
        requireNotNull(
            CheckoutFlowStore.update(owner, expectedFlowId = flow.id) {
                it.copy(currency = "USD", phase = CheckoutPhase.ORDER_SUMMARY)
            },
        )
        val creation = requireNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(owner))
        requireNotNull(CheckoutFlowStore.recordHostedCheckout(owner, creation.id, sessionId))
    }

    private fun sale(id: Int, packageId: Int) = CartStore.CartLine(
        id = id,
        packageId = packageId,
        title = "Sale $id",
        priceUsd = 12.0,
        kind = CartStore.CartLineKind.AUCTION,
        isAuction = true,
    )

    private class FakeBoundary(initial: AuthenticatedSessionOwner?) : AuthenticatedSessionBoundary {
        val current = MutableStateFlow(initial)
        override val changes = current
        override fun capture(): AuthenticatedSessionOwner? = current.value
        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = current.value == owner
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false
            action()
            return true
        }
        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean =
            isCurrent(owner) && action()
        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? =
            owner.takeIf(::isCurrent)?.let {
                AuthenticatedRequestOwner(
                    it,
                    AuthTokenStore.RequestProvenance(94, it.sessionId, it.accountId),
                )
            }
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && owner.accountId == accountId
    }
}
