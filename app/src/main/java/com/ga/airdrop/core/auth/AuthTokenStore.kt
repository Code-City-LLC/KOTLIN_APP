package com.ga.airdrop.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sanctum bearer-token storage. Android equivalent of the Swift
 * AuthTokenStore (Keychain-backed): encrypted at rest, survives restarts,
 * cleared on logout.
 */
object AuthTokenStore {

    private const val PREFS = "airdrop_auth"
    private const val KEY_TOKEN = "api_token"

    private lateinit var prefs: SharedPreferences

    private val _token = MutableStateFlow<String?>(null)
    val tokenFlow: StateFlow<String?> get() = _token

    val token: String? get() = _token.value

    fun init(context: Context) {
        prefs = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Keystore corruption fallback: plain prefs beat a hard crash at
            // launch; the token is re-issued at next login anyway.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
        _token.value = prefs.getString(KEY_TOKEN, null)
    }

    fun save(token: String) {
        // Update the in-memory flow first, then persist only if prefs is bound.
        // A background service / ContentProvider can run before
        // Application.onCreate() calls init(); touching lateinit prefs then
        // would throw UninitializedPropertyAccessException (BUG_AUDIT C8).
        _token.value = token
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
    }

    fun clear() {
        _token.value = null
        if (::prefs.isInitialized) {
            prefs.edit().remove(KEY_TOKEN).apply()
        }
    }
}
