package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ARD display and search forms — the same rule as the web's
 * resources/js/utils/trackingCode.js (`displayTrackingCode` and
 * `normalizeTrackingCode`), so every surface reads "ARD 153 998".
 */
class TrackingCodeFormatTest {

    @Test
    fun `the stored code displays without zeros, grouped in threes`() {
        assertEquals("ARD 153 998", ShipmentsFormat.trackingCode("ARD00000153998"))
    }

    @Test
    fun `works regardless of existing spacing`() {
        assertEquals("ARD 138 634", ShipmentsFormat.trackingCode("ARD 000 0013 8634"))
    }

    @Test
    fun `short codes are grouped, never zero-padded`() {
        assertEquals("ARD 1 234", ShipmentsFormat.trackingCode("ARD1234"))
        assertEquals("ARD 12", ShipmentsFormat.trackingCode("ARD12"))
    }

    @Test
    fun `a drop-alerted package with no code keeps its awaiting copy`() {
        assertEquals("Awaiting Package Creation", ShipmentsFormat.trackingCode(null, statusId = 1))
    }

    @Test
    fun `other labels are shown as sent`() {
        assertEquals("airqa0725003", ShipmentsFormat.trackingCode("airqa0725003"))
    }

    @Test
    fun `search turns the displayed form back into the stored code`() {
        assertEquals("ARD00000153998", ShipmentsFormat.normalizeTrackingCode("ARD 153 998"))
        assertEquals("ARD00000015814SD", ShipmentsFormat.normalizeTrackingCode("ard 15 814 sd"))
        assertEquals("ARD00000156194ES", ShipmentsFormat.normalizeTrackingCode("ard156194es"))
        assertEquals("ARD00000153998", ShipmentsFormat.normalizeTrackingCode("ARD00000153998"))
        assertEquals("1LSCYG50069L93S", ShipmentsFormat.normalizeTrackingCode(" 1LSCYG50069L93S "))
    }

    @Test
    fun `the Packages search finds a package by its displayed drop number`() {
        val stored = ShipmentPackage(id = 1, trackingCode = "ARD00000153998")
        val other = ShipmentPackage(id = 2, trackingCode = "ARD00000153997")

        val state = PackagesUiState(items = listOf(stored, other), searchText = "ARD 153 998")

        assertEquals(listOf(stored), state.visibleItems)
    }
}
