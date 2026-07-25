package com.ga.airdrop.feature.delivery

import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.DeliveryStagePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryCenterPresentationTest {

    @Test
    fun deliveryStatusColorsPreserveTerminalMeaning() {
        assertEquals(DeliveryStagePalette.Current, deliveryStatusColor("assigned"))
        assertEquals(DeliveryStagePalette.Current, deliveryStatusColor("out_for_delivery"))
        assertEquals(DeliveryStagePalette.Passed, deliveryStatusColor("delivered"))
        assertEquals(AlertPalette.Error, deliveryStatusColor("failed"))
        assertEquals(AlertPalette.Cancel, deliveryStatusColor("cancelled"))
    }

    @Test
    fun serverStatusAndTimestampAreDisplayedWithoutInventingCopy() {
        assertEquals("Out For Delivery", humanizeDeliveryStatus("out_for_delivery"))
        assertEquals("Jul 22, 2026 • 1:20 PM", formatDeliveryTimestamp("2026-07-22T13:20:00Z"))
        assertEquals(null, formatDeliveryTimestamp("not-a-timestamp"))
    }
}
