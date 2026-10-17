package com.ga.airdrop.core.location

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryCatalogTest {
    @Test
    fun coversEveryJvmIsoCountryExactlyOnce() {
        val expected = Locale.getISOCountries().filter { it.length == 2 }.toSet()
        val actual = CountryCatalog.all.map(CountryEntry::isoCode)

        assertEquals(expected, actual.toSet())
        assertEquals(expected.size, actual.size)
        assertEquals(actual.size, CountryCatalog.displayOptions.size)
    }

    @Test
    fun anchorsMatchFrozenSwiftOrderFlagsAndDialCodes() {
        assertEquals(listOf("JM", "US", "CA", "GB"), CountryCatalog.all.take(4).map(CountryEntry::isoCode))
        assertEquals("🇯🇲", CountryCatalog.entryForIso("JM")?.flagEmoji)
        assertEquals("+1876", CountryCatalog.entryForName("Jamaica")?.dialCode)
        assertEquals("+1", CountryCatalog.entryForName("United States")?.dialCode)
        assertEquals("+44", CountryCatalog.entryForName("United Kingdom")?.dialCode)
    }

    @Test
    fun englishSpeakingTierPrecedesRemainingCountries() {
        val australia = CountryCatalog.all.indexOfFirst { it.isoCode == "AU" }
        val france = CountryCatalog.all.indexOfFirst { it.isoCode == "FR" }
        val singapore = CountryCatalog.all.indexOfFirst { it.isoCode == "SG" }

        assertTrue(australia in 4 until france)
        assertTrue(singapore in 4 until france)
    }

    @Test
    fun displayRowsRoundTripToCanonicalBackendName() {
        val jamaica = CountryCatalog.entryForIso("JM")!!

        assertEquals("🇯🇲  Jamaica", jamaica.display)
        assertEquals("Jamaica", CountryCatalog.canonicalName(jamaica.display))
        assertEquals(jamaica.display, CountryCatalog.displayNameFor("jamaica"))
        assertEquals("United States", CountryCatalog.canonicalName("US"))
        assertNull(CountryCatalog.entryForDisplay("Jamaica"))
    }

    @Test
    fun addressCountryLabelsCarryNoDialCode() {
        // Kemar 2026-10-17: "1876 is not the only caller ID". The calling
        // codes live in the authorized-user phone picker, not on the address.
        // Some names carry brackets of their own ("Myanmar (Burma)"), so check
        // for the code's "+".
        CountryCatalog.displayOptions.forEach { label ->
            assertFalse(label, label.contains("+"))
        }
        assertEquals("🇺🇸  United States", CountryCatalog.displayNameFor("United States"))
        assertEquals("🇬🇧  United Kingdom", CountryCatalog.displayNameFor("GB"))
    }

    @Test
    fun postalRequirementUsesCatalogAndFailsSafeForUnknowns() {
        assertFalse(CountryCatalog.requiresPostalCode("Jamaica"))
        assertFalse(CountryCatalog.requiresPostalCode(CountryCatalog.displayNameFor("Jamaica")))
        assertTrue(CountryCatalog.requiresPostalCode("United States"))
        assertTrue(CountryCatalog.requiresPostalCode("not-a-country"))
    }
}
