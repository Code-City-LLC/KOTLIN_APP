package com.ga.airdrop.core.designsystem.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide text-size preference (Kemar directive 2026-07-12: "text size
 * should be in setting"). Mirrors Swift `AirdropTextSizePreference`
 * (DesignTokens.swift @ 8ce745c): four levels whose multiplier scales every
 * sp-sized text via the Density funnel in [AirdropTheme]. Same store/observe
 * shape as [ThemeController] so every screen reacts instantly and the value
 * survives restarts. PREFERENCES owns the canonical (and only) editor;
 * Settings' Text Size row just navigates there. Any new surface must reuse
 * this one store — never a second store or picker.
 */
object TextSizeController {

    enum class Level(val displayName: String, val fontMultiplier: Float) {
        SMALLER("Smaller", 0.92f),
        STANDARD("Standard", 1.00f),
        LARGER("Larger", 1.10f),
        LARGEST("Largest", 1.18f),
    }

    private const val PREFS = "airdrop_text_size"
    private const val KEY_LEVEL = "level"

    private lateinit var prefs: SharedPreferences

    var level by mutableStateOf(Level.STANDARD)
        private set

    fun init(context: Context) {
        initWithPrefs(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }

    // internal seam: persistence/restart/malformed-value behavior is pinned
    // by TextSizeControllerTest against an in-memory SharedPreferences.
    internal fun initWithPrefs(sharedPreferences: SharedPreferences) {
        prefs = sharedPreferences
        level = runCatching { Level.valueOf(prefs.getString(KEY_LEVEL, Level.STANDARD.name)!!) }
            .getOrDefault(Level.STANDARD)
    }

    fun set(newLevel: Level) {
        // In-memory value updates immediately so the UI reacts even if a
        // picker fires before init() binds prefs — same C7 crash guard as
        // ThemeController.set. commit() completes the preference write before
        // set() returns (unlike apply()'s async flush). The Boolean result is
        // intentionally ignored: a failed write only means the previous level
        // is read back at next init, which is the safe fallback.
        level = newLevel
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_LEVEL, newLevel.name).commit()
        }
    }
}
