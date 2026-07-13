package com.ga.airdrop.core.designsystem.theme

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Text-size preference pins (Kemar directive 2026-07-12, Swift
 * `AirdropTextSizePreference` parity @ 8ce745c).
 *
 * Plain-JVM: `init()` is never called, mirroring ThemeControllerCrashGuardTest —
 * `set()` must update the in-memory level without touching lateinit prefs.
 */
class TextSizeControllerTest {

    @Test
    fun `levels mirror the Swift multipliers exactly`() {
        val expected = mapOf(
            TextSizeController.Level.SMALLER to 0.92f,
            TextSizeController.Level.STANDARD to 1.00f,
            TextSizeController.Level.LARGER to 1.10f,
            TextSizeController.Level.LARGEST to 1.18f,
        )
        assertEquals(expected.keys.toList(), TextSizeController.Level.entries.toList())
        expected.forEach { (level, multiplier) ->
            assertEquals(multiplier, level.fontMultiplier, 0.0001f)
        }
    }

    @Test
    fun `display names match the Swift picker labels`() {
        assertEquals(
            listOf("Smaller", "Standard", "Larger", "Largest"),
            TextSizeController.Level.entries.map { it.displayName },
        )
    }

    @Test
    fun `set before init updates level without crashing`() {
        TextSizeController.set(TextSizeController.Level.LARGEST)
        assertEquals(TextSizeController.Level.LARGEST, TextSizeController.level)
        TextSizeController.set(TextSizeController.Level.STANDARD)
        assertEquals(TextSizeController.Level.STANDARD, TextSizeController.level)
    }

    // ── persistence / restart / malformed-value contract (PR92 review) ────

    @Test
    fun `persisted level is restored across a simulated restart`() {
        val store = FakePrefs()
        TextSizeController.initWithPrefs(store)
        TextSizeController.set(TextSizeController.Level.LARGEST)
        assertEquals("LARGEST", store.values["level"])

        // Process death: fresh init against the same backing store.
        TextSizeController.set(TextSizeController.Level.STANDARD) // drift the memory value
        TextSizeController.initWithPrefs(FakePrefs(store.values))
        assertEquals(TextSizeController.Level.STANDARD, TextSizeController.level)

        store.values["level"] = "LARGEST"
        TextSizeController.initWithPrefs(FakePrefs(store.values))
        assertEquals(TextSizeController.Level.LARGEST, TextSizeController.level)
    }

    @Test
    fun `malformed persisted value falls back to Standard`() {
        TextSizeController.initWithPrefs(FakePrefs(mutableMapOf("level" to "HUGE")))
        assertEquals(TextSizeController.Level.STANDARD, TextSizeController.level)
    }

    @Test
    fun `missing persisted value defaults to Standard`() {
        TextSizeController.initWithPrefs(FakePrefs())
        assertEquals(TextSizeController.Level.STANDARD, TextSizeController.level)
    }
}

/**
 * Minimal in-memory SharedPreferences for plain-JVM persistence pins —
 * implements only what TextSizeController touches.
 */
private class FakePrefs(
    val values: MutableMap<String, String?> = mutableMapOf(),
) : SharedPreferences {
    override fun getString(key: String?, defValue: String?): String? =
        values[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply { values[key!!] = value }
        override fun remove(key: String?) = apply { values.remove(key) }
        override fun apply() {}
        override fun commit() = true
        override fun clear() = apply { values.clear() }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply {}
        override fun putInt(k: String?, v: Int) = apply {}
        override fun putLong(k: String?, v: Long) = apply {}
        override fun putFloat(k: String?, v: Float) = apply {}
        override fun putBoolean(k: String?, v: Boolean) = apply {}
    }

    override fun getAll(): MutableMap<String, *> = values
    override fun getStringSet(k: String?, d: MutableSet<String>?) = d
    override fun getInt(k: String?, d: Int) = d
    override fun getLong(k: String?, d: Long) = d
    override fun getFloat(k: String?, d: Float) = d
    override fun getBoolean(k: String?, d: Boolean) = d
    override fun contains(k: String?) = values.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {}
    override fun unregisterOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {}
}
