package com.ga.airdrop.core.validation

import com.ga.airdrop.feature.more2.AuthorizedUserPhoneInput
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Field validators — Kotlin port of Swift ProfileValidator (the app-wide SSOT;
 * Swift consolidated four divergent email regexes into this one on 2026-06-15).
 * Shared by Sign-Up and Edit-Profile so both enforce the same rules the
 * backend expects.
 */
object ProfileRules {

    /** Swift ProfileValidator.isValidEmail — requires a 2+ letter TLD. */
    private val EMAIL = Regex("^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** RN isValidPhone — loose: +, parens, dashes, spaces, dots. */
    private val PHONE = Regex("^[+]?[(]?[0-9]{1,4}[)]?[-\\s.]?[(]?[0-9]{1,4}[)]?[-\\s.]?[0-9]{1,9}$")

    private val TRN = Regex("^\\d{9}$")
    private val ID_GENERAL = Regex("^[A-Za-z0-9]{5,20}$")
    private val ID_PASSPORT = Regex("^[A-Za-z0-9]{6,9}$")

    fun isValidEmail(email: String): Boolean =
        email.isNotEmpty() && EMAIL.matches(email)

    // ─── Mobile number ───
    //
    // Kemar, 2026-10-17: "For mobile number, we shouldn't have 1876 ... 1876 is
    // not the only caller ID. Same for other countries. Just put the plus and
    // allow them to complete the remainder of it." Sign Up and Edit Profile
    // take the mobile in ONE field that opens on "+1", and the customer types
    // the rest of any country's number.

    /** What the mobile field holds before the customer types. */
    const val MOBILE_PREFIX = "+1"
    const val MOBILE_PLACEHOLDER = "e.g. +1 234 567 8900"
    const val MOBILE_ERROR =
        "Enter your mobile number with the country code, for example +1 234 567 8900."

    /** "+" then 8–15 digits, once spaces, dashes, dots and brackets are gone. */
    private val MOBILE = Regex("^\\+\\d{8,15}$")
    private val MOBILE_SEPARATORS = Regex("[\\s().-]")

    /**
     * The API's two fields for a mobile number as typed: user_country_code with
     * its "+", and user_mobile as the national digits only. "+1 876 529 0736"
     * is "+1" / "8765290736". The calling code comes from the Authorized User
     * phone table ([AuthorizedUserPhoneInput.splitInternational]). null when the
     * number breaks the rule or no calling code starts it.
     */
    fun mobileForApi(mobile: String): Pair<String, String>? {
        val compact = mobile.replace(MOBILE_SEPARATORS, "")
        if (!MOBILE.matches(compact)) return null
        return AuthorizedUserPhoneInput.splitInternational(compact.substring(1))
    }

    /** The mobile number can be sent ([mobileForApi]). */
    fun isValidMobile(mobile: String): Boolean = mobileForApi(mobile) != null

    /**
     * Edit Profile's mobile field for the stored number: "+CC national", read
     * from user_country_code + user_mobile the way the server reads a stored
     * pair, so an old "1-876" / "5290736" shows "+1 8765290736". [MOBILE_PREFIX]
     * when nothing is stored; a number no calling code can be read from shows
     * as stored, for the customer to complete.
     */
    fun mobileFieldText(storedCode: String?, storedNumber: String?): String {
        val stored = storedNumber?.trim().orEmpty()
        if (stored.isEmpty()) return MOBILE_PREFIX
        val (code, national) = AuthorizedUserPhoneInput.serverReading(storedCode, stored)
        return when {
            national.startsWith("+") -> national
            code.isEmpty() || national.isEmpty() -> stored
            else -> "$code $national"
        }
    }

    fun isValidPhone(phone: String): Boolean =
        PHONE.matches(phone.replace(" ", ""))

    /** TRN must be exactly 9 digits when present. */
    fun isValidTrn(trn: String): Boolean = TRN.matches(trn)

    /** Passport = 6–9 alphanumerics; National ID / Drivers License = 5–20. */
    fun isValidIdentityNumber(number: String, type: String?): Boolean =
        if (type?.contains("passport", ignoreCase = true) == true) {
            ID_PASSPORT.matches(number)
        } else {
            ID_GENERAL.matches(number)
        }

    /** DOB rule — at least 15 years old. [dob] in the form's MM/dd/yyyy. */
    fun isAtLeast15(dob: String): Boolean {
        val parsed = runCatching {
            SimpleDateFormat("MM/dd/yyyy", Locale.US).apply { isLenient = false }.parse(dob)
        }.getOrNull() ?: return false
        val cutoff = Calendar.getInstance().apply { add(Calendar.YEAR, -15) }
        return !parsed.after(cutoff.time)
    }
}
