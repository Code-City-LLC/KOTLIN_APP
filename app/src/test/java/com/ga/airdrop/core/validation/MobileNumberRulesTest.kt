package com.ga.airdrop.core.validation

import com.ga.airdrop.feature.more2.AuthorizedUserPhoneInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kemar, 2026-10-17: "For mobile number, we shouldn't have 1876 ... 1876 is
 * not the only caller ID. Same for other countries. Just put the plus and allow
 * them to complete the remainder of it." Sign Up and Edit Profile take ONE
 * field that opens on "+1"; these pin what it accepts and what the API gets.
 */
class MobileNumberRulesTest {

    @Test
    fun `the field opens on +1 with a placeholder that names no country`() {
        assertEquals("+1", ProfileRules.MOBILE_PREFIX)
        assertEquals("e.g. +1 234 567 8900", ProfileRules.MOBILE_PLACEHOLDER)
        assertEquals(
            "Enter your mobile number with the country code, for example +1 234 567 8900.",
            ProfileRules.MOBILE_ERROR,
        )
    }

    @Test
    fun `a plus and 8 to 15 digits pass once separators are gone`() {
        assertTrue(ProfileRules.isValidMobile("+1 876 529 0736"))
        assertTrue(ProfileRules.isValidMobile("+1 (876) 529-0736"))
        assertTrue(ProfileRules.isValidMobile("+44 7911.123.456"))
        assertTrue(ProfileRules.isValidMobile("+12345678"))
        assertTrue(ProfileRules.isValidMobile("+123456789012345"))
    }

    @Test
    fun `anything without the plus, too short or too long is refused`() {
        // The untouched prefill, and the number without its country code.
        assertFalse(ProfileRules.isValidMobile("+1"))
        assertFalse(ProfileRules.isValidMobile("8765290736"))
        assertFalse(ProfileRules.isValidMobile("1-876-529-0736"))
        assertFalse(ProfileRules.isValidMobile("+1234567"))
        assertFalse(ProfileRules.isValidMobile("+1234567890123456"))
        assertFalse(ProfileRules.isValidMobile("+1 876 529 073x"))
        assertFalse(ProfileRules.isValidMobile(""))
    }

    @Test
    fun `the API gets the calling code with its plus and national digits only`() {
        assertEquals("+1" to "8765290736", ProfileRules.mobileForApi("+1 876 529 0736"))
        assertEquals("+1" to "2125551234", ProfileRules.mobileForApi("+1 (212) 555-1234"))
        assertEquals("+44" to "7911123456", ProfileRules.mobileForApi("+44 7911 123456"))
        assertEquals("+234" to "8031234567", ProfileRules.mobileForApi("+234 803 123 4567"))
        assertNull(ProfileRules.mobileForApi("8765290736"))
    }

    @Test
    fun `the split uses the authorized user calling-code table, longest code first`() {
        assertEquals("+1" to "8765290736", AuthorizedUserPhoneInput.splitInternational("18765290736"))
        assertEquals("+44" to "7911123456", AuthorizedUserPhoneInput.splitInternational("447911123456"))
        assertEquals("+353" to "861234567", AuthorizedUserPhoneInput.splitInternational("353861234567"))
        // No calling code starts with 0.
        assertNull(AuthorizedUserPhoneInput.splitInternational("08001234567"))
        assertNull(AuthorizedUserPhoneInput.splitInternational(""))
    }

    @Test
    fun `edit profile shows the stored number as plus code and national digits`() {
        assertEquals("+1 8765290736", ProfileRules.mobileFieldText("+1", "8765290736"))
        // Older shapes the server still reads: a "1-876" code, a number that
        // carries its own code, a bare NANP number.
        assertEquals("+1 8765290736", ProfileRules.mobileFieldText("1-876", "5290736"))
        assertEquals("+1 8765290736", ProfileRules.mobileFieldText(null, "+18765290736"))
        assertEquals("+1 8765290736", ProfileRules.mobileFieldText(null, "8765290736"))
        assertEquals("+44 7911123456", ProfileRules.mobileFieldText("+44", "7911123456"))
        // Nothing stored: the field opens on "+1", like Sign Up.
        assertEquals("+1", ProfileRules.mobileFieldText(null, null))
        assertEquals("+1", ProfileRules.mobileFieldText("+1", "  "))
        // No calling code can be read: shown as stored, for the customer to complete.
        assertEquals("5290736", ProfileRules.mobileFieldText(null, "5290736"))
    }

    @Test
    fun `what edit profile shows is what it saves`() {
        val shown = ProfileRules.mobileFieldText("1-876", "5290736")
        assertEquals("+1" to "8765290736", ProfileRules.mobileForApi(shown))
    }
}
