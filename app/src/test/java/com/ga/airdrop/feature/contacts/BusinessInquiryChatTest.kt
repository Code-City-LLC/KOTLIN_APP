package com.ga.airdrop.feature.contacts

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.model.AirdropUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessInquiryChatTest {
    @After fun clearSession() { AuthTokenStore.clear() }

    @Test fun inquiryMetadataUsesExistingChatTransportAndPreservesAccountContext() = runTest {
        AuthTokenStore.save("fixture-token", 1)
        val expected = AuthTokenStore.requestProvenance(AuthTokenStore.snapshot())!!
        val requests = mutableListOf<okhttp3.Request>()
        val bodies = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            bodies += Buffer().also { request.body?.writeTo(it) }.readUtf8()
            val payload = if (request.url.encodedPath.endsWith("identity"))
                """{"data":{"endpoint":"https://chat.example.test","publishable_key":"fixture-public","user_id":"user-1","identity_hash":"fixture-hash","user_profile":{"name":"Jordan","account_number":"ARD-1"}}}"""
            else """{"conversation_id":"canonical-chat","message":{"id":"m1","body":"Inquiry","direction":"customer"}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repository = LiveAgentChatRepository(airdropClient = client, directClient = client,
            apiBaseUrl = "https://airdrop.example.test/api/v1", expectedSession = expected,
            accountContextSource = { LiveAgentChatAccountContext() })
        val result = repository.sendInquiry("original-chat", "Business-rate inquiry", AirdropUser(id = 1),
            mapOf("request_type" to "business_rate_inquiry", "client_request_id" to "request-1",
                "laravel_message_id" to "123", "airdrop_context_version" to "untrusted-override"))
        assertEquals("canonical-chat", result.conversationId)
        assertEquals("/api/v1/autopilot/identity", requests[0].url.encodedPath)
        assertEquals(expected.sessionId, requests[0].header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
        assertEquals("/api/v1/app-channels/messages", requests[1].url.encodedPath)
        assertEquals("user-1", requests[1].header("x-autopilot-user-id"))
        val metadata = Json.parseToJsonElement(bodies[1]).jsonObject.getValue("metadata").jsonObject
        assertEquals("business_rate_inquiry", metadata.getValue("request_type").jsonPrimitive.content)
        assertEquals("request-1", metadata.getValue("client_request_id").jsonPrimitive.content)
        assertEquals("123", metadata.getValue("laravel_message_id").jsonPrimitive.content)
        assertEquals("3", metadata.getValue("airdrop_context_version").jsonPrimitive.content)
    }

    @Test fun replacedAccountCannotStartAnInquiryChat() = runTest {
        AuthTokenStore.save("fixture-a", 1)
        val expected = AuthTokenStore.requestProvenance(AuthTokenStore.snapshot())!!
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { calls++; error("Must not dispatch") }.build()
        val repository = LiveAgentChatRepository(airdropClient = client, directClient = client, expectedSession = expected)
        AuthTokenStore.save("fixture-b", 2)
        assertTrue(runCatching { repository.startSession(AirdropUser(id = 1)) }.isFailure)
        assertEquals(0, calls)
    }

    @Test fun suppliedConversationOpensThatThreadWithoutStartingAnother() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val reads = mutableListOf<String>()
            val source = object : LiveAgentChatDataSource {
                override suspend fun currentUser() = AirdropUser(id = 1)
                override suspend fun startSession(user: AirdropUser): AutoPilotAppChatSession = error("No new conversation")
                override suspend fun messages(conversationId: String): List<AutoPilotAppChatMessage> {
                    reads += conversationId
                    return listOf(AutoPilotAppChatMessage("m1", "We received your request", "agent", "Nirvana", "agent", null, null))
                }
                override suspend fun sendMessage(conversationId: String, body: String, user: AirdropUser): AutoPilotAppChatSendResult = error("No send")
            }
            val model = LiveAgentChatViewModel(repository = source, initialConversationId = "confirmed-chat")
            model.start(); advanceUntilIdle()
            assertEquals(listOf("confirmed-chat"), reads)
            assertEquals("confirmed-chat", model.state.value.conversationId)
            assertEquals("We received your request", model.state.value.messages.single().body)
        } finally { Dispatchers.resetMain() }
    }
}
