package com.ga.airdrop.core.security

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Biometric app-lock — Swift `BiometricGate` parity (UserDefaults key
 * "Airdrop.biometric.enabled", opt-in, default false). Owns the on/off
 * preference, hardware introspection, and the BiometricPrompt evaluation.
 * [reset] clears the key on logout (both hygiene paths) so a shared device
 * doesn't carry the previous user's preference.
 *
 * Cold-launch gate only (matches Swift — locks on cold launch, not every
 * foreground). [requiresAuthOnLaunch] is `isEnabled && isAvailable`, so a user
 * who removed biometrics in OS settings while backgrounded is NEVER locked out.
 *
 * Guarded-lateinit (C7/C8): reads/writes before [init] no-op instead of
 * throwing.
 */
object BiometricGate {

    private const val PREFS = "airdrop_security"
    private const val KEY_ENABLED = "Airdrop.biometric.enabled"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var isEnabled: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun reset() {
        if (::prefs.isInitialized) prefs.edit().remove(KEY_ENABLED).apply()
    }

    /**
     * Swift .deviceOwnerAuthentication = biometric WITH device-credential
     * fallback. DEVICE_CREDENTIAL combined with BIOMETRIC_STRONG is only
     * supported without a CryptoObject on API 30+; below that fall back to
     * strong biometrics + an explicit negative button.
     */
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }

    /** Hardware present AND at least one credential enrolled. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Android has no per-type name from BiometricPrompt (Swift's
     * "Face ID"/"Touch ID"); resolve a coarse label from PackageManager
     * features, defaulting to "Biometric". Never hardcode "Face ID" on Android.
     */
    fun biometricTypeName(context: Context): String {
        val pm = context.packageManager
        val hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
        val hasFinger = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        return when {
            hasFace && !hasFinger -> "Face"
            hasFinger && !hasFace -> "Fingerprint"
            else -> "Biometric"
        }
    }

    fun requiresAuthOnLaunch(context: Context): Boolean =
        computeRequiresAuth(isEnabled, isAvailable(context))

    /** Pure gate rule (unit-testable): never lock without both flags. */
    fun computeRequiresAuth(enabled: Boolean, available: Boolean): Boolean =
        enabled && available

    /**
     * Swift authenticate(reason:): returns true only on a successful auth;
     * false on cancel / error / lockout (no specific error surfaced). A single
     * non-matching attempt keeps the prompt up (no resume).
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        reason: String,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // One non-matching attempt — the prompt stays up for retry.
                }
            },
        )
        val authenticators = allowedAuthenticators()
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AirDrop")
            .setSubtitle(reason)
            .setAllowedAuthenticators(authenticators)
        if (authenticators and DEVICE_CREDENTIAL == 0) {
            builder.setNegativeButtonText("Cancel")
        }
        prompt.authenticate(builder.build())
    }
}
