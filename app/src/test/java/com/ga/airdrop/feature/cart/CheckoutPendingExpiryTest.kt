package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deadlock fix: an abandoned hosted checkout (closed Custom Tab, crashed
 * return) previously blocked CheckoutFlowStore.start FOREVER — nothing
 * outside the payment-return deeplink cleared pendingHosted/pendingCreation.
 * They now expire on Stripe's own lifetimes (hosted 24h, creation 30min),
 * and legacy persisted rows (createdAtMs=0) expire immediately, healing
 * already-stuck installs.
 */
class CheckoutPendingExpiryTest {

    private val owner = AuthenticatedSessionOwner(sessionId = "sess-1", accountId = 7)

    private fun line(id: Int) = CartStore.CartLine(
        id = id,
        title = "Item $id",
        priceUsd = 1.0,
        imageUrl = null,
        packageId = id,
        kind = CartStore.CartLineKind.AUCTION,
    )

    private fun startFlowToOrderSummary(nowMs: () -> Long): PendingCheckoutCreation {
        CheckoutFlowStore.restoreForTests(TestSharedPreferences(), owner)
        CheckoutFlowStore.clockMsForTests = nowMs
        val flow = CheckoutFlowStore.start(owner, listOf(line(1)))
        checkNotNull(flow) { "flow should start" }
        checkNotNull(
            CheckoutFlowStore.update(owner, flow.id) {
                it.copy(
                    currency = CheckoutCurrency.USD.wireValue,
                    phase = CheckoutPhase.ORDER_SUMMARY,
                )
            },
        ) { "flow should advance to order summary" }
        return checkNotNull(CheckoutFlowStore.beginHostedCheckoutCreation(owner)) {
            "creation should begin"
        }
    }

    @After
    fun reset() {
        CheckoutFlowStore.clockMsForTests = null
        CheckoutFlowStore.dropProcessStateForTests()
    }

    @Test
    fun `a fresh unresolved creation blocks a new checkout`() {
        var now = 1_000_000L
        startFlowToOrderSummary { now }

        assertNull(
            "unexpired creation must still block",
            CheckoutFlowStore.start(owner, listOf(line(1))),
        )
    }

    @Test
    fun `an unresolved creation expires after 30 minutes and unblocks checkout`() {
        var now = 1_000_000L
        startFlowToOrderSummary { now }

        now += 30L * 60 * 1000 + 1
        assertNull("expired creation is purged", CheckoutFlowStore.creating(owner))
        assertNotNull(
            "checkout must start again after expiry",
            CheckoutFlowStore.start(owner, listOf(line(1))),
        )
    }

    @Test
    fun `a recorded hosted checkout expires after 24 hours and unblocks checkout`() {
        var now = 1_000_000L
        val creation = startFlowToOrderSummary { now }
        checkNotNull(
            CheckoutFlowStore.recordHostedCheckout(owner, creation.id, "cs_test_123"),
        ) { "hosted checkout should record" }

        assertNull("fresh hosted pending blocks", CheckoutFlowStore.start(owner, listOf(line(1))))

        now += 24L * 60 * 60 * 1000 + 1
        assertNull("expired hosted pending is purged", CheckoutFlowStore.pending(owner))
        assertNotNull(
            "checkout must start again after the Stripe session lifetime",
            CheckoutFlowStore.start(owner, listOf(line(1))),
        )
    }

    @Test
    fun `a hosted checkout within its lifetime keeps blocking`() {
        var now = 1_000_000L
        val creation = startFlowToOrderSummary { now }
        checkNotNull(CheckoutFlowStore.recordHostedCheckout(owner, creation.id, "cs_test_123"))

        now += 23L * 60 * 60 * 1000 // 23h — still inside the session lifetime
        assertNotNull("still pending", CheckoutFlowStore.pending(owner))
        assertNull("still blocks new checkout", CheckoutFlowStore.start(owner, listOf(line(1))))
    }
}
