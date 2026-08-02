package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.config.AirdropFeatureFlags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * JMD checkout must not be reachable while Laravel's NCB completion is
 * non-durable.
 *
 * ## Why this gate exists at all
 *
 * v26 shipped pointing at the retired host, so it cannot reach the API — no
 * customer is completing an NCB payment today. The rail is dead in the field by
 * accident. **v27 fixes the host and re-enables it as a side effect**, so
 * shipping v27 without a gate turns a payment path back on rather than leaving
 * it as-is. BrightHarbor gated the release on exactly that (ORC 88698).
 *
 * Laravel's open P0s at the time of writing (ORC 88519/88520/88588): the replay
 * path checks an `invoice_id` column that does not exist, so the claimed
 * idempotency is dead; and `GET_LOCK` releases on provider timeout or process
 * death while status stays `3ds_complete`, so a retry can call `/payment`
 * again — an unknown-outcome double charge.
 *
 * ## The rule this pins
 *
 * ONE flag governs THREE things, and any two of them drifting apart is the bug:
 *  1. whether JMD is **offered**
 *  2. whether the rail can **initiate**
 *  3. what the customer is **told**
 *
 * Hiding the option alone is NOT sufficient — a JMD flow persisted by an
 * earlier build survives the upgrade and would walk straight into PowerTranz on
 * the next launch. That is why the selector itself fails closed.
 */
class JmdCheckoutGateTest {

    private var original = false

    @Before fun capture() { original = AirdropFeatureFlags.jmdNcbCheckout }

    @After fun restore() { AirdropFeatureFlags.jmdNcbCheckout = original }

    // ── Shipping state: OFF ─────────────────────────────────────────────────

    @Test
    fun `the flag ships OFF — this is the assertion that actually protects customers`() {
        // If someone flips the default without reading the KDoc, this fails and
        // names the reason. Deliberately reads the SOURCE default, not the
        // captured value.
        restore()
        assertEquals(
            "AirdropFeatureFlags.jmdNcbCheckout must ship false until Laravel's " +
                "NCB completion is durable — see ORC 88519/88520/88588",
            false,
            original,
        )
    }

    @Test
    fun `with the gate OFF a JMD flow cannot select a payment rail`() {
        AirdropFeatureFlags.jmdNcbCheckout = false

        assertNull(
            "a persisted JMD flow from an earlier build must NOT reach PowerTranz " +
                "after upgrading — hiding the popup option does not stop it",
            checkoutPaymentRail("JMD"),
        )
        assertNull(checkoutPaymentRail("jmd"))
        assertNull(checkoutPaymentRail(" Jmd "))
    }

    @Test
    fun `USD and Stripe are completely unaffected by the gate`() {
        // The outage fix must not be held hostage to the payment freeze. USD
        // checkout has to keep working in exactly the same way.
        AirdropFeatureFlags.jmdNcbCheckout = false
        assertEquals(CheckoutPaymentRail.STRIPE, checkoutPaymentRail("USD"))
        assertEquals(CheckoutPaymentRail.STRIPE, checkoutPaymentRail("usd"))
        assertEquals(CheckoutNextRoute.ORDER_SUMMARY, checkoutNextRoute("USD"))
    }

    @Test
    fun `an unknown currency is still rejected, gate or no gate`() {
        AirdropFeatureFlags.jmdNcbCheckout = false
        assertNull(checkoutPaymentRail("CAD"))
        assertNull(checkoutPaymentRail(""))
        assertNull(checkoutPaymentRail(null))
    }

    // ── The other side: turning it on must fully restore the rail ───────────

    @Test
    fun `with the gate ON the JMD rail works exactly as before`() {
        // The gate must be a switch, not a rewrite. When Laravel is green,
        // flipping this one boolean has to restore the previous behaviour
        // completely — otherwise re-enabling becomes its own risky change.
        AirdropFeatureFlags.jmdNcbCheckout = true

        assertEquals(CheckoutPaymentRail.NCB_POWERTRANZ, checkoutPaymentRail("JMD"))
        assertEquals(CheckoutPaymentRail.NCB_POWERTRANZ, checkoutPaymentRail("jmd"))
        assertEquals(CheckoutPaymentRail.STRIPE, checkoutPaymentRail("USD"))
    }

    @Test
    fun `the route is unchanged by the gate — only the RAIL is gated`() {
        // checkoutNextRoute drives navigation, not payment. Gating it too would
        // change screen flow for no safety benefit and make re-enabling riskier.
        AirdropFeatureFlags.jmdNcbCheckout = false
        assertEquals(CheckoutNextRoute.PROFILE_INFORMATION, checkoutNextRoute("JMD"))

        AirdropFeatureFlags.jmdNcbCheckout = true
        assertEquals(CheckoutNextRoute.PROFILE_INFORMATION, checkoutNextRoute("JMD"))
    }
}
