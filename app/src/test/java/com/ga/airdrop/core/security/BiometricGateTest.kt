package com.ga.airdrop.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature A — biometric app-lock gate rule. The soft-lockout safety is the
 * critical invariant (Swift requiresAuthOnLaunch = isEnabled && isAvailable):
 * a user who removed biometrics in OS settings must NOT be locked out.
 * BiometricManager/BiometricPrompt need a device, so the pure gate rule and the
 * guarded-lateinit preference are the JVM-verifiable layers; the live prompt is
 * device-verified separately.
 */
class BiometricGateTest {

    @Test
    fun `gate requires both enabled and available`() {
        assertTrue(BiometricGate.computeRequiresAuth(enabled = true, available = true))
        // Disabled biometrics in OS settings must never lock the user out.
        assertFalse(BiometricGate.computeRequiresAuth(enabled = true, available = false))
        // Opt-in off → never gates, even with hardware present.
        assertFalse(BiometricGate.computeRequiresAuth(enabled = false, available = true))
        assertFalse(BiometricGate.computeRequiresAuth(enabled = false, available = false))
    }

    @Test
    fun `reads and writes before init are safe no-ops`() {
        // init() is never called, so ::prefs stays unbound — the guard holds:
        // isEnabled defaults false, setter and reset() no-op without crashing.
        assertFalse(BiometricGate.isEnabled)
        BiometricGate.isEnabled = true
        assertFalse(BiometricGate.isEnabled)
        BiometricGate.reset()
    }
}
