package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity coverage for the Package Details charges-breakdown + Add-to-Cart gate.
 *
 * Source of truth: Swift `showCharges` (FigmaPackageDetailsViewController.swift L1265):
 * `statusInt == 7 || statusInt == 18` (7 = Ready for Pickup, 18 = Paid and Ready for
 * Pick Up). The Add-to-Cart CTA lives inside the same Swift `totalContainer` (L1107/1267),
 * so it shares this gate.
 *
 * Guards against the prior `statusInt >= 7` bug that leaked the section onto Delivered (8)
 * and in-transit/customs codes (9/10/12) — status codes are non-contiguous (Swift L1258-1261).
 */
class PackageDetailsChargesGatingTest {

    private fun show(status: String?): Boolean =
        PackageDetailsUiState(
            detail = ShipmentPackageDetail(id = 1, status = status),
            authoritativePackageId = 1,
        ).showChargesAndCart

    private fun canAdd(status: String?): Boolean =
        PackageDetailsUiState(
            detail = ShipmentPackageDetail(id = 1, status = status),
            authoritativePackageId = 1,
        ).canAddToCart

    @Test fun shown_onlyAt_readyForPickup_and_paidReadyForPickup() {
        assertTrue(show("7"))   // Ready for Pickup
        assertTrue(show("18"))  // Paid and Ready for Pick Up
    }

    @Test fun addToCartUsesCanonicalExactStatusSevenRule() {
        assertTrue(canAdd("7"))
        assertFalse(canAdd("18"))
        assertFalse(canAdd(null))
        assertFalse(canAdd("n/a"))
    }

    @Test fun hidden_atDelivered_andLaterCodes() {
        assertFalse(show("8"))   // Delivered
        assertFalse(show("14"))  // Proof of Delivery
        assertFalse(show("19"))  // Returned to Merchant
    }

    @Test fun hidden_atNonContiguousHighCodes_thatOldGateLeaked() {
        // Old `statusInt >= 7` wrongly showed these — all numerically >= 7 but
        // workflow-earlier than pickup: Processing at Customs (9), Detained (10),
        // In-Transit to counter (12).
        assertFalse(show("9"))
        assertFalse(show("10"))
        assertFalse(show("12"))
    }

    @Test fun hidden_atEarlyCodes_andUnknown() {
        assertFalse(show("1"))   // Drop Alerted
        assertFalse(show("5"))   // Released From Customs
        assertFalse(show("6"))   // Processing at our Warehouse
        assertFalse(show(null))
        assertFalse(show("n/a"))
    }

    @Test fun notificationPreviewNeverEnablesReadyStatusActions() {
        val preview = ShipmentPackageDetail(
            id = 42,
            status = "7",
            statusName = "Ready for Pickup",
        )

        val state = PackageDetailsUiState(detail = preview)

        assertFalse(state.hasAuthoritativeDetail)
        assertFalse(state.showChargesAndCart)
        assertFalse(state.canAddToCart)
        assertFalse(state.canDeleteInvoices)
    }

    @Test fun mismatchedAuthoritativeIdNeverEnablesPaidReadyActions() {
        val state = PackageDetailsUiState(
            detail = ShipmentPackageDetail(id = 42, status = "18"),
            authoritativePackageId = 99,
        )

        assertFalse(state.hasAuthoritativeDetail)
        assertFalse(state.showChargesAndCart)
        assertFalse(state.canAddToCart)
    }
}
