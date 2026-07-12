package com.ga.airdrop.feature.more

import kotlinx.coroutines.runBlocking
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

    // ── NavyCave #21904: fail-closed backfill — zero PUT on failed/partial GET ─

    @Test
    fun `failed current-user GET means zero PUT - the backfill fails closed`() {
        val sparse = mapOf("user_id" to "42", "email" to "t@a.com", "pickup_location" to "Kingston")
        val failure = IllegalStateException("network down")
        val out = resolveProfileBackfill(sparse, Result.failure(failure))
        assertTrue(out.isFailure)
        assertTrue(out.exceptionOrNull() === failure)
    }

    @Test
    fun `fetched profile still missing required fields also fails closed`() {
        val sparse = mapOf("user_id" to "42", "email" to "t@a.com")
        val incompleteUser = MoreUser(id = 42, email = "t@a.com", firstName = null, lastName = null)
        val out = resolveProfileBackfill(sparse, Result.success(incompleteUser))
        assertTrue(out.isFailure)
        assertTrue(out.exceptionOrNull()!!.message!!.contains("missing required details"))
    }

    @Test
    fun `successful fetch completes the payload and succeeds`() {
        val sparse = mapOf("user_id" to "42", "email" to "t@a.com", "payment_currency" to "USD")
        val out = resolveProfileBackfill(sparse, Result.success(user))
        assertTrue(out.isSuccess)
        val filled = out.getOrThrow()
        assertEquals("Ay", filled["first_name"])
        assertEquals("Tamzid", filled["last_name"])
        assertEquals("USD", filled["payment_currency"])
    }

    @Test
    fun `full payload skips GET and sends one unchanged PUT`() = runBlocking {
        val full = mapOf(
            "user_id" to "42", "email" to "t@a.com",
            "first_name" to "Ay", "last_name" to "Tamzid",
        )
        var getCalls = 0
        val puts = mutableListOf<Map<String, String?>>()

        val result = updateProfileWithBackfill(
            fields = full,
            fetchCurrentUser = { getCalls++; Result.success(user) },
            putProfile = { puts += it; Result.success("saved") },
        )

        assertEquals("saved", result.getOrThrow())
        assertEquals(0, getCalls)
        assertEquals(listOf(full), puts)
    }

    @Test
    fun `sparse payload performs GET then one completed PUT`() = runBlocking {
        val events = mutableListOf<String>()
        val sparse = mapOf("user_id" to "42", "email" to "t@a.com", "pickup_location" to "Kingston")

        val result = updateProfileWithBackfill(
            fields = sparse,
            fetchCurrentUser = { events += "GET"; Result.success(user) },
            putProfile = { body ->
                events += "PUT"
                assertEquals("Ay", body["first_name"])
                assertEquals("Tamzid", body["last_name"])
                assertEquals("Kingston", body["pickup_location"])
                Result.success("saved")
            },
        )

        assertEquals("saved", result.getOrThrow())
        assertEquals(listOf("GET", "PUT"), events)
    }

    @Test
    fun `failed GET propagates original error and never PUTs`() = runBlocking {
        val failure = IllegalStateException("offline")
        var putCalls = 0

        val result = updateProfileWithBackfill(
            fields = mapOf("email" to "t@a.com"),
            fetchCurrentUser = { Result.failure(failure) },
            putProfile = { putCalls++; Result.success("impossible") },
        )

        assertTrue(result.exceptionOrNull() === failure)
        assertEquals(0, putCalls)
    }

    @Test
    fun `incomplete fetched profile never PUTs`() = runBlocking {
        var putCalls = 0
        val incomplete = MoreUser(id = 42, email = "t@a.com")

        val result = updateProfileWithBackfill(
            fields = mapOf("email" to "t@a.com"),
            fetchCurrentUser = { Result.success(incomplete) },
            putProfile = { putCalls++; Result.success("impossible") },
        )

        assertTrue(result.isFailure)
        assertEquals(0, putCalls)
    }
}
