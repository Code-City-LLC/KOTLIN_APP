package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.model.PackageStorage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Storage-fee copy rules. Laravel owns the numbers ([storageStatusLine] never
 * derives one); these pin the SENTENCE we put in front of the customer, which
 * is where a wrong word costs money or trust.
 */
class StorageFeeCardTest {

    private fun storage(
        fee: Double? = 0.0,
        daysInStorage: Int? = 0,
        freeDays: Int? = 15,
        daysUntilFees: Int? = null,
        inFreePeriod: Boolean? = null,
        accruing: Boolean? = null,
        writtenOff: Boolean? = false,
    ) = PackageStorage(
        eligible = true,
        daysInStorage = daysInStorage,
        fee = fee,
        currency = "USD",
        freeDays = freeDays,
        daysUntilFees = daysUntilFees,
        inFreePeriod = inFreePeriod,
        since = "2026-07-25 09:19:07",
        accruing = accruing,
        writtenOff = writtenOff,
    )

    @Test
    fun `free period with no fee counts the days down`() {
        assertEquals(
            "12 free days left",
            storageStatusLine(storage(fee = 0.0, daysUntilFees = 12, inFreePeriod = true)),
        )
    }

    @Test
    fun `singular day is not pluralised`() {
        assertEquals(
            "1 free day left",
            storageStatusLine(storage(fee = 0.0, daysUntilFees = 1, inFreePeriod = true)),
        )
    }

    /**
     * The case that made this file necessary. Pre-staging package 153906 is
     * `in_free_period: true` AND carries `fee: "8.00"` — the warehouse charge
     * and the tier's free window are independent. Saying "15 free days left"
     * there tells a customer their storage is free while they owe $8.00, and
     * the invoice then contradicts the app.
     */
    @Test
    fun `a fee inside the free period is never described as simply free`() {
        val line = storageStatusLine(
            storage(fee = 8.0, daysUntilFees = 15, inFreePeriod = true, accruing = true),
        )
        assertEquals("Charge applied · 15 free days left", line)
    }

    @Test
    fun `written off wins over every other state`() {
        val line = storageStatusLine(
            storage(fee = 488.0, daysUntilFees = 3, inFreePeriod = true, writtenOff = true),
        )
        assertEquals("Waived — nothing to pay", line)
    }

    @Test
    fun `accruing outside the free window says so`() {
        assertEquals(
            "Accruing daily",
            storageStatusLine(storage(fee = 0.0, inFreePeriod = false, accruing = true)),
        )
    }

    @Test
    fun `charged but no longer accruing`() {
        assertEquals(
            "Charge applied",
            storageStatusLine(storage(fee = 488.0, inFreePeriod = false, accruing = false)),
        )
    }

    @Test
    fun `nothing known yet`() {
        assertEquals("Not accruing", storageStatusLine(storage(fee = 0.0)))
    }
}
