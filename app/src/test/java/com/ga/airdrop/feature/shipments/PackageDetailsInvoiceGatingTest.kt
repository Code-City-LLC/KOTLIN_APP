package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity coverage for Package Details invoice **delete/trash** gating (QC correction #14710).
 *
 * Source of truth: Swift `FigmaPackageDetailsViewController.canDeleteInvoices(for:)`
 * (FigmaPackageDetailsViewController.swift L1473-1485):
 *   - false for explicit IDs 7, 8, 14-20 (excluding gaps)
 *   - else false when the status/catalog name describes a locked terminal state
 *   - else true
 *
 * The single `PackageDetailsUiState.canDeleteInvoices` predicate drives BOTH surfaces:
 * the Screen hides the trash icon (`canDelete = state.canDeleteInvoices`) AND the
 * ViewModel guard makes `requestDeleteInvoice` inert — so exercising the predicate
 * proves the delete/trash action is hidden or inert for every locked state.
 *
 * Upload is intentionally NOT gated (the Swift upload zone and Kotlin `UploadInvoiceZone`
 * are ungated), so upload stays reachable at every status including status 7.
 */
class PackageDetailsInvoiceGatingTest {

    private fun canDelete(status: String?, statusName: String? = null): Boolean =
        PackageDetailsUiState(
            detail = ShipmentPackageDetail(id = 1, status = status, statusName = statusName),
            authoritativePackageId = 1,
        ).canDeleteInvoices

    // ── Numeric status < 7 (pre-pickup): delete allowed ──────────────────────
    @Test fun deleteAllowed_whenNumericStatusBelow7() {
        assertTrue(canDelete("1", "Drop Alerted"))
        assertTrue(canDelete("5", "Released From Customs"))
        assertTrue(canDelete("6", "Processing at our Warehouse"))
    }

    @Test fun deleteBlocked_whenNumericStatusIsInSwiftLockSet() {
        assertFalse(canDelete("7", "Ready for Pickup"))
        assertFalse(canDelete("8", "Delivered"))
        assertFalse(canDelete("14"))
        assertFalse(canDelete("15"))
        assertFalse(canDelete("16"))
        assertFalse(canDelete("17"))
        assertFalse(canDelete("18", "Paid and Ready for Pick Up"))
        assertFalse(canDelete("19"))
        assertFalse(canDelete("20"))
    }

    @Test fun deleteAllowed_whenNumericStatusIsAHighButUnlockedGap() {
        assertTrue(canDelete("9"))
        assertTrue(canDelete("10"))
        assertTrue(canDelete("12"))
    }

    // ── statusName fallback: the regression the old `statusInt >= 7` gate missed ──
    // Old Kotlin gate was `!readyForPickup` == `!(detail?.status?.toIntOrNull() ?: 0 >= 7)`,
    // so a null/non-numeric `status` defaulted to 0 -> delete WRONGLY shown even when
    // statusName said the package was ready/delivered. Swift caught these by name.
    @Test fun deleteBlocked_viaStatusNameFallback_whenNumericStatusMissing() {
        assertFalse(canDelete(status = null, statusName = "Ready for Pickup"))
        assertFalse(canDelete(status = null, statusName = "Ready For Pick Up"))
        assertFalse(canDelete(status = null, statusName = "Delivered"))
        assertFalse(canDelete(status = null, statusName = "Ready for Delivery"))
        assertFalse(canDelete(status = null, statusName = "Order Complete"))
        assertFalse(canDelete(status = null, statusName = "Returned to Merchant"))
        assertFalse(canDelete(status = null, statusName = "Uncollected Packages"))
        assertFalse(canDelete(status = null, statusName = "Dangerous Goods"))
        assertFalse(canDelete(status = null, statusName = "Auction"))
        assertFalse(canDelete(status = null, statusName = "Sale"))
    }

    @Test fun saleLabelPreservesTheBackendAuctionStatusAlias() {
        assertTrue(ShipmentStatusCatalog.defaults.any { it.id == 17 && it.name == "Sale" })
        assertTrue(ShipmentStatusCatalog.idFor("Auction") == 17)
        assertTrue(ShipmentStatusCatalog.idFor("Sale") == 17)
        assertTrue(ShipmentStatusCatalog.customerFacingName("Auction") == "Sale")
        assertTrue(ShipmentStatusCatalog.customerFacingName("17") == "17")
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

    // ── Swift 5496ed0 tolerance (Kemar invoice ruling, cross-platform) ──
    // The numeric lock is evaluated on BOTH fields, and accepts floating /
    // comma-decimal codes.
    @Test fun deleteBlocked_whenStatusNameCarriesTheNumericCode() {
        assertFalse(canDelete(status = null, statusName = "7"))
        assertFalse(canDelete(status = "n/a", statusName = "18"))
    }

    @Test fun deleteBlocked_whenNumericStatusIsDecimalOrCommaDecimal() {
        assertFalse(canDelete("7.0"))
        assertFalse(canDelete("7,0"))
    }

    @Test fun deleteAllowed_whenDecimalStatusBelow7() {
        assertTrue(canDelete("6.5"))
        assertTrue(canDelete("7.5"))
    }
}
