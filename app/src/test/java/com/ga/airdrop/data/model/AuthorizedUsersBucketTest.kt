package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard: when the API returns separated active/inactive arrays, the
 * bucket is the authority on active-ness. Entries that omit status/is_active
 * must inherit the bucket's status — otherwise status is null, isActive is
 * false, and the detail CTA wrongly offers "Activate" for an active user while
 * the row shows "-" (Swift parity: force section status defaults).
 */
class AuthorizedUsersBucketTest {

    private fun decode(json: String): AuthorizedUsers =
        AirdropJson.decodeFromString(AuthorizedUsersEnvelope.serializer(), json).users

    @Test
    fun `active-bucket user missing status inherits Active`() {
        val users = decode(
            """{"active":[{"id":1,"user_first_name":"A"}],"inactive":[{"id":2,"user_first_name":"B"}]}""",
        )
        assertEquals(1, users.active.size)
        assertTrue("active-bucket entry must be active", users.active[0].isActive)
        assertEquals("Active", users.active[0].status)
        assertFalse("inactive-bucket entry must be inactive", users.inactive[0].isActive)
        assertEquals("Inactive", users.inactive[0].status)
    }

    @Test
    fun `data-wrapped separated buckets also stamp status`() {
        val users = decode(
            """{"data":{"active":[{"id":9,"user_first_name":"C"}],"inactive":[]}}""",
        )
        assertTrue(users.active[0].isActive)
        assertEquals("Active", users.active[0].status)
    }

    @Test
    fun `explicit status in a bucket is preserved, not overwritten`() {
        // A server that (incorrectly) lists an "Inactive" user under active
        // keeps its explicit status — we only fill blanks.
        val users = decode(
            """{"active":[{"id":3,"user_first_name":"D","status":"Inactive"}],"inactive":[]}""",
        )
        assertEquals("Inactive", users.active[0].status)
    }

    @Test
    fun `flat array still split by parsed status`() {
        val users = decode(
            """[{"id":1,"user_first_name":"A","is_active":true},{"id":2,"user_first_name":"B","is_active":false}]""",
        )
        assertEquals(1, users.active.size)
        assertEquals(1, users.inactive.size)
    }
}
