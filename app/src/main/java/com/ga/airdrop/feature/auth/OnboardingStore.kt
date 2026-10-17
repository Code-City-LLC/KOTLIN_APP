package com.ga.airdrop.feature.auth

import android.content.Context

/**
 * Owns the two intentional onboarding entry rules:
 *
 * 1. A new installation sees onboarding before authentication.
 * 2. A customer who explicitly signs out sees onboarding straight away,
 *    before the auth landing (React Native order, Kemar 2026-10-17:
 *    SettingsView.onLogout resets the stack to OnboardingView). It used to
 *    wait for the next successful login and play before Home.
 *
 * The logout flag is separate from [KEY_SEEN] and stays armed from the logout
 * until the slides finish ([markSeen] clears it). While it is armed, AppRoot's
 * token-loss reset and a cold start both route to ONBOARDING, so the slides
 * resume if the app closed partway through. Once they finish, the next login
 * goes straight to Home, so a logout plays them once.
 *
 * The names [isRequiredAfterLogin] / [requireAfterNextLogin] and the stored
 * key predate this order. The key is kept so a flag armed by an older build
 * still counts.
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
