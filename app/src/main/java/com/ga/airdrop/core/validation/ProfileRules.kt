package com.ga.airdrop.core.validation

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

    /** Mobile must yield 10–15 digits after stripping non-digits. */
    fun isValidMobile(mobile: String): Boolean {
        val digits = mobile.count { it.isDigit() }
        return digits in 10..15
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
