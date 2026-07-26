package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drop Number formatting.
 *
 * ⚠️ SUPERSEDED FORMAT: this used to assert "letter prefix + last 11 digits
 * grouped 3 / drop4 / drop8-mid4-last4". SwiftHawk #79761 §2 replaced it by
 * owner direction with the **last SIX digits grouped 3 + 3**
 * (`ARD00000138634` -> `ARD 138 634`). Display only — the full reference still
 * goes to the API and is what search matches, and a short reference is never
 * zero-padded. The status-aware empty fallback is unchanged.
 */
class DropNumberFormatTest {

    @Test
    fun `groups the last six digits three and three`() {
        // The canonical example from the handoff.
        assertEquals("ARD 138 634", ShipmentsFormat.trackingCode("ARD00000138634"))
        // 7 digits -> last six of them, prefix uppercased.
        assertEquals("ABC 234 567", ShipmentsFormat.trackingCode("abc1234567"))
        // 11 digits -> still only the last six.
        assertEquals("ABC 678 901", ShipmentsFormat.trackingCode("abc12345678901"))
        // No prefix is fine.
        assertEquals("234 567", ShipmentsFormat.trackingCode("1234567"))
        // Fewer than six digits -> as-is, NEVER zero-padded.
        assertEquals("AB 123", ShipmentsFormat.trackingCode("AB123"))
        // Embedded spaces are stripped before grouping.
        assertEquals("US 456 789", ShipmentsFormat.trackingCode("US 1 2345 6789"))
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
