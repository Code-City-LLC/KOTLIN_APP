package com.ga.airdrop.core.update

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Launch-time "is there a newer build?" check — the PULL half of app-update
 * detection. Parity with iOS `AppUpdateChecker.swift` (SwiftHawk, 2026-08-01).
 *
 * ## Why this exists
 *
 * The app already had a complete update *routing* layer: `isAppUpdate()` in
 * [com.ga.airdrop.feature.homedetails.NotificationIconCatalog], six route
 * spellings in `NotificationsViewModel` (`app_update`, `app_update_available`,
 * `force_update`, `AppUpdateView`, `AppUpdateScreen`, …), and
 * `openPlayStoreListing()`. But **detection was 100% PUSH-DRIVEN** — the app
 * never asked Google anything, so a customer learned about an update only if
 * the backend remembered to send one. This file removes that dependency.
 *
 * ## ⚠️ Why the Play API and not a "lookup endpoint"
 *
 * iOS queries `itunes.apple.com/lookup?bundleId=…`, which returns the live
 * store version as JSON. **Google publishes no equivalent.** The two
 * alternatives were:
 *
 *  - **Scrape the Play listing HTML.** Fragile by construction — Google has
 *    changed that markup repeatedly and many listings now render "Varies with
 *    device" instead of a version at all. A scraper that silently starts
 *    returning nothing looks identical to "no update available", which is the
 *    absence-as-information trap this repo keeps hitting.
 *  - **Play In-App Update API** (this). Official, first-party, no backend, and
 *    it answers the actual question — `updateAvailability()` plus
 *    `availableVersionCode()`.
 *
 * ## What it deliberately does NOT do
 *
 * **Every failure path is silent.** No Play Services, sideloaded build, no
 * network, throttled RPC — all yield [UpdateStatus.None] and the customer sees
 * nothing. *A false "update available" is worse than a missed one*: it sends
 * someone to the Play Store to find nothing there, which reads as a broken app.
 *
 * ## Three independent brakes, so it cannot nag
 *
 * 1. **At most one check per 24h** ([CHECK_INTERVAL_MS]).
 * 2. **"Not Now" suppresses THAT versionCode** until a newer one appears — a
 *    version the customer declined, not a timer they cannot see.
 * 3. **Never prompts twice in one process** ([promptedThisSession]).
 *
 * The push path is unchanged and remains the **force-update lever**: a backend
 * broadcast still routes straight to the update screen regardless of these
 * brakes, because that path is used when someone must move now.
 */
object AppUpdateChecker {

    /** What the caller should do. Nothing else is expressible on purpose. */
    sealed interface UpdateStatus {
        /** No update, or we could not find out. Deliberately the same result. */
        data object None : UpdateStatus

        /** A newer build exists on Play and the customer has not declined it. */
        data class Available(val availableVersionCode: Int) : UpdateStatus
    }

    private const val PREFS = "airdrop.app_update"
    private const val KEY_LAST_CHECK_AT = "lastCheckAtMs"
    private const val KEY_SKIPPED_VERSION = "skippedVersionCode"
    private const val KEY_LAST_AVAILABLE_VERSION = "lastAvailableVersionCode"

    internal const val CHECK_INTERVAL_MS: Long = 24L * 60 * 60 * 1000

    /** Test seams. Null in production. */
    internal var clockMsForTests: (() -> Long)? = null
    internal var promptedThisSession: Boolean = false

    private fun nowMs(): Long = clockMsForTests?.invoke() ?: System.currentTimeMillis()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Fire-and-forget check for `MainActivity.onCreate`. Safe to call on every
     * launch — the throttle lives inside, not at the call site.
     */
    suspend fun checkOnLaunch(context: Context): UpdateStatus {
        if (promptedThisSession) return UpdateStatus.None
        if (!shouldCheckNow(context)) return UpdateStatus.None
        recordCheck(context)

        val info = runCatching { AppUpdateManagerFactory.create(context).awaitInfo() }
            .getOrNull() ?: return UpdateStatus.None
        return evaluate(context, info.availability, info.availableVersionCode)
    }

    /**
     * The decision, split out from the Play plumbing so it is testable without
     * Play Services on the device.
     */
    internal fun evaluate(
        context: Context,
        availability: Int,
        availableVersionCode: Int,
    ): UpdateStatus {
        if (availability != UpdateAvailability.UPDATE_AVAILABLE) return UpdateStatus.None
        // Play reports the code it would install. If it is not actually ahead of
        // what is running, there is nothing to tell the customer — and saying so
        // anyway would send them to a listing offering the build they already have.
        if (availableVersionCode <= BuildConfig.VERSION_CODE) return UpdateStatus.None
        rememberAvailable(context, availableVersionCode)
        if (availableVersionCode == skippedVersion(context)) return UpdateStatus.None
        return UpdateStatus.Available(availableVersionCode)
    }

    /** "Not Now" — suppress this exact build, not a timer. A newer one re-prompts. */
    fun skipVersion(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_SKIPPED_VERSION, versionCode).apply()
    }

    /**
     * The version code the last successful check found, or null.
     *
     * The screen needs this, and it must survive the trip: the PUSH path opens
     * the screen without running a check at all, and on that path we may
     * genuinely have no number. **Null is rendered as "no version line" rather
     * than as "0" or a guess** — the screen must never print a build number the
     * customer cannot actually get.
     */
    fun lastKnownAvailableVersionCode(context: Context): Int? =
        prefs(context).getInt(KEY_LAST_AVAILABLE_VERSION, NO_SKIPPED_VERSION)
            .takeIf { it > BuildConfig.VERSION_CODE }

    private fun rememberAvailable(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_LAST_AVAILABLE_VERSION, versionCode).apply()
    }

    internal fun skippedVersion(context: Context): Int =
        prefs(context).getInt(KEY_SKIPPED_VERSION, NO_SKIPPED_VERSION)

    internal fun shouldCheckNow(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_CHECK_AT, NEVER_CHECKED)
        // ⚠️ "Never checked" is its own case, NOT elapsed-since-epoch.
        //
        // Treating the 0L default as a real timestamp makes the answer depend on
        // the wall clock: `now - 0` is enormous on a device set to 2026 (so it
        // checks) and small on one whose clock is near the epoch (so it silently
        // does not). Same install, same code, different behaviour — and the
        // failure is the invisible direction.
        if (last == NEVER_CHECKED) return true

        // A clock that moved BACKWARDS (timezone change, manual set) yields a
        // negative delta. `elapsed < INTERVAL` would then be true and suppress
        // the check indefinitely, which nothing would ever report.
        val elapsed = nowMs() - last
        return elapsed !in 0 until CHECK_INTERVAL_MS
    }

    private fun recordCheck(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK_AT, nowMs()).apply()
    }

    internal const val NO_SKIPPED_VERSION = -1

    /** Sentinel for "this install has never run a check". */
    private const val NEVER_CHECKED = 0L

    /** What we need off Play's [AppUpdateInfo], and nothing more. */
    internal data class PlayUpdateInfo(val availability: Int, val availableVersionCode: Int)

    /** Bridges Play's Task API into a coroutine without pulling in play-services-tasks. */
    private suspend fun AppUpdateManager.awaitInfo(): PlayUpdateInfo =
        suspendCancellableCoroutine { cont ->
            appUpdateInfo
                .addOnSuccessListener {
                    cont.resume(PlayUpdateInfo(it.updateAvailability(), it.availableVersionCode()))
                }
                // Not an error worth surfacing: no Play Services, a sideloaded
                // build, or a throttled RPC all land here and all mean "we could
                // not find out", which the caller must treat as "say nothing".
                .addOnFailureListener {
                    cont.resume(PlayUpdateInfo(UpdateAvailability.UNKNOWN, 0))
                }
        }
}
