package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The #209 regression guard: a Stripe Checkout URL carries a `#fragment`, and
 * rejecting fragments discarded every real USD checkout.
 *
 * ⚠️ The fixture below is **SYNTHETIC** and must stay that way.
 *
 * An earlier version of this file embedded a checkout URL pre-staging had
 * actually issued, together with the package it was minted for. An issued
 * session artifact does not belong in source or in git history, however
 * short-lived or test-mode it is. Flagged by BrightHarbor; the issued session
 * was expired and the fixture replaced.
 *
 * **No coverage was lost by synthesising it.** What the guard needs is the
 * SHAPE — host, `/c/pay/` path, `cs_` session prefix, and a `#fragment` — not
 * provenance. A real URL proved nothing here that a structurally equivalent one
 * does not, which is exactly why embedding one was cost without benefit.
 */
class StripeCheckoutUrlShapeTest {

    /**
     * Structurally identical to what Stripe issues: the fragment is the part
     * the pre-#209 validator choked on, so it is the part that must be present.
     */
    private val syntheticStripeUrl =
        "https://checkout.stripe.com/c/pay/cs_test_SYNTHETIC0000000000000000" +
            "#fidSYNTHETICfragmentpayload%2Fnotarealsession"

    @Test
    fun `a Stripe checkout URL with a fragment is ACCEPTED`() {
        assertNotNull(
            "Stripe Checkout URLs always carry a #fragment. Rejecting them is the " +
                "#209 P0: the server returned a valid checkout_url, the app threw it " +
                "away, and because the pending latch was already written it bricked " +
                "BOTH the USD and JMD rails.",
            validatedHostedCheckoutUrl(syntheticStripeUrl),
        )
    }

    @Test
    fun `embedded credentials are still rejected — the guard that actually mattered`() {
        assertEquals(
            "userInfo is the real security concern and must stay refused",
            null,
            validatedHostedCheckoutUrl(
                "https://evil:pass@checkout.stripe.com/c/pay/cs_test_x#frag",
            ),
        )
    }

    @Test
    fun `non-https and hostless URLs are still rejected`() {
        assertEquals(null, validatedHostedCheckoutUrl("http://checkout.stripe.com/c/pay/cs_x#f"))
        assertEquals(null, validatedHostedCheckoutUrl("not a url"))
        assertEquals(null, validatedHostedCheckoutUrl(""))
        assertEquals(null, validatedHostedCheckoutUrl(null))
    }
}
