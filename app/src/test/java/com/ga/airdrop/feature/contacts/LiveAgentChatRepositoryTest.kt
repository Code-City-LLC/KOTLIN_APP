package com.ga.airdrop.feature.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser coverage for the Nirvana app-channel wire shapes, captured from the
 * live backend 2026-07-20 (lane #154). The vendor ships several envelope
 * variants; these fixtures are the real payloads.
 */
class LiveAgentChatRepositoryTest {

    @Test
    fun `identity parses flat success payload`() {
        val body = """
            {"success":true,"endpoint":"https://api.autopilotcrm.ai",
             "publishable_key":"apk_pub_test","user_id":"airdrop-user-16427",
             "identity_hash":"deadbeef",
             "user_profile":{"name":"BOLD BADGER","email":"b@x.ai",
               "phone":"+1-876","account_number":"16427"}}
        """.trimIndent()
        val identity = LiveAgentChatRepository.parseIdentity(body)
        assertNotNull(identity)
        assertEquals("https://api.autopilotcrm.ai", identity!!.endpoint)
        assertEquals("apk_pub_test", identity.publishableKey)
        assertEquals("airdrop-user-16427", identity.userId)
        assertEquals("deadbeef", identity.identityHash)
        assertEquals("16427", identity.profileAccountNumber)
    }

    @Test
    fun `identity parses data-wrapped payload`() {
        val body = """
            {"success":true,"data":{"endpoint":"https://api.autopilotcrm.ai",
             "publishable_key":"apk_pub_test","user_id":"u1","identity_hash":"h"}}
        """.trimIndent()
        assertEquals("u1", LiveAgentChatRepository.parseIdentity(body)?.userId)
    }

    @Test
    fun `session accepts external id on first ever session`() {
        // Verified live: a brand-new user's first session returns the
        // EXTERNAL id here; the send response then carries the UUID.
        val body = """
            {"success":true,"channel_id":"c","organization_id":"o",
             "conversation_id":"airdrop-user-16428","contact_id":"ct","messages":[]}
        """.trimIndent()
        val session = LiveAgentChatRepository.parseSession(body)
        assertEquals("airdrop-user-16428", session?.conversationId)
        assertTrue(session?.messages?.isEmpty() == true)
    }

    @Test
    fun `send result adopts canonical uuid and inline reply`() {
        val body = """
            {"success":true,"accepted":true,"duplicate":false,
             "conversation_id":"1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
             "message":{"id":"m1:reply","body":"Confirmed — I can hear you.",
               "direction":"outbound","sender_name":"Nirvana",
               "created_at":"2026-07-20T16:01:39Z"},
             "reply":"Confirmed — I can hear you."}
        """.trimIndent()
        val result = LiveAgentChatRepository.parseSendResult(body)
        assertEquals("1cbda3a6-ea09-4987-a4a7-dd326b077eb1", result.conversationId)
        assertNotNull(result.replyMessage)
        assertEquals("Confirmed — I can hear you.", result.replyMessage!!.body)
        assertEquals(false, result.replyMessage.isCustomer)
    }

    @Test
    fun `send result falls back to bare reply string`() {
        val body = """{"success":true,"conversation_id":"u","reply":"hi there"}"""
        val result = LiveAgentChatRepository.parseSendResult(body)
        assertEquals("hi there", result.replyMessage?.body)
    }

    @Test
    fun `poll parses envelope messages with direction mapping`() {
        val body = """
            {"channel_id":"c","conversation_id":"u",
             "messages":[
               {"id":"a","body":"hello nirvana","direction":"inbound",
                 "created_at":"2026-07-20T16:01:34Z"},
               {"id":"b","body":"hi, I can hear you","direction":"outbound",
                 "sender_name":null,"created_at":"2026-07-20T16:01:39Z"}]}
        """.trimIndent()
        val messages = LiveAgentChatRepository.parseMessages(body)
        assertEquals(2, messages.size)
        assertTrue(messages[0].isCustomer)
        assertTrue(!messages[1].isCustomer)
        assertEquals("hi, I can hear you", messages[1].body)
    }

    @Test
    fun `poll tolerates bare array payload`() {
        val body = """[{"id":"a","body":"hey","direction":"outbound"}]"""
        val messages = LiveAgentChatRepository.parseMessages(body)
        assertEquals(1, messages.size)
    }

    @Test
    fun `malformed payloads never throw`() {
        assertNull(LiveAgentChatRepository.parseIdentity("not json"))
        assertNull(LiveAgentChatRepository.parseSession("{}"))
        assertTrue(LiveAgentChatRepository.parseMessages("not json").isEmpty())
        assertTrue(LiveAgentChatRepository.parseMessages("{}").isEmpty())
    }
}
