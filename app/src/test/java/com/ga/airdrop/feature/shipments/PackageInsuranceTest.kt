package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.api.ApiErrorCodes
import com.ga.airdrop.data.model.InsuranceSelection
import com.ga.airdrop.data.model.PackageTierInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The package-insurance select/decline brain (joint Swift/Kotlin flow), tested
 * through its pure state transitions. The backend owns every rule — premium
 * math, who may decline — so these pin that the page faithfully records what
 * the API answered, and that a refused decline (INSURANCE_MANDATORY) snaps
 * the sheet back to the covered option per the error_code pact.
 */
class PackageInsuranceTest {

    @Test
    fun `a recorded selection closes the sheet and shows Covered on the row`() {
        val before = PackageDetailsUiState(
            showInsuranceSheet = true,
            insuranceBusy = true,
            packageTierInfo = PackageTierInfo(packageId = 41, tierCode = "SAVR"),
        )
        val recorded = InsuranceSelection(
            selected = true,
            declined = false,
            insuredValue = 250.0,
            premium = 4.5,
            status = "recorded",
        )
        val after = applyInsuranceRecorded(before, recorded)

        assertFalse(after.showInsuranceSheet)
        assertFalse(after.insuranceBusy)
        assertNull(after.insuranceError)
        assertEquals(recorded, after.insuranceSelection)
        assertEquals("Covered", insuranceStatusLabel(after.insuranceSelection))
        // The tier snapshot (code etc.) survives the fold.
        assertEquals("SAVR", after.packageTierInfo!!.tierCode)
    }

    @Test
    fun `a recorded decline closes the sheet and shows Declined`() {
        val recorded = InsuranceSelection(selected = false, declined = true)
        val after = applyInsuranceRecorded(PackageDetailsUiState(showInsuranceSheet = true), recorded)

        assertFalse(after.showInsuranceSheet)
        assertEquals("Declined", insuranceStatusLabel(after.insuranceSelection))
    }

    @Test
    fun `INSURANCE_MANDATORY snaps back to covered - decline disappears, coded copy shows`() {
        val before = PackageDetailsUiState(showInsuranceSheet = true, insuranceBusy = true)
        val after = applyInsuranceFailure(
            before,
            errorCode = ApiErrorCodes.INSURANCE_MANDATORY,
            message = "Insurance is mandatory for your tier",
        )

        assertTrue(after.showInsuranceSheet) // stays open to show why
        assertTrue(after.insuranceDeclineRefused) // decline option removed
        assertFalse(after.insuranceBusy)
        assertEquals(
            "Insurance is required for your tier and can't be declined.",
            after.insuranceError,
        )
    }

    @Test
    fun `other failures keep decline available and surface the backend message`() {
        val after = applyInsuranceFailure(
            PackageDetailsUiState(insuranceBusy = true),
            errorCode = null,
            message = "Package not found",
        )

        assertFalse(after.insuranceDeclineRefused)
        assertEquals("Package not found", after.insuranceError)
    }

    @Test
    fun `row status label mirrors the recorded state`() {
        assertEquals("Choose", insuranceStatusLabel(null))
        assertEquals("Covered", insuranceStatusLabel(InsuranceSelection(selected = true)))
        assertEquals("Declined", insuranceStatusLabel(InsuranceSelection(declined = true)))
        // Nothing meaningfully recorded yet — still a choice to make.
        assertEquals("Choose", insuranceStatusLabel(InsuranceSelection()))
    }
}
