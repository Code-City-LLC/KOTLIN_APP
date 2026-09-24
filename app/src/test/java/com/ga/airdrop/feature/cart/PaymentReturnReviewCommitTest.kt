package com.ga.airdrop.feature.cart

import androidx.compose.runtime.saveable.SaverScope
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the hosted-checkout return does to the cart and to the pending
 * checkout when the fraud rules held the payment (Laravel 2026-09-15).
 *
 * A held session is paid on Stripe with an open review. It must never clear
 * the cart, and it keeps its pending checkout: staff have not decided, and the
 * on-resume reconciler re-verifies that exact session until they do. Approval
 * then commits it like any paid checkout; refusal releases it.
 */
class PaymentReturnReviewCommitTest {

    private val owner = AuthenticatedSessionOwner("review-commit-owner", 51)
    private lateinit var cartPrefs: TestSharedPreferences
    private lateinit var checkoutPrefs: TestSharedPreferences
    private lateinit var notePrefs: TestSharedPreferences
    private var serverStatus: CheckoutSessionStatus? = null

    @Before
    fun setUp() {
        CartStore.synchronousCommitOverrideForTests = null
        CheckoutFlowStore.synchronousCommitOverrideForTests = null
        cartPrefs = TestSharedPreferences()
        checkoutPrefs = TestSharedPreferences()
        notePrefs = TestSharedPreferences()
        CartStore.restoreForTests(cartPrefs, owner)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
        CartNoteStore.restoreForTests(notePrefs)
    }

    @After
    fun tearDown() {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartNoteStore.dropProcessStateForTests()
    }

    @Test
    fun `a held checkout keeps the cart and stays pending until staff decide`() = runBlocking {
        val line = sale(1, 901)
        val other = sale(2, 902)
        CartStore.add(line)
        pendingCheckout(line, "cs_test_heldReview01")
        CartStore.add(other)
        serverStatus = reviewedStatus("pending_review", "Payment Under Review")
        val viewModel = viewModel()

        val result = viewModel.verify("cs_test_heldReview01")

        assertTrue("a held payment is not a success; got $result", result is PaymentReturnResult.UnderReview)
        assertTrue("a held payment must not clear the cart", CartStore.contains(line.key))
        reload()
        assertTrue(CartStore.contains(line.key))
        assertEquals("cs_test_heldReview01", CheckoutFlowStore.reconcilableSessionId(owner))

        // The reconciler's next re-verify, after staff approved: an ordinary
        // paid commit that removes exactly the held rows.
        serverStatus = reviewedStatus("approved", "Payment Approved")
        val approved = viewModel.verify("cs_test_heldReview01")

        assertTrue("got $approved", approved is PaymentReturnResult.Success)
        assertEquals(listOf(other.key), CartStore.items.value.map(CartStore.CartLine::key))
        assertNull(CheckoutFlowStore.reconcilableSessionId(owner))
    }

    @Test
    fun `a refused checkout keeps the cart and releases the pending checkout`() = runBlocking {
        val line = sale(3, 903)
        CartStore.add(line)
        pendingCheckout(line, "cs_test_refusedReview01")
        serverStatus = reviewedStatus("declined", "Payment Declined")
        val viewModel = viewModel()

        val result = viewModel.verify("cs_test_refusedReview01")

        assertTrue("a refused payment is not a success; got $result", result is PaymentReturnResult.NotAccepted)
        assertEquals("Payment Declined", (result as PaymentReturnResult.NotAccepted).label)
        assertTrue("a refused payment must not clear the cart", CartStore.contains(line.key))
        assertNull(CheckoutFlowStore.pending("cs_test_refusedReview01", owner))
        reload()
        assertTrue(CartStore.contains(line.key))
        assertNull(CheckoutFlowStore.pending(owner))
        // The review is closed and refunded, so the customer may pay again.
        assertTrue(CheckoutFlowStore.start(owner, listOf(line)) != null)
    }

    @Test
    fun `an approved checkout clears exactly the paid rows like any paid checkout`() = runBlocking {
        val line = sale(4, 904)
        val other = sale(5, 905)
        CartStore.add(line)
        pendingCheckout(line, "cs_test_approvedReview01")
        CartStore.add(other)
        serverStatus = reviewedStatus("approved", "Payment Approved")
        val viewModel = viewModel()

        val result = viewModel.verify("cs_test_approvedReview01")

        assertTrue("got $result", result is PaymentReturnResult.Success)
        assertEquals(listOf(other.key), CartStore.items.value.map(CartStore.CartLine::key))
        assertNull(CheckoutFlowStore.pending("cs_test_approvedReview01", owner))
    }

    /**
     * A refusal released its pending checkout, so a re-verify after
     * Activity/process recreation could only answer "not pending" and degrade
     * the alert to "Couldn't confirm payment": it is kept across the restore.
     * An open review is not — it re-verifies, and may have been approved.
     */
    @Test
    fun `a refused alert survives a saved-state restore and a held one re-verifies`() {
        val scope = SaverScope { true }
        val refused = PaymentReturnResult.NotAccepted("Payment Declined", "We could not approve it.")
        val saved = with(PaymentAlertOutcomeSaver) { scope.save(refused) }
        assertEquals(refused, PaymentAlertOutcomeSaver.restore(requireNotNull(saved)))

        val held = PaymentReturnResult.UnderReview("Payment Under Review", "Your payment is under review.")
        val savedHeld = with(PaymentAlertOutcomeSaver) { scope.save(held) }
        assertNull(savedHeld?.let(PaymentAlertOutcomeSaver::restore))
    }

    private fun reviewedStatus(reviewStatus: String, label: String): CheckoutSessionStatus =
        AirdropJson.decodeFromString(
            """{"status":"complete","payment_status":"paid","invoice_id":null,"package_ids":[],
               "review":{"id":21,"status":"$reviewStatus","label":"$label","message":"$label: see your email.","amount":12,"currency":"USD"}}""",
        )

    private fun viewModel(): PaymentReturnViewModel {
        val service = Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "checkoutSessionStatus" -> DataEnvelope(success = true, data = requireNotNull(serverStatus))
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

    private fun reload() {
        CartStore.dropProcessStateForTests()
        CheckoutFlowStore.dropProcessStateForTests()
        CartStore.restoreForTests(cartPrefs, owner)
        CheckoutFlowStore.restoreForTests(checkoutPrefs, owner)
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
                    AuthTokenStore.RequestProvenance(93, it.sessionId, it.accountId),
                )
            }
        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && owner.accountId == accountId
    }
}
