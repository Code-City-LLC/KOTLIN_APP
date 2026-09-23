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
data class AuthorizedUserPhoneEntry(val isoCode: String, val number: String)

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
     * Where a code shared by several countries opens (typed "+7" is Russia,
     * not Kazakhstan, which merely sorts first). "+1" is decided by area code
     * in [isoFor].
     */
    private val PRIMARY_ISO_BY_CODE = mapOf(
        "+7" to "RU",
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
     *  - A leading "+" or "00" starts a country code. While that code is still
     *    being typed the box keeps it ("+4"), so the next key can finish it;
     *    the moment it names a calling code the picker moves to that country
     *    and only the national digits stay. Typed or pasted, "+44 7911 123456"
     *    is 🇬🇧 +44 / 7911123456.
     *  - Otherwise the box holds digits only, and a full number that repeats the
     *    selected country's own code in front loses it: 447911123456 under +44
     *    is 7911123456; under +1 that is the trunk 1 (18765551234 → 8765551234).
     */
    fun interpret(raw: String, currentIso: String): AuthorizedUserPhoneEntry {
        val digits = raw.filter(Char::isDigit)
        val plus = raw.trimStart().startsWith("+")
        val international = when {
            plus -> digits
            digits.startsWith("00") -> digits.substring(2)
            else -> null
        }
        if (international != null) {
            val code = callingCodes.firstOrNull { international.startsWith(it.substring(1)) }
            if (code != null) {
                val national = international.substring(code.length - 1)
                return AuthorizedUserPhoneEntry(
                    isoCode = isoForTypedCode(code, national, currentIso),
                    number = nationalDigits(national, code),
                )
            }
            // The code is not finished (or names no country): keep what was
            // typed so the next key can complete it and Save can refuse it.
            val pending = if (plus) "+" + international.take(MAX_DIGITS) else digits.take(MAX_DIGITS)
            return AuthorizedUserPhoneEntry(currentIso, pending)
        }
        val code = country(currentIso)?.callingCode ?: "+1"
        return AuthorizedUserPhoneEntry(currentIso, nationalDigits(digits, code))
    }

    /**
     * Digits under [callingCode], capped: a full number (11+ digits) that
     * starts with the code itself had the code typed in front of it, so the
     * code goes — the trunk 1 of an 11-digit +1 number included.
     */
    fun nationalDigits(digits: String, callingCode: String): String {
        val code = callingCode.filter(Char::isDigit)
        val national = if (code.isNotEmpty() && digits.length >= 11 && digits.startsWith(code)) {
            digits.substring(code.length)
        } else {
            digits
        }
        return national.take(MAX_DIGITS)
    }

    /** The box after the picker moves to [callingCode]; a half-typed "+CC" is left for the next key. */
    fun renumber(box: String, callingCode: String): String =
        if (box.all(Char::isDigit)) nationalDigits(box, callingCode) else box

    /** A typed code keeps the picker where it is when it already says that code. */
    private fun isoForTypedCode(code: String, national: String, currentIso: String): String {
        if (country(currentIso)?.callingCode == code) return currentIso
        // "+1" alone does not say which +1 country; Jamaica until an area code does.
        if (code == "+1" && national.length < 3) return DEFAULT_ISO
        return isoFor(code, national)
    }

    /** Validation candidate only; Laravel must normalize the original wire value once. */
    fun submissionDigits(digits: String, callingCode: String): String =
        if (digits.length > 1 && digits.startsWith("0") && !digits.startsWith("00") && callingCode in DROPS_TRUNK_ZERO) {
            digits.substring(1)
        } else {
            digits
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
     * "+1" is many countries: a Jamaican area code picks Jamaica, anything
     * else under +1 picks the United States. A code several countries share
     * opens on its main one ([PRIMARY_ISO_BY_CODE]); otherwise the first
     * catalog match.
     */
    fun isoFor(callingCode: String?, digits: String): String {
        if (callingCode == null) return DEFAULT_ISO
        if (callingCode == "+1") {
            return if (digits.startsWith("876") || digits.startsWith("658")) "JM"
            else if (country("US") != null) "US" else DEFAULT_ISO
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
