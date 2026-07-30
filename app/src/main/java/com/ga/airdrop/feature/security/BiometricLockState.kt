package com.ga.airdrop.feature.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Holds whether the biometric app-lock has been satisfied for the CURRENT
 * process.
 *
 * ⚠️ This is a ViewModel on purpose, and the choice is the whole fix.
 *
 * The unlocked flag used to live in `rememberSaveable`, which writes into the
 * saved-instance-state Bundle and therefore survives **process death**. Android
 * reaps backgrounded processes as a matter of course, and on relaunch the
 * restored `false` overwrote the freshly-computed `lockedAtLaunch = true`, so
 * the gate never appeared. Nothing re-locks on resume — it is a cold-launch
 * gate (Swift `SceneDelegate.presentBiometricLockIfNeeded`) — so the app-lock
 * effectively stopped existing after the first background/reap cycle.
 *
 * ViewModel scoping gives the exact semantics required:
 *  - survives configuration changes, including the deliberate `recreate()` when
 *    the night bit flips (so plain `remember` would be wrong — it would
 *    re-prompt on every theme change);
 *  - dies with the process, so a genuine cold launch always re-prompts.
 */
class BiometricLockState : ViewModel() {

    // Compose state, not a plain var: unlock() must recompose the gate away.
    private var unlocked by mutableStateOf(false)

    /** True while the gate should be shown. [lockedAtLaunch] is the opt-in check. */
    fun locked(lockedAtLaunch: Boolean): Boolean = lockedAtLaunch && !unlocked

    fun unlock() {
        unlocked = true
    }
}
