package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drop Number formatting — Swift FigmaPackagesViewController.formatTrackingCode
 * (_:statusCode:) (commit 9d31872). Guards the grouping (letter prefix + last
 * 11 digits grouped 3 / drop4 / drop8-mid4-last4) and the status-aware empty
 * fallback so cleanup passes cannot silently diverge from the source of truth.
 */
class DropNumberFormatTest {

    @Test
    fun `groups prefix and last 11 digits like Swift`() {
        // 11+ digits → "<head> <mid4> <last4>", prefix uppercased.
        assertEquals("ABC 123 4567 8901", ShipmentsFormat.trackingCode("abc12345678901"))
        // 4..7 digits → "<head> <last4>".
        assertEquals("123 4567", ShipmentsFormat.trackingCode("1234567"))
        // <=3 digits → as-is.
        assertEquals("AB 123", ShipmentsFormat.trackingCode("AB123"))
        // Embedded spaces are stripped before grouping.
        assertEquals("US 1 2345 6789", ShipmentsFormat.trackingCode("US 1 2345 6789"))
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
