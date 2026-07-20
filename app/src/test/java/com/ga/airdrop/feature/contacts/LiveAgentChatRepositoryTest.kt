package com.ga.airdrop.feature.contacts

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveAgentChatRepositoryTest {

    @Test
    fun `identity unwraps Laravel data envelope`() {
        val identity = LiveAgentChatRepository.parseIdentityResponse(
            """
            {
              "data": {
                "endpoint": "https://api.autopilotcrm.ai",
                "publishable_key": "pub_test",
                "user_id": "user_42",
                "identity_hash": "hash_42",
                "user_profile": {
                  "name": "Chase Camp",
                  "email": "chase@example.com",
                  "phone": "8765550000",
                  "account_number": "GA-42"
                }
              }
            }
            """.trimIndent(),
            AirdropJson,
        )

        assertEquals("https://api.autopilotcrm.ai", identity.endpoint)
        assertEquals("pub_test", identity.publishableKey)
        assertEquals("user_42", identity.userId)
        assertEquals("hash_42", identity.identityHash)
        assertEquals("GA-42", identity.userProfile.accountNumber)
    }

    @Test
    fun `session accepts Swift-compatible conversation envelope`() {
        val session = LiveAgentChatRepository.parseSessionResponse(
            """
            {
              "session": {
                "conversation_id": "conv_123",
                "assigned_agent_name": "Nirvana",
                "messages": [
                  {"id":"m1","body":"Hello","direction":"agent","sender_name":"Nirvana"}
                ]
              }
            }
            """.trimIndent(),
            AirdropJson,
        )

        assertEquals("conv_123", session.conversationId)
        assertEquals("Nirvana", session.assignedAgentName)
        assertEquals("Hello", session.messages.single().body)
        assertFalse(session.messages.single().isCustomerAuthored)
    }

    @Test
    fun `messages accepts nested data messages envelope`() {
        val messages = LiveAgentChatRepository.parseMessagesResponse(
            """
            {
              "data": {
                "messages": [
                  {"id":"m2","content":"Where is my package?","direction":"customer","sender_type":"customer"}
                ]
              }
            }
            """.trimIndent(),
            AirdropJson,
        )

        assertEquals(1, messages.size)
        assertEquals("Where is my package?", messages.single().body)
        assertEquals(true, messages.single().isCustomerAuthored)
    }
}
