package com.ga.airdrop.core.update

import android.content.Context
import android.content.SharedPreferences
import com.ga.airdrop.BuildConfig
import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The brakes are the whole product here.
 *
 * A launch-time update check that gets any of these wrong is worse than not
 * shipping one: it either nags a customer on every cold start, or it sends
 * someone to the Play Store to collect a build that is not there. Both read as
 * a broken app, so each brake is pinned individually.
 */
class AppUpdateCheckerTest {

    /** In-memory SharedPreferences — no Robolectric, no device. */
    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
        override fun contains(key: String) = values.containsKey(key)
        override fun getAll() = values.toMap()
        override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { values[key] = value }
            override fun putStringSet(key: String, value: MutableSet<String>?) = apply { values[key] = value }
            override fun putInt(key: String, value: Int) = apply { values[key] = value }
            override fun putLong(key: String, value: Long) = apply { values[key] = value }
            override fun putFloat(key: String, value: Float) = apply { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
            override fun remove(key: String) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun commit() = true
            override fun apply() = Unit
        }
    }

    private lateinit var prefs: FakePrefs
    private lateinit var context: Context
    private var now = 1_000_000L

    @Before
    fun setUp() {
        prefs = FakePrefs()
        val appContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
        context = appContext
        AppUpdateChecker.clockMsForTests = { now }
        AppUpdateChecker.promptedThisSession = false
    }

    @After
    fun tearDown() {
        AppUpdateChecker.clockMsForTests = null
        AppUpdateChecker.promptedThisSession = false
    }

    private val newer = BuildConfig.VERSION_CODE + 1

    // ── The decision ────────────────────────────────────────────────────────

    @Test
    fun `a newer build on Play is reported as available`() {
        val status = AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_AVAILABLE, newer)
        assertEquals(AppUpdateChecker.UpdateStatus.Available(newer), status)
    }

    @Test
    fun `an unknown availability says nothing rather than guessing`() {
        // No Play Services, sideloaded build, throttled RPC — all land here, and
        // all must be indistinguishable from "no update".
        assertEquals(
            AppUpdateChecker.UpdateStatus.None,
            AppUpdateChecker.evaluate(context, UpdateAvailability.UNKNOWN, newer),
        )
        assertEquals(
            AppUpdateChecker.UpdateStatus.None,
            AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_NOT_AVAILABLE, newer),
        )
    }

    @Test
    fun `a build that is not actually ahead is never offered`() {
        // Play can report UPDATE_AVAILABLE for a code we already run. Offering it
        // sends the customer to a listing with nothing new on it.
        assertEquals(
            AppUpdateChecker.UpdateStatus.None,
            AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_AVAILABLE, BuildConfig.VERSION_CODE),
        )
        assertEquals(
            AppUpdateChecker.UpdateStatus.None,
            AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_AVAILABLE, BuildConfig.VERSION_CODE - 1),
        )
    }

    // ── Brake 2: "Not Now" is per-version, not a timer ──────────────────────

    @Test
    fun `Not Now suppresses that build but a NEWER one still prompts`() {
        AppUpdateChecker.skipVersion(context, newer)
        assertEquals(
            AppUpdateChecker.UpdateStatus.None,
            AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_AVAILABLE, newer),
        )
        // The whole point of skipping by version rather than by clock: the next
        // release must get through immediately.
        assertEquals(
            AppUpdateChecker.UpdateStatus.Available(newer + 1),
            AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_AVAILABLE, newer + 1),
        )
    }

    // ── Brake 1: the 24h throttle ───────────────────────────────────────────

    @Test
    fun `first ever launch checks immediately`() {
        assertTrue(AppUpdateChecker.shouldCheckNow(context))
    }

    @Test
    fun `a second launch inside 24h does not re-check`() {
        prefs.values["lastCheckAtMs"] = now
        now += AppUpdateChecker.CHECK_INTERVAL_MS - 1
        assertFalse(AppUpdateChecker.shouldCheckNow(context))
    }

    @Test
    fun `a launch after 24h checks again`() {
        prefs.values["lastCheckAtMs"] = now
        now += AppUpdateChecker.CHECK_INTERVAL_MS
        assertTrue(AppUpdateChecker.shouldCheckNow(context))
    }

    @Test
    fun `a clock that moved BACKWARDS does not lock the check out`() {
        // Timezone change or a manual clock set makes now - last negative. Naive
        // `elapsed < INTERVAL` would then be true and suppress the check
        // indefinitely, which is silent and unreportable.
        prefs.values["lastCheckAtMs"] = now + 5_000_000L
        assertTrue(AppUpdateChecker.shouldCheckNow(context))
    }

    // ── What the screen is allowed to print ─────────────────────────────────

    @Test
    fun `no remembered version means the screen prints no version line`() {
        assertNull(AppUpdateChecker.lastKnownAvailableVersionCode(context))
    }

    @Test
    fun `a remembered version is surfaced only while it is still ahead of us`() {
        AppUpdateChecker.evaluate(context, UpdateAvailability.UPDATE_AVAILABLE, newer)
        assertEquals(newer, AppUpdateChecker.lastKnownAvailableVersionCode(context))

        // Stale row from before the customer updated: it must NOT be shown, or
        // the screen tells someone on the newest build to go get an older one.
        prefs.values["lastAvailableVersionCode"] = BuildConfig.VERSION_CODE
        assertNull(AppUpdateChecker.lastKnownAvailableVersionCode(context))
    }
}
