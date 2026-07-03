package com.ga.airdrop.core.designsystem.theme

import android.content.Context
import android.content.SharedPreferences
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
        mode = newMode
        prefs.edit().putString(KEY_MODE, newMode.name).apply()
    }
}
