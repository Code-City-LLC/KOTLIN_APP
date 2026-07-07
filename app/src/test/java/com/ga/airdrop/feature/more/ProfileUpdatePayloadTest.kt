package com.ga.airdrop.feature.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Preferences-Save regression fix (e06b323 dropped first/last name
 * from the sparse payload; Laravel UpdateUserRequest marks them required).
 * Mirrors Swift AirdropAPI.completedProfileUpdateRequest: a sparse payload is
 * completed with the cached profile's required fields before PUT /user/profile.
 */
class ProfileUpdatePayloadTest {

    private val user = MoreUser(id = 42, firstName = "Ay", lastName = "Tamzid", email = "t@a.com")

    @Test
    fun `sparse Preferences payload is flagged as missing required fields`() {
        val prefs = mapOf(
            "user_id" to "42",
            "email" to "t@a.com",
            "pickup_location" to "Kingston",
            "payment_currency" to "USD",
        )
        assertTrue(profileRequiredFieldsMissing(prefs)) // no first/last name
    }

    @Test
    fun `a payload with all required fields is not flagged`() {
        val full = mapOf(
            "user_id" to "42", "email" to "t@a.com",
            "first_name" to "Ay", "last_name" to "Tamzid",
        )
        assertFalse(profileRequiredFieldsMissing(full))
        // blank counts as missing
        assertTrue(profileRequiredFieldsMissing(full + ("first_name" to " ")))
    }

    @Test
    fun `complete back-fills only the missing required fields from cache`() {
        val prefs = mapOf(
            "user_id" to "42",
            "email" to "t@a.com",
            "pickup_location" to "Kingston",
            "payment_currency" to "USD",
        )
        val out = completeProfileFields(prefs, user)
        assertEquals("Ay", out["first_name"])
        assertEquals("Tamzid", out["last_name"])
        // present fields are preserved, extras untouched
        assertEquals("t@a.com", out["email"])
        assertEquals("Kingston", out["pickup_location"])
        assertEquals("USD", out["payment_currency"])
    }

    @Test
    fun `present values win over cache`() {
        val edited = mapOf("user_id" to "42", "email" to "new@a.com", "first_name" to "Edited")
        val out = completeProfileFields(edited, user)
        assertEquals("new@a.com", out["email"])   // not overwritten by cache
        assertEquals("Edited", out["first_name"]) // not overwritten
        assertEquals("Tamzid", out["last_name"])  // filled from cache
    }

    @Test
    fun `null cache leaves the payload unchanged`() {
        val prefs = mapOf("email" to "t@a.com")
        assertEquals(prefs, completeProfileFields(prefs, null))
    }
}
