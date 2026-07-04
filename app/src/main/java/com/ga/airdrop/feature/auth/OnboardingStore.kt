package com.ga.airdrop.feature.auth

import android.content.Context

/**
 * Persists the "onboarding seen" flag so the carousel only shows on first
 * run, mirroring the RN AsyncStorage onboarding flag.
 */
object OnboardingStore {

    private const val PREFS = "airdrop_onboarding"
    private const val KEY_SEEN = "seen"

    fun hasSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN, true).apply()
    }
}
