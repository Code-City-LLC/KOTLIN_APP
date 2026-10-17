package com.ga.airdrop.feature.auth

import android.content.Context

/**
 * Owns the two intentional onboarding entry rules:
 *
 * 1. A new installation sees onboarding before authentication.
 * 2. A customer who explicitly signs out sees onboarding after the next
 *    successful login, before Home.
 *
 * Keeping the post-logout gate separate from [KEY_SEEN] prevents logout from
 * replaying onboarding before the customer has logged back in.
 */
object OnboardingStore {

    private const val PREFS = "airdrop_onboarding"
    private const val KEY_SEEN = "seen"
    private const val KEY_REQUIRED_AFTER_LOGIN = "required_after_login"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEEN, false)

    fun isRequiredAfterLogin(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRED_AFTER_LOGIN, false)

    fun requireAfterNextLogin(context: Context) {
        prefs(context).edit().putBoolean(KEY_REQUIRED_AFTER_LOGIN, true).apply()
    }

    fun markSeen(context: Context) {
        prefs(context)
            .edit()
            .putBoolean(KEY_SEEN, true)
            .remove(KEY_REQUIRED_AFTER_LOGIN)
            .apply()
    }
}
