package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.core.location.CountryEntry
import java.util.Locale

/**
 * The authorized-user mobile number, the way the website and the server now
 * speak it (Kemar 2026-09-15; Laravel App\Support\AuthorizedUserPhone):
 *
 *     user_country_code  = the CALLING code — "+1" for Jamaica, "+44" for the UK
 *     user_mobile_number = national digits within the API's calling-code limits
 *                          (with "+CC" in front only where the server would
 *                          otherwise cut the code from them: [requestNumber])
 *
 * The old form took one free-text box ("+1 876-5290736") and parsed a "+" out
 * of it. Type 15550199 or (555) 019-9821 and the "+" is missing, so the
 * request never matched the server's shape — and the failure never reached
 * the customer as a field they could fix. Now the country lives in a picker,
 * the box only ever holds digits, and every rule that can fail says so under
 * the field before anything is sent.
 *
 * Pure Kotlin, no Android types, so the JVM unit tests pin every rule.
 */
data class AuthorizedUserPhoneCountry(
    val isoCode: String,
    val name: String,
    val flagEmoji: String,
    /** "+1", never "+1876": NANP area codes belong inside the number. */
    val callingCode: String,
) {
    val pickerLabel: String get() = "$flagEmoji $callingCode"
    val searchText: String get() = "$name $isoCode $callingCode".lowercase(Locale.US)
}

/** The mobile box after one edit, and the picker country that edit implies. */
data class AuthorizedUserPhoneEntry(
    val isoCode: String,
    val number: String,
    /**
     * [number] is the national number as written, so a repeated code is never
     * cut from it: the picker's calling code was typed in the box after a "+"
     * or "00" ("+55 55 99123 4567" is a Brazilian mobile in area code 55), or
     * the box dropped the trunk 0 it followed ("049112345678" under 🇩🇪 is
     * 49112345678, never 112345678). The server never cuts a repeated code in
     * either case, so neither does the box.
     */
    val explicitCode: Boolean = false,
    /**
     * [number] follows a trunk 0 the box dropped (07911 123456 under 🇬🇧 shows
     * 7911123456). It is still part of the number as typed, so a newly picked
     * country re-reads the digits with it: Italy keeps it ([renumbered]).
     */
    val droppedTrunkZero: Boolean = false,
)

object AuthorizedUserPhoneInput {
    const val FIELD_ERROR = "Please enter a valid phone number."

    // What a failed save says (verifier 2026-09-22: every failure used to
    // blame the phone — a 500, no connection, an expired session, a 429 and a
    // 502 HTML page all said "check the phone number"). The phone sentence is
    // kept for a 422 that names the phone; see [saveFailureMessage].
    const val ADD_FAILED = "Failed to add authorized user. Please check the phone number and try again."
    const val UPDATE_FAILED = "Failed to update authorized user. Please check the phone number and try again."
    const val ADD_OFFLINE = "Failed to add authorized user. Check your connection and try again."
    const val UPDATE_OFFLINE = "Failed to update authorized user. Check your connection and try again."
    const val ADD_RETRY = "Failed to add authorized user. Please try again."
    const val UPDATE_RETRY = "Failed to update authorized user. Please try again."
    const val SESSION_EXPIRED = "Your session has expired. Please sign in again."

    const val DEFAULT_ISO = "JM"
    const val MAX_DIGITS = 15
    val SUGGESTED_ISOS = listOf("JM", "US", "CA", "GB")

    /**
     * Calling codes for the territories CountryCatalog lists with no dial code
     * (verifier 2026-09-22: they were missing from the picker). The catalog is
     * frozen to the Swift table and also renders the checkout country rows, so
     * the gap is filled here rather than in it. ITU-T E.164 assignments; SX is
     * a NANP territory, so it is "+1" like Jamaica. Regions with no public
     * numbering of their own (BV, GS, HM, TF, UM) stay out.
     */
    internal val TERRITORY_DIAL_CODES = mapOf(
        "GG" to "+44", "IM" to "+44", "JE" to "+44",
        "AX" to "+358",
        "BL" to "+590", "MF" to "+590",
        "BQ" to "+599", "CW" to "+599",
        "SX" to "+1721",
        "PS" to "+970",
        "SS" to "+211",
        "SH" to "+290",
        "XK" to "+383",
    )

    // Laravel CallingCodes::MIN_NATIONAL_DIGITS, pinned by the contract fixture.
    private val MINIMUM_NATIONAL_DIGITS = mapOf(
        "+290" to 4, "+683" to 4, "+690" to 4,
        "+500" to 5, "+676" to 5, "+677" to 5, "+678" to 5, "+682" to 5, "+685" to 5, "+686" to 5, "+688" to 5,
        "+298" to 6, "+299" to 6, "+376" to 6, "+508" to 6, "+672" to 6, "+681" to 6, "+687" to 6, "+689" to 6,
    )

    private val DROPS_TRUNK_ZERO = setOf(
        "+20", "+27", "+31", "+32", "+33", "+41", "+43", "+44", "+46", "+49", "+51", "+60", "+61", "+62",
        "+63", "+64", "+66", "+81", "+82", "+84", "+86", "+90", "+91", "+92", "+94", "+98", "+233", "+234",
        "+250", "+251", "+254", "+255", "+256", "+260", "+263", "+353", "+358", "+380", "+880", "+966",
        "+971", "+972", "+977",
    )

    /**
     * Laravel CallingCodes::LONGEST_NATIONAL_NUMBER (v1.28): a number typed
     * without "+" that starts with its own calling code loses that code only
     * when it is longer than the country's longest national number — 10
     * unless listed, the old "11+ digits" rule. Brazil's national numbers are
     * 10 or 11 digits (a 2-digit area code), so 55991234567 is area code 55
     * and stays whole, while 5555991234567 is the code typed again. Germany
     * is left out on purpose: its numbers run 5 to 13 digits, so length
     * cannot tell 4921 1234567 from "+49" typed again.
     */
    private val LONGEST_NATIONAL_NUMBER = mapOf("+55" to 11)

    /**
     * Laravel CallingCodes::AMBIGUOUS_REPEATED_CODE_LENGTHS and _HINTS (v1.29,
     * Kemar: "Ask the customer"): a number that starts with its own calling
     * code at one of these lengths, typed without "+" or a trunk 0, can be read
     * neither way. Germany, 11 digits: 4921 1234567 may be Emden (04921) or
     * "+49" typed before Düsseldorf (0211), and the East Frisian area codes
     * collide with the big cities', so no area-code rule can tell. The box
     * keeps it as typed and asks for "+49" or the 0 ([ambiguityError]).
     */
    private val AMBIGUOUS_REPEATED_CODE_LENGTHS = mapOf("+49" to setOf(11))
    private val AMBIGUOUS_REPEATED_CODE_HINTS = mapOf(
        "+49" to "For a German number, start with +49, or with 0 as dialled in Germany.",
    )

    /**
     * The Caribbean (and Pacific) NANP area codes and the country each one
     * dials — CallingCodes::NANP_CARIBBEAN_AREA_CODES and the website's
     * PHONE_NANP_AREA_ISOS. Written the local way with a "+" ("+876 555 1234",
     * "+868 555 1234") they look like calling codes, and 65, 86, 44, 34, 242
     * and 7 really are, so the server reads "+" and exactly one of these
     * ten-digit numbers as +1. The box reads them the same way.
     */
    internal val NANP_CARIBBEAN_AREA_ISOS = mapOf(
        "242" to "BS", "246" to "BB", "264" to "AI", "268" to "AG", "284" to "VG",
        "340" to "VI", "345" to "KY", "441" to "BM", "473" to "GD", "649" to "TC",
        "658" to "JM", "664" to "MS", "670" to "MP", "671" to "GU", "684" to "AS",
        "721" to "SX", "758" to "LC", "767" to "DM", "784" to "VC", "787" to "PR",
        "809" to "DO", "829" to "DO", "849" to "DO", "868" to "TT", "869" to "KN",
        "876" to "JM", "939" to "PR",
    )

    /**
     * Where a code shared by several countries opens (typed "+7" is Russia,
     * not Kazakhstan, which merely sorts first). "+1" is decided by area code
     * in [isoFor].
     */
    private val PRIMARY_ISO_BY_CODE = mapOf(
        "+7" to "RU",
        "+39" to "IT",
        "+44" to "GB",
        "+262" to "RE",
        "+358" to "FI",
        "+590" to "GP",
        "+599" to "CW",
    )

    /**
     * Every catalog country that has a dial code, as a CALLING code. The
     * catalog stores NANP territories with their area code ("+1876", "+1246");
     * the server wants "+1" and the area code inside the number, exactly like
     * the website's PHONE_COUNTRIES.
     */
    val countries: List<AuthorizedUserPhoneCountry> by lazy { countriesFrom(CountryCatalog.all) }

    /** Picker rows for catalog rows; a territory the catalog leaves without a code gets it from [TERRITORY_DIAL_CODES]. */
    internal fun countriesFrom(entries: List<CountryEntry>): List<AuthorizedUserPhoneCountry> =
        entries.mapNotNull { entry ->
            // Keep checkout's table intact while matching the phone API:
            // Vatican uses +39; the API phone picker does not offer +870.
            val dial = if (entry.isoCode == "VA") "+39" else entry.dialCode ?: TERRITORY_DIAL_CODES[entry.isoCode]
            if (dial == "+870") return@mapNotNull null
            val calling = dial?.let(::callingCode) ?: return@mapNotNull null
            AuthorizedUserPhoneCountry(entry.isoCode, entry.name, entry.flagEmoji, calling)
        }

    /** Distinct calling codes, longest first, for reading a typed "+CC". */
    private val callingCodes: List<String> by lazy {
        countries.map(AuthorizedUserPhoneCountry::callingCode).distinct().sortedByDescending { it.length }
    }

    private val validCallingCodes: Set<String> by lazy {
        callingCodes.toSet() + TERRITORY_DIAL_CODES.values.mapNotNull(::callingCode)
    }

    fun callingCode(dialCode: String): String? {
        val digits = dialCode.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return if (digits.startsWith("1")) "+1" else "+" + digits.take(4)
    }

    fun country(iso: String): AuthorizedUserPhoneCountry? {
        val upper = iso.trim().uppercase(Locale.US)
        return countries.firstOrNull { it.isoCode == upper }
    }

    /** Search by name, ISO or calling code — "jam", "JM", "+44", "44". */
    fun search(query: String): List<AuthorizedUserPhoneCountry> {
        val q = query.trim().lowercase(Locale.US)
        return if (q.isEmpty()) countries else countries.filter { it.searchText.contains(q) }
    }

    /**
     * Where the picker opens (verifier 2026-09-22): the customer's own country
     * when the profile knows it, otherwise Jamaica — the "+1" this business
     * serves, and the website's default. The device region counts only when it
     * is itself a "+1" (NANP) region: a UK or Spanish phone used to open on
     * +44 / +34, and 15550199 was then saved as a +44 number nobody chose.
     */
    fun defaultIso(profileCountryName: String?, deviceRegion: String?): String {
        profileIso(profileCountryName)?.let { return it }
        deviceRegion?.trim()?.uppercase(Locale.US)
            ?.takeIf { it.isNotEmpty() && country(it)?.callingCode == "+1" }
            ?.let { return it }
        return DEFAULT_ISO
    }

    /** The picker country for a profile country — "Jamaica", "united kingdom", "GB" — or null when unknown. */
    fun profileIso(profileCountry: String?): String? {
        val value = profileCountry?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return CountryCatalog.entry(value)?.isoCode?.takeIf { country(it) != null }
    }

    /**
     * One edit to the mobile box, and what it means. The TextField hands over
     * the whole box after every key, so this runs once per key as well as on a
     * paste (verifier 2026-09-22: "+44 7911 123456" typed one key at a time was
     * sent as +44 / 447911123456 — the "+" was stripped before the code after
     * it had arrived, so the code stayed in the number).
     *
     * A leading "+" or "00" — or under +1 the exit code 011 before more than
     * eleven digits ([internationalDigits]) — starts an international number,
     * read in the server's order (App\Support\AuthorizedUserPhone::normalize,
     * step 1):
     *
     *  1. The picker's own calling code (not +1) typed in front wins: with
     *     Singapore picked, "+65 8555 1234" is 🇸🇬 85551234.
     *  2. While the digits can still be a Caribbean number written the local
     *     way ("+876 555 1234", "+658…", "+868…", "+441…"), the box keeps them
     *     as typed and the picker stays put (2026-09-23 audit: "+658" became
     *     Singapore at "+65", and "+876 555 1234" could never be saved). An
     *     11th digit, or a first three that are no Caribbean area code, ends
     *     the wait; [resolve] settles a number still waiting on blur and Save.
     *  3. Otherwise the calling code the digits start with moves the picker
     *     and the digits after it stay as typed — never cut for a repeated
     *     code: "+55 55 99123 4567" is 🇧🇷 55991234567, area code 55 kept.
     *     Typed or pasted, "+44 7911 123456" is 🇬🇧 +44 / 7911123456 (typed,
     *     the picker moves at "+447"). A code not finished yet ("+5") stays
     *     in the box for the next key; one that names no country stays for
     *     Save to refuse. "+1" from a picker with another code waits for its
     *     area code: "+1 868 555 1234" is 🇹🇹 8685551234.
     *
     * Otherwise the box holds digits only, and a full number that repeats the
     * selected country's own code in front loses it: 447911123456 under +44 is
     * 7911123456; under +1 that is the trunk 1 (18765551234 → 8765551234).
     * "Full" is longer than the country's longest national number: Brazil's
     * 55991234567 (area code 55) stays whole. Digits typed after an explicit
     * "+CC" ([AuthorizedUserPhoneEntry.explicitCode] of [previous]) keep a repeated code;
     * only the +1 trunk 1 still goes, as it does on the server. Either way one
     * trunk 0 goes where the server drops it: "+44 07911 123456" and 07911
     * 123456 under 🇬🇧 are both 7911123456.
     *
     * [previous] is the entry before this edit — its picker country, its box,
     * and its flag. The flag belongs to the digits it was set for, so it holds
     * only while the edit keeps how they start ([keepsStart]): typed on,
     * deleted back, a digit further along corrected. Select all and paste, or
     * digits typed in front, is a new number read afresh (2026-09-24 audit:
     * 447911123456 pasted over 🇬🇧 7911123456, whose trunk 0 had set the flag,
     * went as +44447911123456 and was stored 447911123456). Emptying the box
     * starts over.
     */
    fun interpret(raw: String, previous: AuthorizedUserPhoneEntry): AuthorizedUserPhoneEntry {
        val currentIso = previous.isoCode
        val digits = raw.filter(Char::isDigit)
        val code = country(currentIso)?.callingCode ?: "+1"
        internationalDigits(raw, digits, code)?.let { international ->
            return readInternational(raw, digits, international, currentIso, waitForCaribbean = true)
        }
        val continues = raw.isNotEmpty() && keepsStart(previous.number.filter(Char::isDigit), digits, code)
        val explicit = previous.explicitCode && continues
        val (number, dropped) = boxDigits(code, digits, explicit)
        return AuthorizedUserPhoneEntry(
            currentIso,
            number,
            explicitCode = explicit || dropped,
            droppedTrunkZero = dropped || previous.droppedTrunkZero && continues,
        )
    }

    /**
     * [raw] read on its own, as if typed into an empty box under [currentIso];
     * [explicitCode]: a "+CC" was read just before it (see [interpret]).
     */
    fun interpret(raw: String, currentIso: String, explicitCode: Boolean = false): AuthorizedUserPhoneEntry =
        interpret(raw, AuthorizedUserPhoneEntry(currentIso, "", explicitCode))

    /**
     * [after] starts the way [before] did, digits both: as far as the shorter
     * of them goes, and no further than the calling code's length and one
     * digit more — the part that decides whether a code typed again is read
     * in front of the number. One digit is not enough: India's 919876543210
     * pasted over 9876543210 shares its first digit, as does Hamburg's
     * 494012345678 pasted over 4012345678. Nothing before (the digits after a
     * "+CC") is always continued; nothing after, only then.
     */
    private fun keepsStart(before: String, after: String, callingCode: String): Boolean {
        if (after.isEmpty()) return before.isEmpty()
        val start = minOf(before.length, after.length, callingCode.count(Char::isDigit) + 1)
        return before.regionMatches(0, after, 0, start)
    }

    /**
     * The box settled for good — when the customer leaves it, and before Save
     * judges or sends it. An international number [interpret] was still
     * waiting on is read the rest of the way, as the server reads it: exactly
     * ten digits after the "+" that start with a Caribbean area code are that
     * area's +1 number ("+876 555 1234" → 🇯🇲 8765551234, "+868…" → 🇹🇹,
     * "+441…" → 🇧🇲); anything else by the calling code it starts with.
     * What names no country stays as typed, for validation to refuse. A box
     * of digits is already settled and comes back unchanged.
     */
    fun resolve(entry: AuthorizedUserPhoneEntry): AuthorizedUserPhoneEntry {
        val digits = entry.number.filter(Char::isDigit)
        val code = country(entry.isoCode)?.callingCode ?: "+1"
        val international = internationalDigits(entry.number, digits, code) ?: return entry
        return readInternational(entry.number, digits, international, entry.isoCode, waitForCaribbean = false)
    }

    /**
     * The digits after an international prefix in front of [digits], read as
     * the server reads one (AuthorizedUserPhone::normalize, step 1): a "+"
     * ([hasLeadingPlus]), a "00", or under +1 the NANP exit code 011 in front
     * of more than eleven digits — "011 44 7911 123456" from Jamaica is 🇬🇧
     * 7911123456 (2026-09-24 audit: the box refused it). Eleven digits or
     * fewer are read as a +1 number, as the server reads them. null: none.
     */
    private fun internationalDigits(raw: String, digits: String, callingCode: String): String? = when {
        hasLeadingPlus(raw) -> digits
        digits.startsWith("00") -> digits.substring(2)
        callingCode == "+1" && digits.startsWith("011") && digits.length > 11 -> digits.substring(3)
        else -> null
    }

    /**
     * Whether [raw] starts with an international "+", read the way the server
     * reads it (AuthorizedUserPhone::normalize: every character but a digit or
     * "+" removed, then the first one left): a "+" behind an invisible mark (a
     * number pasted from Contacts or a chat often starts with U+200E), inside
     * brackets "(+44)", or after "tel:" counts; one after a digit
     * ("876+5551234") does not.
     */
    private fun hasLeadingPlus(raw: String): Boolean =
        raw.firstOrNull { it == '+' || it in '0'..'9' } == '+'

    /** Steps 1–3 of [interpret]; [waitForCaribbean] false is [resolve]. */
    private fun readInternational(
        raw: String,
        digits: String,
        international: String,
        currentIso: String,
        waitForCaribbean: Boolean,
    ): AuthorizedUserPhoneEntry {
        val plus = hasLeadingPlus(raw)
        // The number is not settled yet (or names no country): keep what was
        // typed so the next key can finish it and Save can refuse it.
        val pending = AuthorizedUserPhoneEntry(
            currentIso,
            if (plus) "+" + international.take(MAX_DIGITS) else digits.take(MAX_DIGITS),
        )

        val own = country(currentIso)?.callingCode
        if (own != null && own != "+1" && international.startsWith(own.substring(1))) {
            val (number, dropped) = boxDigits(own, international.substring(own.length - 1), explicit = true)
            return AuthorizedUserPhoneEntry(currentIso, number, explicitCode = true, droppedTrunkZero = dropped)
        }
        if (waitForCaribbean) {
            if (couldBeCaribbeanNumber(international)) return pending
        } else if (isCaribbeanNumber(international)) {
            return AuthorizedUserPhoneEntry(isoFor("+1", international), international, explicitCode = true)
        }
        val code = callingCodes.firstOrNull { international.startsWith(it.substring(1)) } ?: return pending
        val national = international.substring(code.length - 1)
        // "+1" alone does not say which +1 country. From a picker with another
        // code it waits for the area code, which then names it — "+1 868…" is
        // 🇹🇹, "+1 212…" 🇺🇸, as in Swift; a +1 country already picked stays.
        if (code == "+1" && own != "+1" && national.length < 3) return pending
        val (number, dropped) = boxDigits(code, national, explicit = true)
        return AuthorizedUserPhoneEntry(
            isoCode = isoForTypedCode(code, national, currentIso),
            number = number,
            explicitCode = true,
            droppedTrunkZero = dropped,
        )
    }

    /** CallingCodes::isCaribbeanNanpNumber: ten digits whose area code is a Caribbean one. */
    private fun isCaribbeanNumber(digits: String): Boolean =
        digits.length == 10 && digits.take(3) in NANP_CARIBBEAN_AREA_ISOS

    /**
     * Digits after a "+" that can still grow into [isCaribbeanNumber]: at most
     * ten, starting with a Caribbean area code — or, under three digits, with
     * the start of one.
     */
    private fun couldBeCaribbeanNumber(digits: String): Boolean = when {
        digits.length > 10 -> false
        digits.length < 3 -> NANP_CARIBBEAN_AREA_ISOS.keys.any { it.startsWith(digits) }
        else -> digits.take(3) in NANP_CARIBBEAN_AREA_ISOS
    }

    /**
     * [digits] as the box keeps them under [callingCode] — the national number
     * the server stores, normalized once as it does (normalize step 7) — and
     * whether a trunk 0 went ([AuthorizedUserPhoneEntry.droppedTrunkZero]).
     * After an explicit "+CC" a repeated code is never cut (the server's
     * $explicit); the +1 trunk 1 is, typed after "+1" or not, because the
     * server drops it either way. Then one trunk 0 goes for the codes that
     * drop it ([submissionDigits]): "+44 07911 123456" shows 7911123456. What
     * followed that 0 is the national number as written
     * ([AuthorizedUserPhoneEntry.explicitCode]), so the next keys never cut a
     * code from it: the server cuts a repeated code before it drops the 0,
     * never after.
     */
    private fun boxDigits(callingCode: String, digits: String, explicit: Boolean): Pair<String, Boolean> {
        val national = if (explicit && callingCode != "+1") {
            digits.take(MAX_DIGITS)
        } else {
            nationalDigits(digits, callingCode)
        }
        val shown = submissionDigits(national, callingCode)
        return shown to (shown != national)
    }

    /**
     * Digits under [callingCode], capped: a number longer than the country's
     * longest national number ([LONGEST_NATIONAL_NUMBER]: 11+ digits, 12+ for
     * Brazil) that starts with the code itself had the code typed in front of
     * it, so the code goes — the trunk 1 of an 11-digit +1 number included.
     * Not at a length where that cannot be told ([isAmbiguousRepeatedCode]:
     * Germany, 11 digits): those stay as typed, and the customer is asked.
     */
    fun nationalDigits(digits: String, callingCode: String): String {
        val code = callingCode.filter(Char::isDigit)
        val longest = LONGEST_NATIONAL_NUMBER[callingCode] ?: 10
        // Neither reading can be trusted: kept as typed, for [ambiguityError] to ask.
        if (isAmbiguousRepeatedCode(digits, callingCode)) return digits.take(MAX_DIGITS)
        val national = if (code.isNotEmpty() && digits.length > longest && digits.startsWith(code)) {
            digits.substring(code.length)
        } else {
            digits
        }
        return national.take(MAX_DIGITS)
    }

    /**
     * `user_mobile_number` for the number in the box: its digits — except when
     * they start with the calling code (not +1) and run to 11+ digits. As bare
     * digits the server could take that code for one typed twice and cut it
     * (AuthorizedUserPhone::normalize step 7), storing a number the box never
     * showed, so they go as "+" + code + digits: the picker's own code typed
     * in front, which every server version keeps whole. That covers
     * "+55 55 99123 4567" (Brazil, area code 55: "+5555991234567", the Laravel
     * handoff's contract row, as Swift sends it since SWIFT_APP #51) and a
     * number the box already normalized once, such as German 049112345678,
     * whose trunk 0 the box dropped: 49112345678 goes as "+4949112345678", so
     * the server does not normalize it a second time. The threshold stays the
     * pre-v1.28 "11+" on purpose, so it is right against either server.
     * Every other number stays digits only.
     */
    fun requestNumber(digits: String, callingCode: String): String {
        val code = callingCode.filter(Char::isDigit)
        val repeatsCode = code.isNotEmpty() && digits.length >= 11 && digits.startsWith(code) &&
            digits.length - code.length >= 7
        return if (code != "1" && repeatsCode) "+$code$digits" else digits
    }

    /**
     * The box after the picker moves to a different [callingCode], re-read
     * under it: a repeated code cut, one trunk 0 dropped. A half-typed "+CC"
     * is left for the next key.
     */
    fun renumber(box: String, callingCode: String): String = renumbered(box, callingCode).first

    /**
     * [renumber], and whether a trunk 0 went under [callingCode] (the digits
     * are then the national number as written). [droppedTrunkZero]: the box
     * had dropped one in front of its digits under the old code; it goes back
     * first, so the digits are read as typed (2026-09-24 audit: 🇬🇧 06 1234
     * 5678 shows 612345678, and picking Italy then sent +39 612345678 where
     * the server, and the same keys under Italy, keep 0612345678).
     */
    internal fun renumbered(box: String, callingCode: String, droppedTrunkZero: Boolean = false): Pair<String, Boolean> =
        if (box.all(Char::isDigit)) {
            val typed = if (droppedTrunkZero && box.isNotEmpty()) "0$box" else box
            boxDigits(callingCode, typed, explicit = false)
        } else {
            box to false
        }

    /** A typed code keeps the picker where it is when it already says that code. */
    private fun isoForTypedCode(code: String, national: String, currentIso: String): String {
        if (country(currentIso)?.callingCode == code) return currentIso
        return isoFor(code, national)
    }

    /**
     * One national trunk 0 dropped, for the codes the server drops it for
     * (CallingCodes::DROPS_TRUNK_ZERO), as it drops it once: never a lone "0",
     * never "00" (an international prefix). The box applies it as the digits
     * come in ([boxDigits], [renumber]), so it shows what the server
     * stores; validation applies it too, where it finds nothing left to drop.
     * A stored phone an edit leaves alone is never re-read by it.
     */
    fun submissionDigits(digits: String, callingCode: String): String =
        if (digits.length > 1 && digits.startsWith("0") && !digits.startsWith("00") && callingCode in DROPS_TRUNK_ZERO) {
            digits.substring(1)
        } else {
            digits
        }

    /**
     * [digits] under [callingCode] start with the code at a length where that
     * cannot be told from the national number ([AMBIGUOUS_REPEATED_CODE_LENGTHS]).
     */
    fun isAmbiguousRepeatedCode(digits: String, callingCode: String): Boolean {
        val code = callingCode.filter(Char::isDigit)
        return digits.length in AMBIGUOUS_REPEATED_CODE_LENGTHS[callingCode].orEmpty() &&
            code.isNotEmpty() && digits.startsWith(code)
    }

    /**
     * The server's refusal (AuthorizedUserPhone::mobileProblem) for a box of
     * digits typed without "+" or a trunk 0 ([explicitCode] false) that
     * [isAmbiguousRepeatedCode]: "Please enter a valid phone number. For a
     * German number, start with +49, or with 0 as dialled in Germany." — said
     * under the field on blur and Save, which it blocks. null otherwise:
     * "+49 4921 1234567", "0049 …" and "04921 1234567" read as they are.
     */
    fun ambiguityError(digits: String, callingCode: String, explicitCode: Boolean): String? {
        if (explicitCode || !isAmbiguousRepeatedCode(digits, callingCode)) return null
        val hint = AMBIGUOUS_REPEATED_CODE_HINTS[callingCode] ?: "Start with + and the country code."
        return "$FIELD_ERROR $hint"
    }

    /** null when the normalized number is sendable; otherwise the field message. */
    fun validationError(digits: String, callingCode: String): String? {
        if (callingCode !in validCallingCodes || digits.isEmpty() || !digits.all { it in '0'..'9' }) return FIELD_ERROR
        // "00" is an international prefix whose country code never resolved.
        if (digits.startsWith("00")) return FIELD_ERROR
        return if (callingCode == "+1") {
            if (digits.length == 10 && digits.first() in '2'..'9') null else FIELD_ERROR
        } else {
            val minimum = MINIMUM_NATIONAL_DIGITS[callingCode] ?: 7
            val maximum = MAX_DIGITS - (callingCode.length - 1)
            if (digits.length in minimum..maximum) null else FIELD_ERROR
        }
    }

    /**
     * A stored row may carry the area code inside the code column ("+1876" +
     * 7 digits) — the shape the website folds too. Returns the picker ISO and
     * the digits the box should show.
     */
    fun fold(storedCode: String?, number: String?): Pair<String, String> {
        val codeDigits = storedCode.orEmpty().filter(Char::isDigit)
        var numberDigits = number.orEmpty().filter(Char::isDigit)
        var calling: String? = null
        when {
            codeDigits.length == 4 && codeDigits.startsWith("1") && numberDigits.length == 7 -> {
                numberDigits = codeDigits.substring(1) + numberDigits
                calling = "+1"
            }
            codeDigits.isEmpty() -> {
                if (numberDigits.length == 11 && numberDigits.startsWith("1")) {
                    numberDigits = numberDigits.substring(1)
                    calling = "+1"
                } else if (numberDigits.length == 10) {
                    calling = "+1"
                }
            }
            else -> calling = callingCode("+$codeDigits")
        }
        return isoFor(calling, numberDigits) to numberDigits.take(MAX_DIGITS)
    }

    /**
     * "+1" is many countries: a Caribbean area code picks its own country
     * ([NANP_CARIBBEAN_AREA_ISOS]: 876 and 658 Jamaica, 868 Trinidad and
     * Tobago, 441 Bermuda, …), anything else under +1 the United States — the
     * website's rule. A code several countries share opens on its main one
     * ([PRIMARY_ISO_BY_CODE]); otherwise the first catalog match.
     */
    fun isoFor(callingCode: String?, digits: String): String {
        if (callingCode == null) return DEFAULT_ISO
        if (callingCode == "+1") {
            NANP_CARIBBEAN_AREA_ISOS[digits.take(3)]?.takeIf { country(it) != null }?.let { return it }
            return if (country("US") != null) "US" else DEFAULT_ISO
        }
        PRIMARY_ISO_BY_CODE[callingCode]?.takeIf { country(it) != null }?.let { return it }
        return countries.firstOrNull { it.callingCode == callingCode }?.isoCode ?: DEFAULT_ISO
    }

    /** The field-level message the server sent for the phone, if any. */
    fun serverPhoneError(fieldErrors: Map<String, String>): String? =
        fieldErrors["user_mobile_number"] ?: fieldErrors["user_country_code"]

    /**
     * The snackbar after a failed save — only a 422 that names the phone
     * blames the phone (verifier 2026-09-22).
     *
     * @param status the HTTP status, or null when no HTTP answer came back
     * @param offline the request never reached the server (an IOException)
     * @param serverMessage the JSON `message` of the error body; an HTML page
     *   never parses as one, so it is never shown
     * @param fieldErrors Laravel's `errors`, first message per field
     */
    fun saveFailureMessage(
        isEdit: Boolean,
        status: Int?,
        offline: Boolean,
        serverMessage: String?,
        fieldErrors: Map<String, String>,
    ): String = when {
        serverPhoneError(fieldErrors) != null -> if (isEdit) UPDATE_FAILED else ADD_FAILED
        offline -> if (isEdit) UPDATE_OFFLINE else ADD_OFFLINE
        status == 401 -> SESSION_EXPIRED
        // Another field, in the server's words: a duplicate email is not a phone problem.
        fieldErrors.isNotEmpty() -> fieldErrors.values.first()
        // A JSON client error says something the customer can act on ("Too
        // Many Attempts."); a 5xx never does ("Server Error").
        status != null && status in 400..499 && !serverMessage.isNullOrBlank() -> serverMessage
        else -> if (isEdit) UPDATE_RETRY else ADD_RETRY
    }
}
