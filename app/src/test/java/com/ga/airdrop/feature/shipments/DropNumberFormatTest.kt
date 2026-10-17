package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drop Number display.
 *
 * Kemar, 2026-10-17: "We have started to stick with six digits ARD number ...
 * getting rid of the zeros and we'll have space ... across everywhere." The
 * rule is the web's `displayTrackingCode` (resources/js/utils/trackingCode.js):
 * an ARD code loses its leading zeros and groups its digits in threes from the
 * right, keeping an SD/ES suffix. Anything else is shown unchanged. The
 * status-aware empty fallback is unchanged.
 */
class DropNumberFormatTest {

    @Test
    fun `an ARD code drops its zeros and groups its digits in threes`() {
        assertEquals("ARD 153 998", ShipmentsFormat.trackingCode("ARD00000153998"))
        assertEquals("ARD 15 814 SD", ShipmentsFormat.trackingCode("ARD00000015814SD"))
        assertEquals("ARD 1 000 000", ShipmentsFormat.trackingCode("ARD00001000000"))
        assertEquals("ARD 156 194 ES", ShipmentsFormat.trackingCode("ard00000156194es"))
    }

    @Test
    fun `a code already in the display form reads the same`() {
        assertEquals("ARD 156 194", ShipmentsFormat.trackingCode("ARD 156 194"))
    }

    @Test
    fun `anything that is not an ARD code is shown unchanged`() {
        assertEquals("1LSCYG50069L93S", ShipmentsFormat.trackingCode("1LSCYG50069L93S"))
        assertEquals("1LSCYG50069L93S", ShipmentsFormat.trackingCode("  1LSCYG50069L93S "))
        // More digits than the canonical eleven cannot be a real ARD code.
        assertEquals("ARD000001539981", ShipmentsFormat.trackingCode("ARD000001539981"))
    }

    @Test
    fun `empty tracking is status-aware to match Swift`() {
        // Status 1 (Drop Alerted) → awaiting-creation copy.
        assertEquals("Awaiting Package Creation", ShipmentsFormat.trackingCode(null, 1))
        assertEquals("Awaiting Package Creation", ShipmentsFormat.trackingCode("", 1))
        // Any other status with empty tracking → em dash.
        assertEquals("—", ShipmentsFormat.trackingCode(null, 7))
        assertEquals("—", ShipmentsFormat.trackingCode("   ", 2))
        // No status supplied (Payments cards) keeps the legacy hyphen.
        assertEquals("-", ShipmentsFormat.trackingCode(null))
        assertEquals("-", ShipmentsFormat.trackingCode(""))
    }
}
