package com.ga.airdrop.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The #209 P0, proven against a URL Stripe ACTUALLY issued.
 *
 * Minted live from pre-staging on 2026-08-01 via
 * `POST /api/v1/payments/create-checkout` for package 151611. Every previous
 * test used a hand-written URL; this is the real artifact, fragment and all.
 */
class RealStripeUrlTest {

    private val realUrl =
        "https://checkout.stripe.com/c/pay/cs_test_a1nZW6KF8PEZGZlWiPUOJTVFmFTsf2cp" +
            "aFLLm27Q4P6xvGyuvlLO3oIkFz#fidnandhYHdWcXxpYCc%2FJ2FgY2RwaXEnKSdicGRmZGhq" +
            "aWBTZHdsZGtxJz8nZmprcXdqaScpJ2R1bE5gfCc%2FJ3VuWnFgdnFaMDROVGA1MURvfElgYn03"

    @Test
    fun `the fixed validator ACCEPTS a real Stripe URL with its fragment`() {
        val out = validatedHostedCheckoutUrl(realUrl)
        assertNotNull(
            "This exact URL came back from Stripe. Rejecting it is the #209 P0 — " +
                "the customer saw 'Stripe started checkout but did not return a " +
                "secure URL' and the pending latch was already written, bricking " +
                "BOTH rails. Got: $out",
            out,
        )
    }

    @Test
    fun `embedded credentials are still rejected — the guard that mattered`() {
        assertEquals(
            "userInfo is the real security concern and must still be refused",
            null,
            validatedHostedCheckoutUrl("https://evil:pass@checkout.stripe.com/c/pay/cs_test_x#frag"),
        )
    }
}
