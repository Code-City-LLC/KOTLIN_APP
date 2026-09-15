package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.location.CountryCatalog
import java.util.Locale

/**
 * The authorized-user mobile number, the way the website and the server now
 * speak it (Kemar 2026-09-15; Laravel App\Support\AuthorizedUserPhone):
 *
 *     user_country_code  = the CALLING code — "+1" for Jamaica, "+44" for the UK
 *     user_mobile_number = digits only, 7–15 of them; exactly 10 under "+1"
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

object AuthorizedUserPhoneInput {
    const val FIELD_ERROR = "Please enter a valid phone number."
    const val ADD_FAILED = "Failed to add authorized user. Please check the phone number and try again."
    const val UPDATE_FAILED = "Failed to update authorized user. Please check the phone number and try again."
    const val DEFAULT_ISO = "JM"
    const val MAX_DIGITS = 15
    val SUGGESTED_ISOS = listOf("JM", "US", "CA", "GB")

    /**
     * Every catalog country that has a dial code, as a CALLING code. The
     * catalog stores NANP territories with their area code ("+1876", "+1246");
     * the server wants "+1" and the area code inside the number, exactly like
     * the website's PHONE_COUNTRIES.
     */
    val countries: List<AuthorizedUserPhoneCountry> by lazy {
        CountryCatalog.all.mapNotNull { entry ->
            val calling = entry.dialCode?.let(::callingCode) ?: return@mapNotNull null
            AuthorizedUserPhoneCountry(entry.isoCode, entry.name, entry.flagEmoji, calling)
        }
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
     * Where the picker opens: the customer's own country when the profile
     * knows it, else the device region, else Jamaica.
     */
    fun defaultIso(profileCountryName: String?, deviceRegion: String?): String {
        profileCountryName
            ?.let { CountryCatalog.entryForName(it)?.isoCode }
            ?.takeIf { country(it) != null }
            ?.let { return it }
        deviceRegion?.trim()?.uppercase(Locale.US)
            ?.takeIf { it.isNotEmpty() && country(it) != null }
            ?.let { return it }
        return DEFAULT_ISO
    }

    /**
     * Digits only, capped. A pasted "+1 (876) 555-1234" under +1 becomes
     * "8765551234": the selected code is dropped when the text carried it, and
     * so is the trunk 1 a +1 number is often typed with.
     */
    fun sanitize(raw: String, callingCode: String): String {
        val hadPlus = raw.trim().startsWith("+")
        var digits = raw.filter(Char::isDigit)
        val code = callingCode.filter(Char::isDigit)
        if (hadPlus && code.isNotEmpty() && digits.startsWith(code) && digits.length > code.length) {
            digits = digits.substring(code.length)
        }
        if (code == "1" && digits.length == 11 && digits.startsWith("1")) {
            digits = digits.substring(1)
        }
        return digits.take(MAX_DIGITS)
    }

    /** null when the number is sendable; otherwise the message that goes under the field. */
    fun validationError(digits: String, callingCode: String): String? {
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return FIELD_ERROR
        return if (callingCode == "+1") {
            if (digits.length == 10) null else FIELD_ERROR
        } else {
            if (digits.length in 7..15) null else FIELD_ERROR
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
     * else under +1 picks the United States. Other codes: first catalog match.
     */
    fun isoFor(callingCode: String?, digits: String): String {
        if (callingCode == null) return DEFAULT_ISO
        if (callingCode == "+1") {
            return if (digits.startsWith("876") || digits.startsWith("658")) "JM"
            else if (country("US") != null) "US" else DEFAULT_ISO
        }
        return countries.firstOrNull { it.callingCode == callingCode }?.isoCode ?: DEFAULT_ISO
    }

    /** The field-level message the server sent for the phone, if any. */
    fun serverPhoneError(fieldErrors: Map<String, String>): String? =
        fieldErrors["user_mobile_number"] ?: fieldErrors["user_country_code"]
}
