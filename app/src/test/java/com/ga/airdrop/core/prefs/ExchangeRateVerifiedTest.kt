package com.ga.airdrop.core.prefs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A seed rate must never be indistinguishable from a confirmed one.
 *
 * [ExchangeRateStore.DEFAULT_USD_TO_JMD] exists so a cold launch paints a real
 * number instantly. It is NOT a fact, and it has already been wrong in
 * production: the app quoted 160.625 for months while the server said 162.00,
 * understating every JMD figure a customer saw, because nothing recorded that
 * the value was a starting guess rather than an answer.
 *
 * `verified` is what makes those two states tellable apart. Before it, they
 * were the same `Double` and the difference was unrecoverable downstream.
 *
 * ⚠️ [ExchangeRateStore] is an `object` — a singleton with process-wide state.
 * These tests reset it before AND after each case so they cannot leak into each
 * other or into the rest of the suite. `prefs` is null here (no Android
 * context), and every write is null-safe, so persistence simply no-ops while
 * the in-memory contract is fully exercised.
 */
class ExchangeRateVerifiedTest {

    @Before fun reset() = ExchangeRateStore.clear()

    @After fun tearDown() = ExchangeRateStore.clear()

    @Test
    fun `the seed rate is NOT verified — it is a starting guess`() {
        assertEquals(
            ExchangeRateStore.DEFAULT_USD_TO_JMD,
            ExchangeRateStore.current,
            0.0001,
        )
        assertFalse(
            "the constant is a seed so the first paint is not blank; calling it " +
                "verified is exactly the bug that quoted 160.625 for months",
            ExchangeRateStore.verified,
        )
    }

    @Test
    fun `only a server response marks the rate verified`() {
        ExchangeRateStore.update(162.0)

        assertEquals(162.0, ExchangeRateStore.current, 0.0001)
        assertTrue("a rate that came off the wire is a fact", ExchangeRateStore.verified)
    }

    @Test
    fun `a nonsense rate is refused and cannot forge verification`() {
        // The guard has to reject before it sets the flag — otherwise a garbage
        // response upgrades the seed to "confirmed" while leaving it unchanged,
        // which is worse than the original bug.
        listOf(0.0, -1.0, -162.0).forEach { bad ->
            ExchangeRateStore.clear()
            ExchangeRateStore.update(bad)

            assertEquals(
                "rate=$bad must not overwrite the seed",
                ExchangeRateStore.DEFAULT_USD_TO_JMD,
                ExchangeRateStore.current,
                0.0001,
            )
            assertFalse(
                "rate=$bad must not mark the rate verified",
                ExchangeRateStore.verified,
            )
        }
    }

    @Test
    fun `clearing a session drops the verification, not just the number`() {
        ExchangeRateStore.update(162.0)
        assertTrue(ExchangeRateStore.verified)

        ExchangeRateStore.clear()

        assertEquals(
            ExchangeRateStore.DEFAULT_USD_TO_JMD,
            ExchangeRateStore.current,
            0.0001,
        )
        assertFalse(
            "leaving verified=true after a clear would relabel the seed as a " +
                "confirmed rate for the next user of this process",
            ExchangeRateStore.verified,
        )
    }

    @Test
    fun `a later confirmed rate replaces an earlier one and stays verified`() {
        ExchangeRateStore.update(160.0)
        ExchangeRateStore.update(162.0)

        assertEquals(162.0, ExchangeRateStore.current, 0.0001)
        assertTrue(ExchangeRateStore.verified)
    }
}
