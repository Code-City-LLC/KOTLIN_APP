package com.ga.airdrop.feature.shipments

import com.ga.airdrop.core.designsystem.theme.AlertPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the package-list status text color mapping against
 * FigmaPackagesViewController.statusColor(for:).
 */
class PackageStatusColorParityTest {

    @Test fun completedStatusesUseCompletedColor() {
        assertEquals(AlertPalette.Completed, packageStatusColor("Delivered"))
        assertEquals(AlertPalette.Completed, packageStatusColor("Complete"))
        assertEquals(AlertPalette.Completed, packageStatusColor("Ready for Pickup"))
        assertEquals(AlertPalette.Completed, packageStatusColor("Paid and Ready for Pick Up"))
    }

    @Test fun exceptionalStatesUseTheirSwiftAlertColors() {
        assertEquals(AlertPalette.OnHold, packageStatusColor("On Hold"))
        assertEquals(AlertPalette.Cancel, packageStatusColor("Cancelled"))
        assertEquals(AlertPalette.Error, packageStatusColor("Failed"))
        assertEquals(AlertPalette.Error, packageStatusColor("Payment Error"))
    }

    @Test fun inProgressStatesUsePendingColor() {
        assertEquals(AlertPalette.Pending, packageStatusColor("Pending"))
        assertEquals(AlertPalette.Pending, packageStatusColor("Processing at Customs"))
        assertEquals(AlertPalette.Pending, packageStatusColor("In-Transit to counter"))
    }

    @Test fun unknownAndArrivedStatusesKeepSwiftCompletedFallback() {
        assertEquals(AlertPalette.Completed, packageStatusColor("Arrived at Port -JAM"))
        assertEquals(AlertPalette.Completed, packageStatusColor("Shipment Received"))
        assertEquals(AlertPalette.Completed, packageStatusColor("Drop Alerted"))
        assertEquals(AlertPalette.Completed, packageStatusColor(null))
    }
}
