package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity coverage for Package Details invoice **delete/trash** gating (QC correction #14710).
 *
 * Source of truth: Swift `FigmaPackageDetailsViewController.canDeleteInvoices(for:)`
 * (FigmaPackageDetailsViewController.swift L1473-1485):
 *   - false if `Int(status) >= 7`
 *   - else false if `statusName` contains ready / pickup / pick up
 *   - else false if `statusName` contains delivered / complete
 *   - else true
 *
 * The single `PackageDetailsUiState.canDeleteInvoices` predicate drives BOTH surfaces:
 * the Screen hides the trash icon (`canDelete = state.canDeleteInvoices`) AND the
 * ViewModel guard makes `requestDeleteInvoice` inert — so exercising the predicate
 * proves "delete/trash is gone OR inert at status 7+" for both.
 *
 * Upload is intentionally NOT gated (the Swift upload zone and Kotlin `UploadInvoiceZone`
 * are ungated), so upload stays reachable at every status including status 7.
 */
class PackageDetailsInvoiceGatingTest {

    private fun canDelete(status: String?, statusName: String? = null): Boolean =
        PackageDetailsUiState(
            detail = ShipmentPackageDetail(id = 1, status = status, statusName = statusName),
        ).canDeleteInvoices

    // ── Numeric status < 7 (pre-pickup): delete allowed ──────────────────────
    @Test fun deleteAllowed_whenNumericStatusBelow7() {
        assertTrue(canDelete("1", "Drop Alerted"))
        assertTrue(canDelete("5", "Released From Customs"))
        assertTrue(canDelete("6", "Processing at our Warehouse"))
    }

    // ── Numeric status >= 7 (Ready for Pickup and later): delete blocked ──────
    @Test fun deleteBlocked_whenNumericStatus7OrLater() {
        assertFalse(canDelete("7", "Ready for Pickup"))
        assertFalse(canDelete("8", "Delivered"))
        assertFalse(canDelete("18", "Paid and Ready for Pick Up"))
    }

    /**
     * Swift keeps the numeric `>= 7` predicate for DELETE on purpose (charges use a
     * different `== 7 || == 18`), so codes 9/10/12 — although workflow-earlier — are
     * blocked by `>= 7` in BOTH apps. We match Swift exactly rather than "correct" it.
     */
    @Test fun deleteBlocked_matchesSwiftNumericPredicate_forHighCodes() {
        assertFalse(canDelete("9"))
        assertFalse(canDelete("10"))
        assertFalse(canDelete("12"))
    }

    // ── statusName fallback: the regression the old `statusInt >= 7` gate missed ──
    // Old Kotlin gate was `!readyForPickup` == `!(detail?.status?.toIntOrNull() ?: 0 >= 7)`,
    // so a null/non-numeric `status` defaulted to 0 -> delete WRONGLY shown even when
    // statusName said the package was ready/delivered. Swift caught these by name.
    @Test fun deleteBlocked_viaStatusNameFallback_whenNumericStatusMissing() {
        assertFalse(canDelete(status = null, statusName = "Ready for Pickup"))
        assertFalse(canDelete(status = null, statusName = "Ready For Pick Up"))
        assertFalse(canDelete(status = null, statusName = "Delivered"))
        assertFalse(canDelete(status = null, statusName = "Order Complete"))
    }

    @Test fun deleteBlocked_viaStatusNameFallback_whenNumericStatusNonNumeric() {
        assertFalse(canDelete(status = "", statusName = "Ready for Pickup"))
        assertFalse(canDelete(status = "n/a", statusName = "Delivered"))
    }

    // ── Default-allow when neither numeric nor name indicates ready/delivered ──
    @Test fun deleteAllowed_whenStatusUnknownOrEarly() {
        assertTrue(canDelete(status = null, statusName = null))
        assertTrue(canDelete(status = null, statusName = "Drop Alerted"))
        assertTrue(canDelete(status = "n/a", statusName = "Processing at Customs"))
    }
}
