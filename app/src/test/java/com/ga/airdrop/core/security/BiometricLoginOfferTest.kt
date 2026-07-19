package com.ga.airdrop.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enrollment-offer rule for biometric sign-in (Kemar 2026-07-19): offered
 * once after a successful password login; never nags after a decline;
 * re-offered when a DIFFERENT account signs in over an existing vault.
 */
class BiometricLoginOfferTest {

    @Test
    fun `offered on first password login with a sensor`() {
        assertTrue(
            shouldOfferBiometricLogin(
                biometricsAvailable = true,
                vaultEnabled = false,
                vaultEmail = null,
                loginEmail = "a@b.com",
                offerDeclined = false,
            ),
        )
    }

    @Test
    fun `never offered without hardware or after a decline`() {
        assertFalse(
            shouldOfferBiometricLogin(
                biometricsAvailable = false,
                vaultEnabled = false,
                vaultEmail = null,
                loginEmail = "a@b.com",
                offerDeclined = false,
            ),
        )
        assertFalse(
            shouldOfferBiometricLogin(
                biometricsAvailable = true,
                vaultEnabled = false,
                vaultEmail = null,
                loginEmail = "a@b.com",
                offerDeclined = true,
            ),
        )
    }

    @Test
    fun `not re-offered for the same vaulted account, re-offered for a new one`() {
        assertFalse(
            shouldOfferBiometricLogin(
                biometricsAvailable = true,
                vaultEnabled = true,
                vaultEmail = "A@B.com",
                loginEmail = " a@b.com ",
                offerDeclined = false,
            ),
        )
        assertTrue(
            shouldOfferBiometricLogin(
                biometricsAvailable = true,
                vaultEnabled = true,
                vaultEmail = "old@b.com",
                loginEmail = "new@b.com",
                offerDeclined = false,
            ),
        )
    }
}
