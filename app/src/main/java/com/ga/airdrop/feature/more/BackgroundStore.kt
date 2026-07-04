package com.ga.airdrop.feature.more

import android.content.Context
import com.ga.airdrop.R

/**
 * Home hero background selection — Android port of the Swift
 * FigmaBackgroundImagesViewController persistence (UserDefaults key
 * "BACKGROUND_IMAGE_ID"). ID 0 is the theme-aware default (Figma
 * 40000710:5347 light / 40000710:5667 dark); IDs 1–13 map to the bundled
 * back_color set copied verbatim from the iOS asset catalog.
 *
 * RECONCILE: HomeScreen should replace its hard-coded hero drawable with
 * `BackgroundStore.currentBackgroundRes(context, isDark)` (orchestrator).
 */
object BackgroundStore {

    const val DEFAULT_ID = 0
    private const val PREFS = "airdrop_background"
    private const val KEY_ID = "BACKGROUND_IMAGE_ID"

    data class Choice(val id: Int, val lightRes: Int, val darkRes: Int, val isDefault: Boolean)

    val choices: List<Choice> = listOf(
        Choice(0, R.drawable.img_more_bg_default_light, R.drawable.img_more_bg_default_dark, true),
        Choice(1, R.drawable.img_more_bg_1, R.drawable.img_more_bg_1, false),
        Choice(2, R.drawable.img_more_bg_2, R.drawable.img_more_bg_2, false),
        Choice(3, R.drawable.img_more_bg_3, R.drawable.img_more_bg_3, false),
        Choice(4, R.drawable.img_more_bg_4, R.drawable.img_more_bg_4, false),
        Choice(5, R.drawable.img_more_bg_5, R.drawable.img_more_bg_5, false),
        Choice(6, R.drawable.img_more_bg_6, R.drawable.img_more_bg_6, false),
        Choice(7, R.drawable.img_more_bg_7, R.drawable.img_more_bg_7, false),
        Choice(8, R.drawable.img_more_bg_8, R.drawable.img_more_bg_8, false),
        Choice(9, R.drawable.img_more_bg_9, R.drawable.img_more_bg_9, false),
        Choice(10, R.drawable.img_more_bg_10, R.drawable.img_more_bg_10, false),
        Choice(11, R.drawable.img_more_bg_11, R.drawable.img_more_bg_11, false),
        Choice(12, R.drawable.img_more_bg_12, R.drawable.img_more_bg_12, false),
        Choice(13, R.drawable.img_more_bg_13, R.drawable.img_more_bg_13, false),
    )

    fun selectedId(context: Context): Int =
        prefs(context).getInt(KEY_ID, DEFAULT_ID)

    fun save(context: Context, id: Int) {
        prefs(context).edit().putInt(KEY_ID, id).apply()
    }

    /** Resolves the drawable for the saved choice, theme-aware for the default. */
    fun currentBackgroundRes(context: Context, isDark: Boolean): Int {
        val choice = choices.firstOrNull { it.id == selectedId(context) }
            ?: choices.first()
        return if (isDark) choice.darkRes else choice.lightRes
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
