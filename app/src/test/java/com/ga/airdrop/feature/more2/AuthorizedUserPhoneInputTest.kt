package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.core.location.CountryEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/$name.json")) {
        "Missing pinned contract fixture $name"
    }.bufferedReader().use { Json.parseToJsonElement(it.readText()) }

    @Test
    fun `normalized Laravel phone fixture agrees with the existing validator`() {
        // Original 78-case fixture at 841015b1, not a claim of raw-input parity.
        val rows = fixture("authorized-user-phones").jsonArray
        assertEquals(78, rows.size)
        rows.forEachIndexed { index, element ->
            val row = element.jsonObject
            val pair = row.getValue("normalized").jsonArray
            assertEquals(2, pair.size)
            val error = AuthorizedUserPhoneInput.validationError(pair[1].jsonPrimitive.content, pair[0].jsonPrimitive.content)
            assertEquals("Laravel fixture row $index", row.getValue("problem") == JsonNull, error == null)
        }
    }

    @Test
    fun `all Laravel calling codes obey national length boundaries`() {
        val contract = fixture("authorized-user-calling-codes").jsonObject
        val codes = contract.getValue("callingCodes").jsonArray.map { it.jsonPrimitive.content }
        val minimums = contract.getValue("minimumNationalDigits").jsonObject
        assertEquals(205, codes.size)
        // Desktop Java omits Kosovo from Locale; Android includes it. Include
        // the existing territory fallback when checking the contract itself.
        val offered = AuthorizedUserPhoneInput.countries.map { it.callingCode }.toSet() +
            AuthorizedUserPhoneInput.TERRITORY_DIAL_CODES.values.mapNotNull(AuthorizedUserPhoneInput::callingCode)
        assertEquals(codes.map { "+$it" }.toSet(), offered)
        codes.forEach { code ->
            (0..16).forEach { length ->
                val minimum = minimums[code]?.jsonPrimitive?.int ?: 7
                val expected = if (code == "1") length == 10 else length in minimum..(15 - code.length)
                assertEquals("+$code, $length national digits", expected,
                    AuthorizedUserPhoneInput.validationError("2".repeat(length), "+$code") == null)
            }
        }
        listOf("", "+0", "+99", "+379", "+870", "44", "+044", "+1876", "+44 ").forEach { code ->
            assertEquals(code, AuthorizedUserPhoneInput.FIELD_ERROR,
                AuthorizedUserPhoneInput.validationError("2222222222", code))
        }
        ('0'..'9').forEach { first ->
            assertEquals("NANP area code beginning $first", first >= '2',
                AuthorizedUserPhoneInput.validationError("${first}234567890", "+1") == null)
        }
    }

    @Test
    fun `phone-only calling code corrections preserve the checkout catalogue`() {
        assertEquals("+39", AuthorizedUserPhoneInput.country("VA")?.callingCode)
        assertNull(AuthorizedUserPhoneInput.country("PN"))
        assertEquals("+379", CountryCatalog.all.first { it.isoCode == "VA" }.dialCode)
        assertEquals("+870", CountryCatalog.all.first { it.isoCode == "PN" }.dialCode)
    }

    @Test
    fun `submission normalization uses only the backend trunk zero whitelist`() {
        val contract = fixture("authorized-user-calling-codes").jsonObject
        val dropsZero = contract.getValue("dropsTrunkZero").jsonArray.map { it.jsonPrimitive.content }.toSet()
        contract.getValue("callingCodes").jsonArray.forEach { element ->
            val code = element.jsonPrimitive.content
            val expected = if (code in dropsZero) "2222222222" else "02222222222"
            assertEquals(code, expected, AuthorizedUserPhoneInput.submissionDigits("02222222222", "+$code"))
            assertEquals("0022222222", AuthorizedUserPhoneInput.submissionDigits("0022222222", "+$code"))
        }
        assertEquals("03012345678901", AuthorizedUserPhoneInput.interpret("03012345678901", "DE").number)
        val submitted = AuthorizedUserPhoneInput.submissionDigits("03012345678901", "+49")
        assertEquals("3012345678901", submitted)
        assertNull(AuthorizedUserPhoneInput.validationError(submitted, "+49"))
    }

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
        fun box(raw: String, iso: String) = AuthorizedUserPhoneInput.interpret(raw, iso).number
        assertEquals("5550199821", box("(555) 019-9821", "US"))
        assertEquals("8765551234", box("+1 (876) 555-1234", "JM"))
        assertEquals("8765551234", box("18765551234", "JM"))
        assertEquals("7911123456", box("+44 7911 123456", "GB"))
        assertEquals("123456789012345", box("1234567890123456789", "GB"))
        assertEquals("", box("abc", "JM"))
    }

    @Test
    fun `a +CC typed one key at a time finishes in the picker, not in the number`() {
        // What a TextField does: after every key it hands over the whole box.
        fun typeKeys(text: String, startIso: String): AuthorizedUserPhoneEntry {
            var entry = AuthorizedUserPhoneEntry(startIso, "")
            text.forEach { key -> entry = AuthorizedUserPhoneInput.interpret(entry.number + key, entry.isoCode) }
            return entry
        }
        assertEquals(AuthorizedUserPhoneEntry("GB", "7911123456"), typeKeys("+44 7911 123456", "GB"))
        assertEquals(AuthorizedUserPhoneEntry("GB", "7911123456"), typeKeys("+44 7911 123456", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("GB", "7911123456"), typeKeys("0044 7911 123456", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("IE", "871234567"), typeKeys("+353 87 123 4567", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("JM", "8765551234"), typeKeys("+1 876 555 1234", "GB"))
        assertEquals(AuthorizedUserPhoneEntry("US", "5550199821"), typeKeys("+1 555 019 9821", "US"))
        assertEquals(AuthorizedUserPhoneEntry("JM", "8765551234"), typeKeys("18765551234", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("GB", "7911123456"), typeKeys("447911123456", "GB"))
        // A code several countries share opens on its main one.
        assertEquals("RU", typeKeys("+7 916 123 4567", "JM").isoCode)
        assertEquals("CW", typeKeys("+599 9 123 4567", "JM").isoCode)
    }

    @Test
    fun `an unfinished code stays in the box and Save refuses it`() {
        assertEquals(AuthorizedUserPhoneEntry("JM", "+"), AuthorizedUserPhoneInput.interpret("+", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("JM", "+4"), AuthorizedUserPhoneInput.interpret("+4", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("JM", "004"), AuthorizedUserPhoneInput.interpret("004", "JM"))
        assertEquals(AuthorizedUserPhoneEntry("JM", "+999123"), AuthorizedUserPhoneInput.interpret("+999 123", "JM"))
        val msg = AuthorizedUserPhoneInput.FIELD_ERROR
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("+4", "+1"))
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("+999123", "+44"))
        assertEquals(msg, AuthorizedUserPhoneInput.validationError("0099912345", "+44"))
    }

    @Test
    fun `the selected code repeated in front of a full number is dropped, when the picker moves too`() {
        assertEquals("7911123456", AuthorizedUserPhoneInput.nationalDigits("447911123456", "+44"))
        assertEquals("871234567", AuthorizedUserPhoneInput.nationalDigits("353871234567", "+353"))
        assertEquals("8765551234", AuthorizedUserPhoneInput.nationalDigits("18765551234", "+1"))
        // Short numbers are never cut: 4479111 is seven digits under +44, not a repeated code.
        assertEquals("4479111", AuthorizedUserPhoneInput.nationalDigits("4479111", "+44"))
        assertEquals("8765551234", AuthorizedUserPhoneInput.nationalDigits("8765551234", "+1"))
        // A UK number typed while Jamaica was selected, then the picker moved to the UK.
        assertEquals("7911123456", AuthorizedUserPhoneInput.renumber("447911123456", "+44"))
        assertEquals("+4", AuthorizedUserPhoneInput.renumber("+4", "+44"))
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
    fun `the picker opens on the customer's country, else Jamaica - the device only when it is a +1 region`() {
        assertEquals("GB", AuthorizedUserPhoneInput.defaultIso("United Kingdom", "US"))
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso("jamaica", "GB"))
        assertEquals("GB", AuthorizedUserPhoneInput.defaultIso("GB", "US"))
        assertEquals("US", AuthorizedUserPhoneInput.defaultIso(null, "us"))
        assertEquals("CA", AuthorizedUserPhoneInput.defaultIso(null, "CA"))
        assertEquals("TT", AuthorizedUserPhoneInput.defaultIso(null, "TT")) // +1 868 is still +1
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso(null, "GB")) // verifier: en_GB opened on +44
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso(null, "ES")) // verifier: es_ES opened on +34
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso(null, "ZZ"))
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso(null, ""))
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso("Atlantis", null))
        assertEquals("JM", AuthorizedUserPhoneInput.defaultIso("  ", "DE"))
        assertNull(AuthorizedUserPhoneInput.profileIso("Atlantis"))
        assertNull(AuthorizedUserPhoneInput.profileIso(null))
    }

    @Test
    fun `territories the catalog lists without a dial code get their calling code`() {
        val expected = mapOf(
            "GG" to "+44", "IM" to "+44", "JE" to "+44", "SS" to "+211", "XK" to "+383",
            "CW" to "+599", "SX" to "+1", "BQ" to "+599", "PS" to "+970", "AX" to "+358",
            "MF" to "+590", "BL" to "+590", "SH" to "+290",
        )
        // The catalog lists these with no dial code. Built from rows shaped like
        // the catalog's — Kosovo included, which Android lists and the desktop
        // JVM running this test does not.
        val rows = expected.keys.map { CountryEntry(it, it, "🏳", null) }
        val picker = AuthorizedUserPhoneInput.countriesFrom(rows).associate { it.isoCode to it.callingCode }
        assertEquals(expected, picker)
        // No invented codes for places with no public numbering of their own.
        val none = listOf("BV", "GS", "HM", "TF", "UM").map { CountryEntry(it, it, "🏳", null) }
        assertTrue(AuthorizedUserPhoneInput.countriesFrom(none).isEmpty())
        // A +44 search lists the Crown Dependencies next to the UK.
        val plus44 = AuthorizedUserPhoneInput.search("+44").map { it.isoCode }
        assertTrue(plus44.containsAll(listOf("GB", "GG", "IM", "JE")))
        assertEquals("GB", AuthorizedUserPhoneInput.isoFor("+44", "7911123456"))
    }

    @Test
    fun `a failed save blames the phone only when the server's 422 names it`() {
        fun msg(status: Int?, offline: Boolean = false, message: String? = null, errors: Map<String, String> = emptyMap(), edit: Boolean = false) =
            AuthorizedUserPhoneInput.saveFailureMessage(edit, status, offline, message, errors)

        val phone = "Please enter a valid phone number."
        assertEquals(AuthorizedUserPhoneInput.ADD_FAILED, msg(422, message = phone, errors = mapOf("user_mobile_number" to phone)))
        assertEquals(AuthorizedUserPhoneInput.ADD_FAILED, msg(422, message = phone, errors = mapOf("user_country_code" to phone)))
        assertEquals(AuthorizedUserPhoneInput.UPDATE_FAILED, msg(422, errors = mapOf("user_mobile_number" to phone), edit = true))
        assertEquals("Failed to add authorized user. Check your connection and try again.", msg(null, offline = true))
        assertEquals("Your session has expired. Please sign in again.", msg(401, message = "Unauthenticated."))
        assertEquals("Too Many Attempts.", msg(429, message = "Too Many Attempts."))
        assertEquals("That email is taken.", msg(422, message = "That email is taken.", errors = mapOf("user_email" to "That email is taken.")))
        assertEquals("Failed to add authorized user. Please try again.", msg(500, message = "Server Error"))
        assertEquals("Failed to add authorized user. Please try again.", msg(502))
        assertEquals("Failed to add authorized user. Please try again.", msg(429))
        assertEquals("Failed to add authorized user. Please try again.", msg(null))
        assertEquals("Failed to update authorized user. Please try again.", msg(503, edit = true))
        assertEquals("Failed to update authorized user. Check your connection and try again.", msg(null, offline = true, edit = true))
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
