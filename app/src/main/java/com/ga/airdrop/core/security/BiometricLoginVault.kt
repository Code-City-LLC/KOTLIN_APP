package com.ga.airdrop.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Biometric SIGN-IN (Kemar 2026-07-19: "biometrics on Android to be able
 * to be used for logging in") — distinct from [BiometricGate]'s launch
 * lock. The backend has no biometric/passkey auth, so this is the standard
 * credential-vault pattern: after a successful password login the customer
 * can opt in; the credentials land in Keystore-backed
 * EncryptedSharedPreferences (same protection class as the bearer token in
 * AuthTokenStore), and the Login screen offers "Sign in with biometrics" —
 * fingerprint/face unlocks the vault and replays POST /auth/login for a
 * fresh session. The vault survives logout on purpose; disabling clears it.
 *
 * Hardening path (documented, not yet needed at the app's current
 * posture): bind the entry to a Keystore key with
 * setUserAuthenticationRequired via BiometricPrompt.CryptoObject so the
 * ciphertext is undecryptable without the sensor even off-process.
 */
object BiometricLoginVault {

    private const val PREFS_NAME = "airdrop.biometric.login"
    private const val KEY_EMAIL = "email"
    private const val KEY_SECRET = "secret"
    private const val KEY_OFFER_DECLINED = "offer_declined"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = runCatching {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }

    internal fun restoreForTests(storage: SharedPreferences) {
        prefs = storage
    }

    fun isEnabled(): Boolean = !savedEmail().isNullOrBlank() &&
        prefs?.getString(KEY_SECRET, null).isNullOrBlank().not()

    fun savedEmail(): String? = prefs?.getString(KEY_EMAIL, null)

    fun offerDeclined(): Boolean = prefs?.getBoolean(KEY_OFFER_DECLINED, false) ?: false

    fun setOfferDeclined() {
        prefs?.edit()?.putBoolean(KEY_OFFER_DECLINED, true)?.apply()
    }

    /** Opt in with the credentials that just authenticated successfully. */
    fun enable(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) return
        prefs?.edit()
            ?.putString(KEY_EMAIL, cleanEmail)
            ?.putString(KEY_SECRET, password)
            ?.putBoolean(KEY_OFFER_DECLINED, false)
            ?.apply()
    }

    /** Turn biometric sign-in off and destroy the stored credentials. */
    fun disable() {
        prefs?.edit()
            ?.remove(KEY_EMAIL)
            ?.remove(KEY_SECRET)
            ?.apply()
    }

    data class Credentials(val email: String, val password: String)

    /**
     * Fingerprint/face gate → credentials. Null on cancel, sensor error,
     * or an empty vault. The caller feeds the result straight into the
     * normal login path and never persists it anywhere else.
     */
    suspend fun unlock(activity: FragmentActivity): Credentials? {
        val email = savedEmail()?.takeIf(String::isNotBlank) ?: return null
        val secret = prefs?.getString(KEY_SECRET, null)?.takeIf(String::isNotEmpty)
            ?: return null
        val passed = BiometricGate.authenticate(
            activity,
            "Sign in as $email",
        )
        return if (passed) Credentials(email, secret) else null
    }
}

/**
 * Post-login enrollment offer rule (pure): offer once after a successful
 * PASSWORD login when the sensor exists, the vault is empty or belongs to
 * a different account, and the customer hasn't declined before.
 */
internal fun shouldOfferBiometricLogin(
    biometricsAvailable: Boolean,
    vaultEnabled: Boolean,
    vaultEmail: String?,
    loginEmail: String,
    offerDeclined: Boolean,
): Boolean {
    if (!biometricsAvailable || offerDeclined) return false
    if (!vaultEnabled) return true
    return !vaultEmail.equals(loginEmail.trim(), ignoreCase = true)
}
