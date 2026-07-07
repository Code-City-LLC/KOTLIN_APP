package com.ga.airdrop.feature.dropalert

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [applyPreset] parity with Swift FigmaDropAlertViewController.applySavedShipperPreset
 * (incl. 9519615): shipper fills only where blank; courier fills only where
 * blank AND still a valid option; shipping method starts on the "Airdrop
 * standard" default, and a saved preset value WINS over that default.
 */
class DropAlertPresetTest {

    private val options = DropAlertViewModel.COURIER_COMPANY_OPTIONS

    @Test
    fun `form starts on the default shipping method`() {
        assertEquals("Airdrop standard", DropAlertUiState().shippingMethod)
    }

    @Test
    fun `fills a fresh form from the preset (saved method beats the default)`() {
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
    fun `never overwrites shipper or courier the user already filled`() {
        val typed = DropAlertUiState(
            shipper = "My Shipper",
            courierCompany = "UPS",
        )
        val out = applyPreset(typed, DropAlertPreset.Preset("X", "FedEx", "SeaDrop Standard"), options)
        assertEquals("My Shipper", out.shipper)
        assertEquals("UPS", out.courierCompany)
        // Shipping method: the saved value wins (Swift 9519615) — apply runs
        // only on fresh/reset forms, so nothing user-typed is at stake here.
        assertEquals("SeaDrop Standard", out.shippingMethod)
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
    fun `empty preset leaves the form untouched (default method stays)`() {
        val blank = DropAlertUiState()
        assertEquals(blank, applyPreset(blank, DropAlertPreset.Preset(), options))
        assertEquals("Airdrop standard", applyPreset(blank, DropAlertPreset.Preset(), options).shippingMethod)
    }
}
