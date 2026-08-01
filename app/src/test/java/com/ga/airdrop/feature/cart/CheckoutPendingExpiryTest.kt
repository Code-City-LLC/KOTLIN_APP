package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import org.junit.After
import org.junit.Assert.assertEquals
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
        // creating() must report the lapsed latch as gone BEFORE anything calls
        // start(): the auction rail's pay() checks creating() and returns on a
        // hit, so a non-sweeping read here bricks that rail permanently.
        assertNull("expired creation must not be reported to blocking guards", CheckoutFlowStore.creating(owner))
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
        // Same as above: the blocking guards read pending() directly and bail
        // out before start() is reached, so this read has to come back clean.
        assertNull("expired hosted pending must not be reported to blocking guards", CheckoutFlowStore.pending(owner))
        assertNotNull(
            "checkout must start again after the Stripe session lifetime",
            CheckoutFlowStore.start(owner, listOf(line(1))),
        )
    }

    /**
     * The reconciler's door stays open after the guards' door has closed.
     *
     * A customer can pay and then leave the phone alone past the session TTL.
     * [CheckoutFlowStore.pending] sweeps — that is what keeps the auction rail
     * from bricking — so the on-resume reconciler deliberately reads through
     * [CheckoutFlowStore.reconcilableSessionId], which must NOT sweep, or the
     * payment it was about to verify disappears first.
     */
    @Test
    fun `a lapsed session is hidden from guards but still reconcilable`() {
        var now = 1_000_000L
        val creation = startFlowToOrderSummary { now }
        checkNotNull(CheckoutFlowStore.recordHostedCheckout(owner, creation.id, "cs_live_paid"))

        now += 24L * 60 * 60 * 1000 + 1

        assertEquals(
            "the reconciler must still see the session it has to verify",
            "cs_live_paid",
            CheckoutFlowStore.reconcilableSessionId(owner),
        )
        assertNull("...while the blocking guards see it as gone", CheckoutFlowStore.pending(owner))
    }

    /**
     * The heal that matters for phones ALREADY bricked by the fragment bug: rows
     * persisted before this fix carry no timestamp, so kotlinx-serialization
     * decodes them with createdAtMs=0 — arbitrarily old, therefore expired on
     * the very first sweep. A stuck customer recovers by installing the build,
     * with no data clearing and no reinstall.
     */
    @Test
    fun `a legacy persisted row with no timestamp expires immediately and unblocks checkout`() {
        val storage = TestSharedPreferences()
        // Exactly what a pre-fix install left on disk: no createdAtMs key at all.
        storage.edit().putString(
            "pending_hosted",
            """{"flowId":"flow-legacy","ownerSessionId":"sess-1","ownerAccountId":7,""" +
                """"checkoutSessionId":"cs_live_stuck","cartKeys":[{"kind":"AUCTION","id":1}]}""",
        ).commit()
        CheckoutFlowStore.restoreForTests(storage, owner)
        CheckoutFlowStore.clockMsForTests = { 1_000_000L }

        assertNull("legacy row must be treated as expired", CheckoutFlowStore.pending(owner))
        assertNotNull(
            "a phone bricked before the fix must check out again after installing it",
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
