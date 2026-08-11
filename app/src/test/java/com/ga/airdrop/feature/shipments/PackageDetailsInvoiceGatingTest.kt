package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
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
 * ⚠️ Upload is gated too, and the two rules are related but NOT the same:
 *
 *   Upload and delete share the recognized locked-status predicate
 *   (`statusLocksInvoiceDeletion`) and nothing more. Upload ADDITIONALLY requires a
 *   recognized status and fails CLOSED on absent/unknown/unparseable; delete stays
 *   independent and fail-OPEN there.
 *
 * Both sets of cases live in this one file so the shared half stays shared and the
 * divergent half stays visible — `uploadAndDeleteDivergeOnAbsentAndUnknownStatus`
 * pins the divergence deliberately.
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

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD — shares the recognized locked-status boundary above, then diverges:
    // it also requires a RECOGNIZED status and fails CLOSED on absent/unknown.
    //
    // Pinned here beside delete so the shared half cannot drift and the divergent
    // half cannot be "tidied" into an alias. A gate on DELIVERED (8) alone leaves
    // the live drop zone on every ready-to-collect package.
    // ─────────────────────────────────────────────────────────────────────────

    private fun canUpload(status: String?, statusName: String? = null): Boolean =
        PackageDetailsUiState(
            detail = ShipmentPackageDetail(id = 1, status = status, statusName = statusName),
        ).canUploadInvoices

    /**
     * ⚠️ `detail == null` is the state the screen is in while loading, and the
     * one `canDeleteInvoices` deliberately treats as permissive. Upload must not
     * inherit that: no package loaded is not proof of an eligible package.
     */
    @Test fun uploadBlocked_whenDetailIsAbsentEntirely() {
        assertFalse(
            "a null detail must never be eligible for a mutation",
            PackageDetailsUiState(detail = null).canUploadInvoices,
        )
        assertTrue(
            "delete's documented fail-open on a null detail is untouched by this task",
            PackageDetailsUiState(detail = null).canDeleteInvoices,
        )
    }

    @Test fun uploadAllowed_beforeReadyForPickup() {
        assertTrue(canUpload("1", "Drop Alerted"))
        assertTrue(canUpload("5", "Released From Customs"))
        assertTrue(canUpload("6", "Processing at our Warehouse"))
    }

    @Test fun uploadBlocked_atReadyForPickupAndEveryLaterLockedState() {
        // AT the boundary, not one past it — 7 and 18 are collectable.
        assertFalse(canUpload("7", "Ready for Pickup"))
        assertFalse(canUpload("18", "Paid and Ready for Pick Up"))
        assertFalse(canUpload("8", "Delivered"))
        assertFalse(canUpload("14"))
        assertFalse(canUpload("15"))
        assertFalse(canUpload("16"))
        assertFalse(canUpload("17"))
        assertFalse(canUpload("19"))
        assertFalse(canUpload("20"))
    }

    @Test fun uploadBlocked_viaTerminalStatusName_whenCodeIsUnusable() {
        assertFalse(canUpload(status = null, statusName = "Delivered"))
        assertFalse(canUpload(status = "n/a", statusName = "Ready for Pickup"))
        assertFalse(canUpload(status = "", statusName = "Uncollected Packages"))
        // Either field may carry the code, and decimals/commas are tolerated —
        // same tolerance the delete path was audited for.
        assertFalse(canUpload(status = null, statusName = "7"))
        assertFalse(canUpload("7,0"))
        assertFalse(canUpload("18.0"))
    }

    @Test fun uploadAllowed_whenNumericStatusIsAHighButUnlockedGap() {
        assertTrue(canUpload("9"))
        assertTrue(canUpload("10"))
        assertTrue(canUpload("12"))
    }

    /**
     * ⚠️ PINS THE UNKNOWN POLICY: **BLOCK**. Upload fails CLOSED.
     *
     * An earlier ruling (and an earlier version of this test) allowed unknown,
     * on the reasoning that the cost was one pointless upload. That reasoning
     * assumed a server-side backstop. There is none — **Laravel does not reject
     * the unsafe upload** — so this predicate is the only gate, and an
     * unrecognised status writing an attachment onto a possibly-closed shipment
     * is a real write. ORC 95620/95648.
     */
    @Test fun uploadBlocked_whenNothingIsRecognizable() {
        assertFalse("both fields absent must not be treated as eligible", canUpload(null, null))
        assertFalse(canUpload(status = "???", statusName = "Unknown"))
        assertFalse(canUpload(status = "999"))
        assertFalse(canUpload(status = "n/a"))
        assertFalse(canUpload(status = "", statusName = ""))
        assertFalse(canUpload(status = "   ", statusName = null))
    }

    /**
     * ⚠️ THE `"???"` TRAP, PINNED (ORC 95657).
     *
     * `ShipmentStatusCatalog.idFor` strips punctuation then falls back to
     * `contains` matching — so `"???"` normalises to the EMPTY string, every
     * catalog name contains it, and an unguarded lookup resolves to status **1**,
     * silently re-opening upload for the most obviously unreadable input there
     * is. This pins that punctuation-only and blank values stay unknown, and that
     * generic partial text is never mistaken for a proven stage.
     */
    @Test fun uploadBlocked_forPunctuationOnlyAndPartialNameMatches() {
        assertFalse("punctuation-only must not resolve via contains-matching", canUpload("???"))
        assertFalse(canUpload("!!!"))
        assertFalse(canUpload("-"))
        // Generic fragments of real catalog names are NOT proof of a stage.
        assertFalse(canUpload(status = null, statusName = "Processing"))
        assertFalse(canUpload(status = null, statusName = "Shipment"))
        assertFalse(canUpload(status = null, statusName = "at"))
    }

    /** Exact catalog names ARE recognised — the gate must not be uselessly strict. */
    @Test fun uploadAllowed_forExactPreReadyCatalogNames() {
        assertTrue(canUpload(status = null, statusName = "Processing at our Warehouse"))
        assertTrue(canUpload(status = null, statusName = "Drop Alerted"))
        assertTrue(canUpload(status = null, statusName = "Processing at Customs"))
        // Normalisation tolerates case and punctuation differences.
        assertTrue(canUpload(status = null, statusName = "processing at our warehouse"))
    }

    /** Lock precedence: when the two fields disagree, the lock decides. */
    @Test fun uploadBlocked_whenAnyFieldLocksEvenIfTheOtherLooksEligible() {
        assertFalse(canUpload(status = "6", statusName = "Delivered"))
        assertFalse(canUpload(status = "Processing at our Warehouse", statusName = "7"))
        assertFalse(canUpload(status = "1", statusName = "Ready for Pickup"))
    }

    /**
     * ⚠️ UPLOAD IS NOT THE INVERSE OF DELETE, AND MUST NEVER BE SIMPLIFIED INTO IT.
     *
     * A previous version of this file asserted the opposite — that the two gates
     * always agree — which encoded the wrong invariant (ORC 95648). `canDelete`
     * deliberately fails OPEN when detail/status is absent, because hiding a
     * delete affordance is the lesser harm; upload fails CLOSED because it
     * writes. This pins the divergence so nobody "tidies" them back together.
     */
    @Test fun uploadAndDeleteDivergeOnAbsentAndUnknownStatus() {
        assertTrue("delete keeps its documented fail-open", canDelete(null, null))
        assertFalse("upload must fail closed on the same input", canUpload(null, null))

        assertTrue(canDelete("999"))
        assertFalse(canUpload("999"))

        // Where a status IS recognised and locked, they agree — that is the
        // overlap, not an invariant to generalise from.
        assertFalse(canDelete("7", "Ready for Pickup"))
        assertFalse(canUpload("7", "Ready for Pickup"))
    }
}
