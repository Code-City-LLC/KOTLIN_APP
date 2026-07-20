package com.ga.airdrop.feature.auth

import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swift AirdropIdentityReminder parity (Kemar 2026-07-19, 2a3cf04+44a9c5f). */
class IdentityReminderTest {

    @Test
    fun accountKeyPrefersAccountNumberThenIdThenEmail() {
        assertEquals(
            "AD-2048",
            IdentityReminder.accountKey(
                AirdropUser(id = 7, accountNumber = "AD-2048", email = "u@x.com"),
            ),
        )
        assertEquals(
            "7",
            IdentityReminder.accountKey(AirdropUser(id = 7, email = "u@x.com")),
        )
        assertEquals(
            "u@x.com",
            IdentityReminder.accountKey(AirdropUser(email = "u@x.com")),
        )
        assertNull(IdentityReminder.accountKey(AirdropUser()))
    }

    @Test
    fun serverIdentityCompleteFlagWinsOverFieldInspection() {
        // Flag true → complete even with blank fields.
        assertTrue(
            IdentityReminder.isIdentityComplete(AirdropUser(identityComplete = true)),
        )
        // Flag false → incomplete even when both fields are present.
        assertFalse(
            IdentityReminder.isIdentityComplete(
                AirdropUser(
                    identityComplete = false,
                    trnNumber = "123456789",
                    identityNumber = "P-4242",
                ),
            ),
        )
    }

    @Test
    fun fallbackRequiresBothTrnAndIdentityNumber() {
        // 44a9c5f semantics: prompt (incomplete) when EITHER is missing.
        assertTrue(
            IdentityReminder.isIdentityComplete(
                AirdropUser(trnNumber = "123456789", identityNumber = "P-4242"),
            ),
        )
        assertFalse(
            IdentityReminder.isIdentityComplete(AirdropUser(trnNumber = "123456789")),
        )
        assertFalse(
            IdentityReminder.isIdentityComplete(AirdropUser(identityNumber = "P-4242")),
        )
        assertFalse(IdentityReminder.isIdentityComplete(AirdropUser()))
    }

    @Test
    fun airdropUserDecodesIdentityCompleteAndFields() {
        val user = AirdropJson.decodeFromString(
            AirdropUser.serializer(),
            """{"id":7,"identity_complete":true,"user_trn_number":"123456789","user_identity_number":"P-4242"}""",
        )
        assertEquals(true, user.identityComplete)
        assertEquals("123456789", user.trnNumber)
        assertEquals("P-4242", user.identityNumber)

        // Older payload without the flag → null (callers fall back to fields).
        val legacy = AirdropJson.decodeFromString(
            AirdropUser.serializer(),
            """{"id":7}""",
        )
        assertNull(legacy.identityComplete)
    }
}
