package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Swift `FigmaPackageDetailsViewController.canDeleteInvoices(for:)` parity.
 *
 * Upload stays available, but existing invoice delete/trash locks once the
 * package is ready for pickup/status 7+ or the status name is already terminal.
 */
class PackageDetailsInvoiceGatingTest {

    @Test
    fun statusSevenAndLaterLockInvoiceDeletes() {
        assertFalse(packageInvoicesCanDelete(status = "7", statusName = "Ready for Pickup"))
        assertFalse(packageInvoicesCanDelete(status = "18", statusName = "Paid and Ready for Pick Up"))
        assertFalse(packageInvoicesCanDelete(status = " 9 ", statusName = "Processing at Customs"))
    }

    @Test
    fun statusNameFallbackLocksReadyPickupAndTerminalPackages() {
        assertFalse(packageInvoicesCanDelete(status = null, statusName = "Ready for Pickup"))
        assertFalse(packageInvoicesCanDelete(status = "nil", statusName = "Ready for Pick-Up"))
        assertFalse(packageInvoicesCanDelete(status = "-", statusName = "Paid and Ready for Pick Up"))
        assertFalse(packageInvoicesCanDelete(status = "6", statusName = "Delivered"))
        assertFalse(packageInvoicesCanDelete(status = null, statusName = "Completed"))
    }

    @Test
    fun preReadyOrUnknownPackagesCanStillDeleteInvoices() {
        assertTrue(packageInvoicesCanDelete(status = "6", statusName = "Processing at Warehouse"))
        assertTrue(packageInvoicesCanDelete(status = null, statusName = null))
        assertTrue(packageInvoicesCanDelete(status = "nil", statusName = "-"))
    }
}
