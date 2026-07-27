package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.prefs.ExchangeRateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * One screen must not show the same money at two different JMD figures.
 *
 * Package Details reads two rates:
 *   `exchangeRate`  — app-wide, and it falls back to the HARDCODED
 *                     [ExchangeRateStore.DEFAULT_USD_TO_JMD]. The fetch that
 *                     fills it is onSuccess-only (PackageDetailsViewModel:176-179),
 *                     so a failed/slow /exchange-rates leaves the constant in
 *                     place and surfaces nothing.
 *   `effectiveRate` — the package's OWN server rate, falling back to the above.
 *
 * Breakdown, Exchange Rate and Total all use `effectiveRate`. The CIF sheet used
 * `exchangeRate`, so it converted at a hardcoded constant while the rest of the
 * same screen converted at the server's number.
 *
 * These tests pin the state contract that made the divergence possible. The call
 * site itself is covered by the parity test alongside it.
 */
class PackageDetailsRateConsistencyTest {

    private fun state(serverRate: Double?, appWide: Double) = PackageDetailsUiState(
        detail = serverRate?.let { ShipmentPackageDetail(id = 1, exchangeRate = it) },
        exchangeRate = appWide,
    )

    @Test
    fun `effectiveRate prefers the package's own server rate`() {
        val s = state(serverRate = 162.0, appWide = ExchangeRateStore.DEFAULT_USD_TO_JMD)

        assertEquals(162.0, s.effectiveRate, 0.0001)
        assertNotEquals(
            "the two rates genuinely differ — that is why using the wrong one is visible",
            s.exchangeRate,
            s.effectiveRate,
        )
    }

    /**
     * The exact failure state: /exchange-rates has not answered, so the app-wide
     * rate is still the hardcoded constant. `effectiveRate` must NOT be it.
     */
    @Test
    fun `a package rate wins even when the app-wide rate is still the hardcoded default`() {
        val s = state(serverRate = 162.0, appWide = ExchangeRateStore.DEFAULT_USD_TO_JMD)

        assertEquals(ExchangeRateStore.DEFAULT_USD_TO_JMD, s.exchangeRate, 0.0001)
        assertNotEquals(
            "converting at 160.625 while the rest of the screen uses 162.00 is the bug",
            ExchangeRateStore.DEFAULT_USD_TO_JMD,
            s.effectiveRate,
        )
    }

    /** With no package rate, falling back to the app-wide rate is correct. */
    @Test
    fun `effectiveRate falls back to the app-wide rate when the package has none`() {
        val s = state(serverRate = null, appWide = 161.5)

        assertEquals(161.5, s.effectiveRate, 0.0001)
    }

    /**
     * Guards the money itself: at the two rates a CIF total visibly differs, so
     * a future regression cannot be dismissed as rounding.
     */
    @Test
    fun `the two rates produce visibly different CIF totals`() {
        val cifUsd = 1_000.0
        val s = state(serverRate = 162.0, appWide = ExchangeRateStore.DEFAULT_USD_TO_JMD)

        val correct = cifUsd * s.effectiveRate
        val wrong = cifUsd * s.exchangeRate

        assertEquals(162_000.0, correct, 0.01)
        assertEquals(160_625.0, wrong, 0.01)
        assertNotEquals(correct, wrong, 1.0)
    }
}
