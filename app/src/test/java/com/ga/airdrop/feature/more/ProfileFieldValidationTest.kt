package com.ga.airdrop.feature.more

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swift ProfileValidator parity — email format, TRN 9-digit, DOB min-age 15. */
class ProfileFieldValidationTest {

    @Test
    fun validEmailPassesInvalidEmailFails() {
        assertNull(ProfileFieldValidation.firstError(email = "user@example.com", trn = "", dob = ""))
        assertEquals(
            "Invalid email" to "Invalid email format",
            ProfileFieldValidation.firstError(email = "not-an-email", trn = "", dob = ""),
        )
    }

    @Test
    fun blankEmailIsNotAFormatError() {
        // Presence of the required email is enforced by the caller, not here.
        assertNull(ProfileFieldValidation.firstError(email = "", trn = "", dob = ""))
    }

    @Test
    fun trnMustBeExactly9Digits() {
        assertTrue(ProfileFieldValidation.isValidTrn("123456789"))
        assertFalse(ProfileFieldValidation.isValidTrn("12345678"))
        assertFalse(ProfileFieldValidation.isValidTrn("1234567890"))
        assertFalse(ProfileFieldValidation.isValidTrn("12345678a"))
        // Empty TRN is optional → no error.
        assertNull(ProfileFieldValidation.firstError(email = "user@example.com", trn = "", dob = ""))
        assertEquals(
            "Invalid TRN" to "TRN must be 9 digits",
            ProfileFieldValidation.firstError(email = "user@example.com", trn = "12345", dob = ""),
        )
    }

    @Test
    fun dobEnforcesMinimumAge15() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        assertTrue(ProfileFieldValidation.isAtLeast15("01/01/${year - 20}"))
        assertFalse(ProfileFieldValidation.isAtLeast15("01/01/${year - 5}"))
        // Unparseable DOB is not blocked here (server is authoritative).
        assertTrue(ProfileFieldValidation.isAtLeast15("not-a-date"))
        assertEquals(
            "Invalid date of birth" to "Must be at least 15 years old.",
            ProfileFieldValidation.firstError(
                email = "user@example.com",
                trn = "",
                dob = "01/01/${year - 5}",
            ),
        )
    }

    @Test
    fun emailErrorTakesPrecedenceOverTrnAndDob() {
        assertEquals(
            "Invalid email" to "Invalid email format",
            ProfileFieldValidation.firstError(email = "bad", trn = "12345", dob = "01/01/3000"),
        )
    }
}
