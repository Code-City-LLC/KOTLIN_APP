package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ARD display format — SwiftHawk #79761 §2.
 *
 * DISPLAY only: the last six digits grouped 3 + 3. The full reference still
 * goes to the API and is what search matches.
 */
class TrackingCodeFormatTest {

    @Test
    fun `last six digits grouped three and three`() {
        assertEquals("ARD 138 634", ShipmentsFormat.trackingCode("ARD00000138634"))
    }

    @Test
    fun `works regardless of existing spacing`() {
        assertEquals("ARD 138 634", ShipmentsFormat.trackingCode("ARD 000 0013 8634"))
    }

    /** Never zero-pad — that would misrepresent a short reference. */
    @Test
    fun `short references are shown as-is, never padded`() {
        assertEquals("ARD 1234", ShipmentsFormat.trackingCode("ARD1234"))
        assertEquals("ARD 12", ShipmentsFormat.trackingCode("ARD12"))
    }

    @Test
    fun `a drop-alerted package with no code keeps its awaiting copy`() {
        assertEquals("Awaiting Package Creation", ShipmentsFormat.trackingCode(null, statusId = 1))
    }

    @Test
    fun `other prefixes are preserved and uppercased`() {
        assertEquals("AIRQA 725 003", ShipmentsFormat.trackingCode("airqa0725003"))
    }
}
