package com.ga.airdrop.feature.dropalert

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [applyPreset] parity with Swift FigmaDropAlertViewController.applySavedShipperPreset:
 * fill shipper/method where blank; apply courier only when it's still a valid
 * option; never overwrite a field the user already typed.
 */
class DropAlertPresetTest {

    private val options = DropAlertViewModel.COURIER_COMPANY_OPTIONS

    @Test
    fun `fills all three blank fields from the preset`() {
        val out = applyPreset(
            DropAlertUiState(),
            DropAlertPreset.Preset("Amazon Store", "FedEx", "SeaDrop Standard"),
            options,
        )
        assertEquals("Amazon Store", out.shipper)
        assertEquals("FedEx", out.courierCompany)
        assertEquals("SeaDrop Standard", out.shippingMethod)
    }

    @Test
    fun `never overwrites fields the user already filled`() {
        val typed = DropAlertUiState(
            shipper = "My Shipper",
            courierCompany = "UPS",
            shippingMethod = "Airdrop standard",
        )
        val out = applyPreset(typed, DropAlertPreset.Preset("X", "FedEx", "SeaDrop Standard"), options)
        assertEquals(typed, out)
    }

    @Test
    fun `ignores a courier that is not a valid option`() {
        val out = applyPreset(
            DropAlertUiState(),
            DropAlertPreset.Preset("Shop", "Some Defunct Courier", "Airdrop standard"),
            options,
        )
        assertEquals("Shop", out.shipper)
        assertEquals("", out.courierCompany) // stale courier dropped
        assertEquals("Airdrop standard", out.shippingMethod)
    }

    @Test
    fun `empty preset leaves the form untouched`() {
        val blank = DropAlertUiState()
        assertEquals(blank, applyPreset(blank, DropAlertPreset.Preset(), options))
    }
}
