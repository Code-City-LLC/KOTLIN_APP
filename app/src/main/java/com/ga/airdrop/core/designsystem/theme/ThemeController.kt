package com.ga.airdrop.core.designsystem.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide light/dark override, persisted across launches.
 * Android equivalent of FigmaAppTheme (Swift): user picks Dark/Light in
 * Settings; default follows the system.
 */
object ThemeController {

    enum class Mode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "airdrop_theme"
    private const val KEY_MODE = "mode"

    private lateinit var prefs: SharedPreferences

    var mode by mutableStateOf(Mode.SYSTEM)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = runCatching { Mode.valueOf(prefs.getString(KEY_MODE, Mode.SYSTEM.name)!!) }
            .getOrDefault(Mode.SYSTEM)
    }

    fun set(newMode: Mode) {
        // In-memory mode updates immediately so the UI reacts even if a toggle
        // fires before init() (e.g. a preview or a race at cold start). Persist
        // only once prefs is bound — accessing lateinit prefs early would throw
        // UninitializedPropertyAccessException (BUG_AUDIT C7).
        mode = newMode
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_MODE, newMode.name).apply()
        }
    }

    /**
     * The uiMode night bit this Mode forces for Android resource-qualifier
     * resolution (values-night: @color/icon_duotone, window_background,
     * themes-night). SYSTEM returns null — "don't override, pass the OS bit
     * through" (Swift FigmaAppTheme `.unspecified` parity). Consumed by
     * MainActivity.attachBaseContext so the whole activity's resources resolve
     * against ThemeController, in lockstep with Compose.
     */
    fun nightMask(): Int? = when (mode) {
        Mode.DARK -> Configuration.UI_MODE_NIGHT_YES
        Mode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        Mode.SYSTEM -> null
    }

    /** The effective dark bit for the current mode; SYSTEM reads the OS uiMode. */
    fun resolvedNight(context: Context): Boolean = when (mode) {
        Mode.DARK -> true
        Mode.LIGHT -> false
        Mode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}
