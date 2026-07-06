package com.ga.airdrop.core.push

import android.content.Context
import java.util.Calendar

/**
 * Quiet Hours — Swift `QuietHoursStore` parity (UserDefaults keys
 * "Airdrop.quietHours.{enabled,startMinutes,endMinutes}"). A fully client-side
 * enable-toggle + start/end window (minutes since midnight, 0..1439) marking
 * when foreground FCM notifications should be delivered SILENTLY — they still
 * land in the shade, they are never dropped. Default OFF; default window
 * 10:00 PM → 7:00 AM; overnight-wrap aware. Device-local, cleared on logout
 * via [clear].
 *
 * Context-per-call (BackgroundStore template) — no init()/lateinit. The window
 * math ([inWindow]) and label ([formatMinutes]) are pure so they unit-test
 * without a Context, which is the only end-to-end-verifiable layer while FCM is
 * inert (no google-services.json).
 */
object QuietHoursStore {

    const val DEFAULT_START_MINUTES = 22 * 60 // 10:00 PM
    const val DEFAULT_END_MINUTES = 7 * 60 // 7:00 AM

    private const val PREFS = "airdrop_quiet_hours"
    private const val KEY_ENABLED = "Airdrop.quietHours.enabled"
    private const val KEY_START = "Airdrop.quietHours.startMinutes"
    private const val KEY_END = "Airdrop.quietHours.endMinutes"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun startMinutes(context: Context): Int =
        prefs(context).getInt(KEY_START, DEFAULT_START_MINUTES)

    fun setStartMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_START, minutes.coerceIn(0, 1439)).apply()
    }

    fun endMinutes(context: Context): Int =
        prefs(context).getInt(KEY_END, DEFAULT_END_MINUTES)

    fun setEndMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_END, minutes.coerceIn(0, 1439)).apply()
    }

    /** Live query used by the suppression hook — reads enabled + the window. */
    fun isInQuietWindow(context: Context, nowMinutes: Int = nowMinutesOfDay()): Boolean {
        if (!isEnabled(context)) return false
        return inWindow(nowMinutes, startMinutes(context), endMinutes(context))
    }

    /**
     * Pure window math (Swift isInQuietWindow): inclusive of start, EXCLUSIVE of
     * end; start==end is an empty window (false); start>end wraps past midnight.
     */
    fun inWindow(nowMinutes: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> nowMinutes in start until end
        else -> nowMinutes >= start || nowMinutes < end
    }

    /** "10:00 PM – 7:00 AM" (spaced en-dash U+2013), Swift displayWindow. */
    fun displayWindow(context: Context): String =
        "${formatMinutes(startMinutes(context))} – ${formatMinutes(endMinutes(context))}"

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_ENABLED).remove(KEY_START).remove(KEY_END)
            .apply()
    }

    /** minutes-since-midnight → "h:mm a" (12-hour, AM/PM). */
    fun formatMinutes(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        val hour24 = m / 60
        val min = m % 60
        val period = if (hour24 < 12) "AM" else "PM"
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        return "%d:%02d %s".format(hour12, min, period)
    }

    private fun nowMinutesOfDay(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
