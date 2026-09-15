package com.ga.airdrop.feature.more2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kemar 2026-09-15: "If a user enters a phone number like 15550199 or
 * (555) 019-9821 (without an explicit + prefix), the UI accepts the input
 * without throwing an inline validation error … the API rejects the payload
 * (expected +15550199821), but no failure toast, alert, or field-level
 * validation message is rendered."
 *
 * These pin the client to the server's shape (App\Support\AuthorizedUserPhone):
 * a calling code from the picker, digits only in the box, and a message under
 * the field for every rule that can fail.
 */
class AuthorizedUserPhoneInputTest {

    @Test
    fun `NANP territories collapse to the calling code the server expects`() {
        assertEquals("+1", AuthorizedUserPhoneInput.callingCode("+1876"))
        assertEquals("+1", AuthorizedUserPhoneInput.callingCode("+1246"))
        assertEquals("+1", AuthorizedUserPhoneInput.callingCode("+1"))
        assertEquals("+44", AuthorizedUserPhoneInput.callingCode("+44"))
        assertEquals("+353", AuthorizedUserPhoneInput.callingCode("+353"))
        assertNull(AuthorizedUserPhoneInput.callingCode(""))
        assertEquals("+1", AuthorizedUserPhoneInput.country("JM")?.callingCode)
        assertEquals("+44", AuthorizedUserPhoneInput.country("GB")?.callingCode)
    }

    @Test
    fun `the picker is searchable by name, ISO and code`() {
        assertTrue(AuthorizedUserPhoneInput.search("jama").any { it.isoCode == "JM" })
        assertTrue(AuthorizedUserPhoneInput.search("gb").any { it.isoCode == "GB" })
        assertTrue(AuthorizedUserPhoneInput.search("+44").any { it.isoCode == "GB" })
        assertTrue(AuthorizedUserPhoneInput.search("").size > 100)
        assertNotNull(AuthorizedUserPhoneInput.country("JM")?.flagEmoji?.takeIf { it.isNotBlank() })
    }

    @Test
    fun `the box only ever holds digits, and a pasted full number loses its code`() {
        assertEquals("5550199821", AuthorizedUserPhoneInput.sanitize("(555) 019-9821", "+1"))
        assertEquals("8765551234", AuthorizedUserPhoneInput.sanitize("+1 (876) 555-1234", "+1"))
        assertEquals("8765551234", AuthorizedUserPhoneInput.sanitize("18765551234", "+1"))
        assertEquals("7911123456", AuthorizedUserPhoneInput.sanitize("+44 7911 123456", "+44"))
        assertEquals("123456789012345", AuthorizedUserPhoneInput.sanitize("1234567890123456789", "+44"))
        assertEquals("", AuthorizedUserPhoneInput.sanitize("abc", "+1"))
    }

    @Test
    fun `validation says so under the field before anything is sent`() {
        val msg = AuthorizedUserPhoneInput.FIELD_ERROR
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("", "+1"))
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("15550199", "+1")) // the report's example
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("5551234", "+1")) // area code missing
        assertNull(AuthorizedUserPhoneInput.validationError("5550199821", "+1"))
        assertNull(AuthorizedUserPhoneInput.validationError("7911123456", "+44"))
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("123456", "+44"))
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("1234567890123456", "+44"))
    }

    @Test
    fun `a stored row folds the way the website folds it`() {
        assertEquals("JM" to "8765551234", AuthorizedUserPhoneInput.fold("+1876", "5551234"))
        assertEquals("JM" to "8765551234", AuthorizedUserPhoneInput.fold("+1", "8765551234"))
        assertEquals("US" to "5550199821", AuthorizedUserPhoneInput.fold("+1", "5550199821"))
        assertEquals("US" to "5550199821", AuthorizedUserPhoneInput.fold("", "15550199821"))
        assertEquals("GB" to "7911123456", AuthorizedUserPhoneInput.fold("+44", "7911123456"))
        assertEquals("JM" to "", AuthorizedUserPhoneInput.fold("undefined", ""))
    }

    @Test
    fun `the picker opens on the customer's country, then the device, then Jamaica`() {
        assertEquals("GB", AuthorizedUserPhoneInput.defaultIso("United Kingdom", "US"))
        assertEquals("US", AuthorizedUserPhoneInput.defaultIso(null, "us"))
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso(null, "ZZ"))
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso("Atlantis", null))
    }

    @Test
    fun `the server's own phone message wins over the generic one`() {
        assertEquals(
            "Enter all 10 digits of the mobile number, for example 876 555 1234.",
            AuthorizedUserPhoneInput.serverPhoneError(mapOf("user_mobile_number" to "Enter all 10 digits of the mobile number, for example 876 555 1234.")),
        )
        assertNull(AuthorizedUserPhoneInput.serverPhoneError(mapOf("user_email" to "taken")))
    }
}
