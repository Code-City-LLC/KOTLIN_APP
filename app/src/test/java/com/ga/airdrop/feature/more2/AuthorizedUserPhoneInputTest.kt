package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.core.location.CountryEntry
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // The 87-case fixture at AIRDROP-LARAVEL dd4675c8 (v1.29: the original
        // 78, v1.28's Brazil and Germany cases, and the ambiguous German shapes).
        val rows = fixture("authorized-user-phones").jsonArray
        assertEquals(87, rows.size)
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
        // The box drops that trunk 0 itself now, once (2026-09-23).
        assertEquals("3012345678901", AuthorizedUserPhoneInput.interpret("03012345678901", "DE").number)
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

    // ── 2026-09-23 audit: a "+" number is read in the server's order ──────────
    // App\Support\AuthorizedUserPhone::normalize: the picker's own code typed in
    // front, else exactly ten digits of a Caribbean number written the local
    // way, else the calling code the digits start with — and never a repeated
    // code cut after an explicit "+CC".

    /** What a TextField does: after every key it hands over the whole box. Every step, in order. */
    private fun typedSteps(text: String, startIso: String): List<AuthorizedUserPhoneEntry> {
        var entry = AuthorizedUserPhoneEntry(startIso, "")
        return text.map { key ->
            entry = AuthorizedUserPhoneInput.interpret(entry.number + key, entry.isoCode, entry.explicitCode)
            entry
        }
    }

    /** Typed key by key, then settled the way leaving the box or Save settles it: picker ISO to box. */
    private fun typedAndSettled(text: String, startIso: String): Pair<String, String> =
        AuthorizedUserPhoneInput.resolve(typedSteps(text, startIso).last()).let { it.isoCode to it.number }

    private fun AuthorizedUserPhoneEntry.shown(): Pair<String, String> = isoCode to number

    @Test
    fun `a Caribbean number typed with a plus waits in the box and settles on its own island`() {
        val expected = mapOf(
            "+876 555 1234" to ("JM" to "8765551234"),
            "+658 555 1234" to ("JM" to "6585551234"), // never Singapore at "+65"
            "+868 555 1234" to ("TT" to "8685551234"), // never China at "+86"
            "+441 555 1234" to ("BM" to "4415551234"), // never the UK at "+44"
        )
        for ((typed, settled) in expected) {
            val steps = typedSteps(typed, "JM")
            assertEquals("$typed moved the picker while it was typed", listOf("JM"), steps.map { it.isoCode }.distinct())
            assertEquals("$typed stays as typed until it is settled", "+" + settled.second, steps.last().number)
            assertEquals(typed, settled, typedAndSettled(typed, "JM"))
        }
        // It names its own country from a picker with another code as well.
        assertEquals("JM" to "8765551234", typedAndSettled("+876 555 1234", "GB"))
    }

    @Test
    fun `a plus code that cannot be Caribbean moves the picker at the key that rules it out`() {
        val uk = typedSteps("+44 7911 123456", "JM")
        assertEquals("+44 waits: +441 is Bermuda", "JM" to "+44", uk[2].shown())
        assertEquals("+447: 447 is no Caribbean area code", "GB" to "7", uk[4].shown())
        assertEquals("GB" to "7911123456", typedAndSettled("+44 7911 123456", "JM"))

        val singapore = typedSteps("+65 9123 4567", "JM")
        assertEquals("+65 waits: +658 is Jamaica", "JM" to "+65", singapore[2].shown())
        assertEquals("SG" to "9", singapore[4].shown())
        assertEquals("SG" to "91234567", typedAndSettled("+65 9123 4567", "JM"))

        // Ten digits that start like Bermuda's still could be; an eleventh cannot.
        val london = typedSteps("+44 1632 960000", "JM")
        assertEquals("JM" to "+4416329600", london[12].shown())
        assertEquals("GB" to "163296000", london[13].shown())
        assertEquals("GB" to "1632960000", typedAndSettled("+44 1632 960000", "JM"))
    }

    @Test
    fun `the picker's own code typed in front keeps the picker`() {
        assertEquals("SG" to "85551234", typedAndSettled("+65 8555 1234", "SG"))
        assertEquals("SG" to "85551234", AuthorizedUserPhoneInput.interpret("+65 8555 1234", "SG").shown())
        assertEquals("GB" to "15551234", typedAndSettled("+441 555 1234", "GB"))
    }

    @Test
    fun `nothing typed after a plus code is cut for repeating it`() {
        // Area code 55 of a Brazilian mobile is not "+55" typed twice.
        assertEquals("BR" to "55991234567", typedAndSettled("+55 55 99123 4567", "JM"))
        assertEquals("BR" to "55991234567", AuthorizedUserPhoneInput.interpret("+55 55 99123 4567", "JM").shown())
        assertEquals("BR" to "55991234567", AuthorizedUserPhoneInput.interpret("+55 55 99123 4567", "BR").shown())
        assertEquals("DE" to "49211234567", AuthorizedUserPhoneInput.interpret("+49 4921 1234567", "JM").shown())
        // Without a "+" a repeated code still goes, and under +1 the trunk 1 always does.
        assertEquals("7911123456", AuthorizedUserPhoneInput.interpret("447911123456", "GB").number)
        assertEquals("8765551234", AuthorizedUserPhoneInput.interpret("+1 1 876 555 1234", "JM").number)
        assertEquals("8765551234", typedAndSettled("+1 1876 555 1234", "JM").second)
        // Emptying the box starts over.
        assertFalse(AuthorizedUserPhoneInput.interpret("", "BR", explicitCode = true).explicitCode)
    }

    @Test
    fun `a box the server would cut as a repeated code goes with the code in front`() {
        fun wire(digits: String, code: String) = AuthorizedUserPhoneInput.requestNumber(digits, code)
        // "+55 55 99123 4567": the handoff's contract row, and what Swift sends.
        assertEquals("+5555991234567", wire("55991234567", "+55"))
        assertEquals("+4949211234567", wire("49211234567", "+49"))
        // German 049112345678 with its trunk 0 dropped by the box: sent bare,
        // the server would cut "49" as well and store 112345678.
        assertEquals("+4949112345678", wire("49112345678", "+49"))
        // Everything else stays digits only.
        assertEquals("5599123456", wire("5599123456", "+55")) // ten digits: never cut
        assertEquals("11991234567", wire("11991234567", "+55")) // no repeated code
        assertEquals("18765551234", wire("18765551234", "+1")) // +1 keeps its own rule
        assertEquals("7911123456", wire("7911123456", "+44"))
        assertEquals("3012345678901", wire("3012345678901", "+49"))
        assertEquals("", wire("", "+55"))
    }

    @Test
    fun `resolve settles only a number still waiting`() {
        fun settle(box: String, iso: String) =
            AuthorizedUserPhoneInput.resolve(AuthorizedUserPhoneEntry(iso, box)).shown()
        assertEquals("JM" to "8765551234", settle("+8765551234", "JM"))
        assertEquals("JM" to "6585551234", settle("+6585551234", "JM"))
        assertEquals("TT" to "8685551234", settle("+8685551234", "US"))
        assertEquals("BM" to "4415551234", settle("+4415551234", "JM"))
        // A code on its own: the picker moves and the box empties.
        assertEquals("GB" to "", settle("+44", "JM"))
        assertEquals("GB" to "", settle("0044", "JM"))
        // Not a whole number, or no country at all: left as typed, and refused.
        assertEquals("JM" to "+876555", settle("+876555", "JM"))
        assertEquals("JM" to "+999123", settle("+999123", "JM"))
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, AuthorizedUserPhoneInput.validationError("+876555", "+1"))
        // A box of digits is already settled.
        assertEquals("JM" to "8765551234", settle("8765551234", "JM"))
        assertEquals("US" to "5551234", settle("5551234", "US"))
    }

    @Test
    fun `every Caribbean area code opens its own country under +1`() {
        // The website's PHONE_NANP_AREA_ISOS (phoneCountries.js), verbatim.
        val website = mapOf(
            "242" to "BS", "246" to "BB", "264" to "AI", "268" to "AG", "284" to "VG", "340" to "VI",
            "345" to "KY", "441" to "BM", "473" to "GD", "649" to "TC", "658" to "JM", "664" to "MS",
            "670" to "MP", "671" to "GU", "684" to "AS", "721" to "SX", "758" to "LC", "767" to "DM",
            "784" to "VC", "787" to "PR", "809" to "DO", "829" to "DO", "849" to "DO", "868" to "TT",
            "869" to "KN", "876" to "JM", "939" to "PR",
        )
        for ((area, iso) in website) {
            assertEquals(area, iso, AuthorizedUserPhoneInput.isoFor("+1", "${area}5551234"))
        }
        assertEquals(website, AuthorizedUserPhoneInput.NANP_CARIBBEAN_AREA_ISOS)
        // Any other +1 number opens on the United States, as on the website.
        assertEquals("US", AuthorizedUserPhoneInput.isoFor("+1", "5550199821"))
        assertEquals("US", AuthorizedUserPhoneInput.isoFor("+1", "4165551234"))
        // A stored row opens on its island too.
        assertEquals("TT" to "8685551234", AuthorizedUserPhoneInput.fold("+1868", "5551234"))
        assertEquals("BB" to "2465551234", AuthorizedUserPhoneInput.fold("+1", "2465551234"))
    }

    @Test
    fun `the picker offers only calling codes the server accepts`() {
        val refused = AuthorizedUserPhoneInput.countries
            .filter { it.callingCode !in WEBSITE_DIAL_CODES.values }
            .map { "${it.isoCode} ${it.callingCode}" }
        assertEquals("picker codes the server refuses", emptyList<String>(), refused)
        // Every country the website offers is here with the same code.
        val listed = Locale.getISOCountries().toSet()
        for ((iso, code) in WEBSITE_DIAL_CODES) {
            if (iso !in listed) continue // the desktop JVM has no Kosovo; Android does
            assertEquals(iso, code, AuthorizedUserPhoneInput.country(iso)?.callingCode)
        }
        assertEquals("+39", AuthorizedUserPhoneInput.country("VA")?.callingCode)
        assertNull("Pitcairn has no calling code of its own", AuthorizedUserPhoneInput.country("PN"))
        assertEquals("IT", AuthorizedUserPhoneInput.isoFor("+39", "0612345678"))
        assertTrue(AuthorizedUserPhoneInput.search("+379").isEmpty())
        // The catalog's own rows, whichever regions this runtime lists; the
        // correction lives in the picker, not in the shared catalog.
        val catalogRows = listOf(
            CountryEntry("VA", "Vatican City", "🇻🇦", "+379"),
            CountryEntry("PN", "Pitcairn Islands", "🇵🇳", "+870"),
        )
        assertEquals(
            listOf("VA" to "+39"),
            AuthorizedUserPhoneInput.countriesFrom(catalogRows).map { it.isoCode to it.callingCode },
        )
    }

    // ── 2026-09-23 display gaps: the box shows the number the server stores ────

    @Test
    fun `one trunk 0 after a typed or picked code goes, as the server drops it once`() {
        // Typed with the code, key by key or pasted: 🇬🇧 7911123456.
        assertEquals("GB" to "7911123456", typedAndSettled("+44 07911 123456", "JM"))
        assertEquals("GB" to "7911123456", AuthorizedUserPhoneInput.interpret("+44 07911 123456", "JM").shown())
        // The UK already picked, the number written the national way.
        assertEquals("GB" to "7911123456", typedAndSettled("07911 123456", "GB"))
        // A lone 0 stays until the next digit (the server keeps a lone 0 too).
        assertEquals("GB" to "0", typedAndSettled("0", "GB"))
        // Only for the codes the server drops it for: Italy keeps its 0.
        assertEquals("IT" to "0612345678", typedAndSettled("+39 06 1234 5678", "JM"))
        // A country picked after the digits: re-read under its code.
        assertEquals("7911123456", AuthorizedUserPhoneInput.renumber("07911123456", "+44"))
        assertEquals("0612345678", AuthorizedUserPhoneInput.renumber("0612345678", "+39"))
        // Once only, however the digits arrive: after the 0 the digits are the
        // national number, so "49" in front of them is never cut as well
        // (the server keeps 49112345678 for 049112345678 under +49).
        val german = typedSteps("049112345678", "DE").last()
        assertEquals("DE" to "49112345678", german.shown())
        assertTrue(german.explicitCode)
        assertEquals("49112345678", AuthorizedUserPhoneInput.renumber("049112345678", "+49"))
    }

    @Test
    fun `+1 from a picker with another code waits for the area code, which names the country`() {
        val trinidad = typedSteps("+1 868 555 1234", "GB")
        assertEquals("+1 waits", "GB" to "+1", trinidad[1].shown())
        assertEquals("GB" to "+186", trinidad[4].shown())
        assertEquals("the area code names it", "TT" to "868", trinidad[5].shown())
        assertEquals("TT" to "8685551234", trinidad.last().shown())
        assertEquals("US" to "2125551234", typedAndSettled("+1 212 555 1234", "GB"))
        assertEquals("JM" to "8765551234", typedAndSettled("+1 876 555 1234", "GB"))
        // A +1 country the customer already picked stays as it is.
        assertEquals("JM" to "8685551234", typedAndSettled("+1 868 555 1234", "JM"))
        assertEquals("US" to "8765551234", typedAndSettled("+1 876 555 1234", "US"))
        // Left before the area code: still waiting, and refused, not guessed.
        assertEquals("GB" to "+18", typedAndSettled("+18", "GB"))
    }

    @Test
    fun `a repeated code is cut only above the country's longest national number`() {
        fun national(digits: String, code: String) = AuthorizedUserPhoneInput.nationalDigits(digits, code)
        // Brazil: 10 or 11 digits, so an 11-digit number starting 55 is area code 55.
        assertEquals("55991234567", national("55991234567", "+55"))
        assertEquals("55991234567", national("5555991234567", "+55"))
        assertEquals("1134567890", national("551134567890", "+55"))
        // Every other code keeps the 11+ rule — Germany too, except at exactly
        // 11 digits, which v1.29 neither cuts nor keeps as a guess: it asks.
        assertEquals("49211234567", national("49211234567", "+49"))
        assertEquals("3012345678", national("493012345678", "+49"))
        assertEquals("7911123456", national("447911123456", "+44"))
        assertEquals("8765551234", national("18765551234", "+1"))
        // Typed key by key, the box ends where the server does.
        assertEquals("BR" to "55991234567", typedAndSettled("55 99123 4567", "BR"))
        assertEquals("BR" to "55991234567", typedAndSettled("55 55 99123 4567", "BR"))
        assertEquals("BR" to "1134567890", typedAndSettled("55 11 3456 7890", "BR"))
    }

    @Test
    fun `a plus behind an invisible mark, in brackets or after tel counts, as on the server`() {
        // Pasted from Contacts or a chat, a number often starts with a
        // direction mark; the server removes everything but digits and "+"
        // before it looks for the "+", so the box does too.
        val marks = listOf(
            '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
            '\u2066', '\u2067', '\u2068', '\u2069', '\uFEFF',
        )
        for (mark in marks) {
            val label = "U+%04X".format(mark.code)
            assertEquals(label, "GB" to "7911123456", AuthorizedUserPhoneInput.interpret("$mark+44 7911 123456", "JM").shown())
        }
        assertEquals("GB" to "7911123456", AuthorizedUserPhoneInput.interpret("(+44) 7911 123456", "JM").shown())
        assertEquals("JM" to "8765551234", AuthorizedUserPhoneInput.interpret("tel:+1 876 555 1234", "GB").shown())
        // Typed key by key they land the same way.
        assertEquals("GB" to "7911123456", typedAndSettled("\u200E+44 7911 123456", "JM"))
        assertEquals("GB" to "7911123456", typedAndSettled("(+44) 7911 123456", "JM"))
        assertEquals("JM" to "8765551234", typedAndSettled("tel:+1 876 555 1234", "GB"))
        // Blur and Save read the "+" the same way.
        assertEquals(
            "JM" to "8765551234",
            AuthorizedUserPhoneInput.resolve(AuthorizedUserPhoneEntry("GB", "\u200E+8765551234")).shown(),
        )
        // A "+" after a digit is no calling code: digits only, under the picker.
        assertEquals("JM" to "8765551234", AuthorizedUserPhoneInput.interpret("876+5551234", "JM").shown())
        assertEquals("GB" to "8765551234", AuthorizedUserPhoneInput.interpret("876+5551234", "GB").shown())
    }

    @Test
    fun `the box shows what the server stores for every fixture row a customer can type`() {
        // Each row the server accepts whose code the picker offers: the picker
        // on that code, the number typed or pasted, the box settled, must read
        // as the server's normalized pair. Not modelled by the box: a code the
        // picker cannot show ("+876", "undefined", ""), which only old stored
        // rows carry, and "011", the NANP exit code.
        val rows = fixture("authorized-user-phones").jsonArray.map { it.jsonObject }
        var checked = 0
        for (row in rows) {
            if (row.getValue("problem") != JsonNull) continue
            val code = row.getValue("code").jsonPrimitive.content
            val mobile = row.getValue("mobile").jsonPrimitive.content
            val iso = AuthorizedUserPhoneInput.countries.firstOrNull { it.callingCode == code }?.isoCode ?: continue
            if (mobile.startsWith("011")) continue
            val (wantCode, wantNumber) = row.getValue("normalized").jsonArray.map { it.jsonPrimitive.content }
            val start = if (code == "+1") "JM" else AuthorizedUserPhoneInput.isoFor(code, "")
            for ((how, entry) in listOf(
                "pasted" to AuthorizedUserPhoneInput.resolve(AuthorizedUserPhoneInput.interpret(mobile, start)),
                "typed" to AuthorizedUserPhoneInput.resolve(typedSteps(mobile, start).last()),
            )) {
                val label = "$how $code \"$mobile\""
                assertEquals(label, wantNumber, entry.number)
                assertEquals(label, wantCode, AuthorizedUserPhoneInput.country(entry.isoCode)?.callingCode)
            }
            checked++
            assertTrue(iso.isNotEmpty())
        }
        assertEquals("fixture rows checked", 49, checked)
    }

    @Test
    fun `an ambiguous German number is kept as typed and refused in the server's words`() {
        // The fixture's refusals whose number the server left unresolved: the
        // box keeps what was typed, and asks exactly as the server asks.
        val rows = fixture("authorized-user-phones").jsonArray.map { it.jsonObject }
        var checked = 0
        for (row in rows) {
            val (code, stored) = row.getValue("normalized").jsonArray.map { it.jsonPrimitive.content }
            if (!stored.startsWith("+")) continue
            val mobile = row.getValue("mobile").jsonPrimitive.content
            val message = row.getValue("problem").jsonArray[1].jsonPrimitive.content
            val start = AuthorizedUserPhoneInput.isoFor(code, "")
            for ((how, entry) in listOf(
                "pasted" to AuthorizedUserPhoneInput.resolve(AuthorizedUserPhoneInput.interpret(mobile, start)),
                "typed" to AuthorizedUserPhoneInput.resolve(typedSteps(mobile, start).last()),
            )) {
                val label = "$how $code \"$mobile\""
                assertEquals(label, stored.drop(1), entry.number)
                assertEquals(label, message, AuthorizedUserPhoneInput.ambiguityError(entry.number, code, entry.explicitCode))
            }
            checked++
        }
        assertEquals("ambiguous fixture rows checked", 2, checked)

        val message = "Please enter a valid phone number. For a German number, start with +49, or with 0 as dialled in Germany."
        assertEquals("49211234567", AuthorizedUserPhoneInput.nationalDigits("49211234567", "+49"))
        assertEquals(message, AuthorizedUserPhoneInput.ambiguityError("49211234567", "+49", explicitCode = false))
        // Read as they are: after "+49", "0049" or a trunk 0, and at 12 digits.
        assertNull(AuthorizedUserPhoneInput.ambiguityError("49211234567", "+49", explicitCode = true))
        assertEquals("DE" to "49211234567", typedAndSettled("+49 4921 1234567", "DE"))
        assertEquals("DE" to "2111234567", typedAndSettled("0049 211 1234567", "DE"))
        val emden = typedSteps("04921 1234567", "DE").last()
        assertEquals("DE" to "49211234567", emden.shown())
        assertNull(AuthorizedUserPhoneInput.ambiguityError(emden.number, "+49", emden.explicitCode))
        assertEquals("DE" to "3012345678", typedAndSettled("49 30 12345678", "DE"))
        // Only Germany, only 11 digits.
        assertNull(AuthorizedUserPhoneInput.ambiguityError("447911123456", "+44", explicitCode = false))
        assertNull(AuthorizedUserPhoneInput.ambiguityError("4921123456", "+49", explicitCode = false))
    }

    private companion object {
        /** The website's PHONE_COUNTRIES (phoneCountries.js): the codes the server accepts, and nothing else. */
        val WEBSITE_DIAL_CODES = mapOf(
            "JM" to "+1", "US" to "+1", "CA" to "+1", "GB" to "+44", "TT" to "+1", "BB" to "+1", "BS" to "+1",
            "KY" to "+1", "BM" to "+1", "GD" to "+1", "LC" to "+1", "VC" to "+1", "AG" to "+1", "DM" to "+1",
            "VG" to "+1", "AI" to "+1", "TC" to "+1", "MS" to "+1", "KN" to "+1", "DO" to "+1", "PR" to "+1",
            "VI" to "+1", "SX" to "+1", "HT" to "+509", "CU" to "+53", "GY" to "+592", "SR" to "+597",
            "BZ" to "+501", "AW" to "+297", "CW" to "+599", "BQ" to "+599", "GP" to "+590", "MQ" to "+596",
            "BL" to "+590", "MF" to "+590", "AF" to "+93", "AX" to "+358", "AL" to "+355", "DZ" to "+213",
            "AS" to "+1", "AD" to "+376", "AO" to "+244", "AR" to "+54", "AM" to "+374", "AU" to "+61",
            "AT" to "+43", "AZ" to "+994", "BH" to "+973", "BD" to "+880", "BY" to "+375", "BE" to "+32",
            "BJ" to "+229", "BT" to "+975", "BO" to "+591", "BA" to "+387", "BW" to "+267", "BR" to "+55",
            "IO" to "+246", "BN" to "+673", "BG" to "+359", "BF" to "+226", "BI" to "+257", "CV" to "+238",
            "KH" to "+855", "CM" to "+237", "CF" to "+236", "TD" to "+235", "CL" to "+56", "CN" to "+86",
            "CX" to "+61", "CC" to "+61", "CO" to "+57", "KM" to "+269", "CG" to "+242", "CD" to "+243",
            "CK" to "+682", "CR" to "+506", "CI" to "+225", "HR" to "+385", "CY" to "+357", "CZ" to "+420",
            "DK" to "+45", "DJ" to "+253", "EC" to "+593", "EG" to "+20", "SV" to "+503", "GQ" to "+240",
            "ER" to "+291", "EE" to "+372", "SZ" to "+268", "ET" to "+251", "FK" to "+500", "FO" to "+298",
            "FJ" to "+679", "FI" to "+358", "FR" to "+33", "GF" to "+594", "PF" to "+689", "GA" to "+241",
            "GM" to "+220", "GE" to "+995", "DE" to "+49", "GH" to "+233", "GI" to "+350", "GR" to "+30",
            "GL" to "+299", "GU" to "+1", "GT" to "+502", "GG" to "+44", "GN" to "+224", "GW" to "+245",
            "HN" to "+504", "HK" to "+852", "HU" to "+36", "IS" to "+354", "IN" to "+91", "ID" to "+62",
            "IR" to "+98", "IQ" to "+964", "IE" to "+353", "IM" to "+44", "IL" to "+972", "IT" to "+39",
            "JP" to "+81", "JE" to "+44", "JO" to "+962", "KZ" to "+7", "KE" to "+254", "KI" to "+686",
            "XK" to "+383", "KW" to "+965", "KG" to "+996", "LA" to "+856", "LV" to "+371", "LB" to "+961",
            "LS" to "+266", "LR" to "+231", "LY" to "+218", "LI" to "+423", "LT" to "+370", "LU" to "+352",
            "MO" to "+853", "MG" to "+261", "MW" to "+265", "MY" to "+60", "MV" to "+960", "ML" to "+223",
            "MT" to "+356", "MH" to "+692", "MR" to "+222", "MU" to "+230", "YT" to "+262", "MX" to "+52",
            "FM" to "+691", "MD" to "+373", "MC" to "+377", "MN" to "+976", "ME" to "+382", "MA" to "+212",
            "MZ" to "+258", "MM" to "+95", "NA" to "+264", "NR" to "+674", "NP" to "+977", "NL" to "+31",
            "NC" to "+687", "NZ" to "+64", "NI" to "+505", "NE" to "+227", "NG" to "+234", "NU" to "+683",
            "NF" to "+672", "KP" to "+850", "MK" to "+389", "MP" to "+1", "NO" to "+47", "OM" to "+968",
            "PK" to "+92", "PW" to "+680", "PS" to "+970", "PA" to "+507", "PG" to "+675", "PY" to "+595",
            "PE" to "+51", "PH" to "+63", "PL" to "+48", "PT" to "+351", "QA" to "+974", "RE" to "+262",
            "RO" to "+40", "RU" to "+7", "RW" to "+250", "SH" to "+290", "PM" to "+508", "WS" to "+685",
            "SM" to "+378", "ST" to "+239", "SA" to "+966", "SN" to "+221", "RS" to "+381", "SC" to "+248",
            "SL" to "+232", "SG" to "+65", "SK" to "+421", "SI" to "+386", "SB" to "+677", "SO" to "+252",
            "ZA" to "+27", "KR" to "+82", "SS" to "+211", "ES" to "+34", "LK" to "+94", "SD" to "+249",
            "SJ" to "+47", "SE" to "+46", "CH" to "+41", "SY" to "+963", "TW" to "+886", "TJ" to "+992",
            "TZ" to "+255", "TH" to "+66", "TL" to "+670", "TG" to "+228", "TK" to "+690", "TO" to "+676",
            "TN" to "+216", "TR" to "+90", "TM" to "+993", "TV" to "+688", "UG" to "+256", "UA" to "+380",
            "AE" to "+971", "UY" to "+598", "UZ" to "+998", "VU" to "+678", "VA" to "+39", "VE" to "+58",
            "VN" to "+84", "WF" to "+681", "EH" to "+212", "YE" to "+967", "ZM" to "+260", "ZW" to "+263",
        )
    }
}
