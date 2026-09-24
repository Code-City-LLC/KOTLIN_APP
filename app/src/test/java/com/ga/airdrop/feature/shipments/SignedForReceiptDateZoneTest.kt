package com.ga.airdrop.feature.shipments

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The "Signed For" date is the customer's calendar day. Laravel sends
 * delivered_at with Jamaica's offset (CarbonImmutable::toIso8601String), and
 * formatting that instant in UTC moved every delivery after 7 pm to the next
 * day.
 */
class SignedForReceiptDateZoneTest {

    private val deviceZone = TimeZone.getDefault()

    @Before
    fun setUp() = TimeZone.setDefault(TimeZone.getTimeZone("America/Jamaica"))

    @After
    fun tearDown() = TimeZone.setDefault(deviceZone)

    @Test
    fun `an evening delivery keeps its own day on a Jamaican device`() {
        assertEquals("22 Sep 2026", ShipmentsFormat.receiptDate("2026-09-22T20:30:00-05:00"))
        assertEquals("22 Sep 2026", ShipmentsFormat.receiptDate("2026-09-23T01:30:00Z"))
        assertEquals("22 Sep 2026", ShipmentsFormat.receiptDate("2026-09-23T01:30:00.000+00:00"))
    }

    @Test
    fun `a timestamp with no offset keeps the date it names in any zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals("22 Sep 2026", ShipmentsFormat.receiptDate("2026-09-22 20:30:00"))
        assertEquals("22 Sep 2026", ShipmentsFormat.receiptDate("2026-09-22"))
    }
}
