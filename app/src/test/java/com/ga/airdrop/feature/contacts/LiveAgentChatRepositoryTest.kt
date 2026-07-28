package com.ga.airdrop.feature.contacts

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.AuthInterceptor
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.Order
import com.ga.airdrop.data.model.Package as AirdropPackage
import com.ga.airdrop.data.model.Payment
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAgentChatRepositoryTest {

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

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
    fun `session does not treat a generic customer id as a conversation id`() {
        val session = LiveAgentChatRepository.parseSessionResponse(
            """
            {
              "id": "customer-42",
              "assigned_agent_name": "Nirvana",
              "messages": []
            }
            """.trimIndent(),
            AirdropJson,
        )

        assertEquals("", session.conversationId)
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

    @Test
    fun `send result exposes canonical conversation uuid and inline reply`() {
        val result = LiveAgentChatRepository.parseSendResultResponse(
            """
            {
              "conversation_id": "1cbda3a6-ea09-4987-a4a7-dd326b077eb1",
              "message": {
                "id": "m3:reply",
                "body": "Confirmed — I can hear you.",
                "direction": "outbound",
                "sender_name": "Nirvana"
              },
              "reply": "Confirmed — I can hear you."
            }
            """.trimIndent(),
            AirdropJson,
        )

        assertEquals("1cbda3a6-ea09-4987-a4a7-dd326b077eb1", result.conversationId)
        assertEquals("Confirmed — I can hear you.", result.reply)
        assertEquals("Confirmed — I can hear you.", result.message?.body)
        assertFalse(requireNotNull(result.message).isCustomerAuthored)
    }

    @Test
    fun `identity mint uses Laravel bearer while direct session uses only identity headers`() =
        runBlocking {
            AuthTokenStore.save("sanctum-test")
            var contextLoads = 0
            val accountContext = LiveAgentChatAccountContext(
                packages = listOf(
                    AirdropPackage(
                        id = 12,
                        trackingCode = "DROP-12",
                        shippingMethod = "Express",
                        description = "Laptop stand",
                        statusName = "Ready for Pickup",
                        totalPrice = "USD 24.00",
                    ),
                ),
                orders = listOf(
                    Order(
                        id = 22,
                        orderNumber = "ORD-22",
                        productName = "Headphones",
                        orderStatus = "Processing",
                        paymentStatus = "Paid",
                    ),
                ),
                payments = listOf(
                    Payment(
                        id = 32,
                        method = "Card",
                        currency = "usd",
                        totalAmount = 24.0,
                        trackingCode = "DROP-12",
                        paymentDate = "2026-07-25",
                        packageId = 12,
                    ),
                ),
                airCoins = "425",
            )
            val identityServer = RecordingJsonInterceptor(
                """
                {
                  "data": {
                    "endpoint": "https://api.autopilotcrm.ai",
                    "publishable_key": "pub_test",
                    "user_id": "airdrop-user-42",
                    "identity_hash": "hash_42",
                    "user_profile": {"account_number": "GA-42"}
                  }
                }
                """.trimIndent(),
            )
            val directServer = RecordingJsonInterceptor(
                """
                {
                  "conversation_id": "airdrop-user-42",
                  "assigned_agent_name": "Nirvana",
                  "messages": []
                }
                """.trimIndent(),
                """
                {
                  "conversation_id": "1cbda3a6-ea09-4987-a4a7-dd326b077eb1"
                }
                """.trimIndent(),
            )
            val airdropClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor())
                .addInterceptor(identityServer)
                .build()
            val repository = LiveAgentChatRepository(
                airdropClient = airdropClient,
                directClient = OkHttpClient.Builder().addInterceptor(directServer).build(),
                apiBaseUrl = "https://pre-staging.example/api/v1/",
                now = { "2026-07-26T12:00:00Z" },
                accountContextSource = LiveAgentChatAccountContextSource {
                    contextLoads += 1
                    accountContext
                },
            )

            val session = repository.startSession(testUser())
            repository.sendMessage(
                conversationId = session.conversationId,
                body = "Where is DROP-12?",
                user = testUser(),
            )

            assertEquals("airdrop-user-42", session.conversationId)
            // Session start + every send refresh account context (no cold cache).
            assertEquals(2, contextLoads)
            val identityRequest = identityServer.requests.single()
            assertEquals(
                "https://pre-staging.example/api/v1/autopilot/identity",
                identityRequest.url.toString(),
            )
            assertEquals("POST", identityRequest.method)
            assertEquals("Bearer sanctum-test", identityRequest.header("Authorization"))

            val directRequest = directServer.requests.first()
            assertEquals(
                "https://api.autopilotcrm.ai/api/v1/app-channels/session",
                directRequest.url.toString(),
            )
            assertEquals("POST", directRequest.method)
            assertNull(directRequest.header("Authorization"))
            assertEquals("pub_test", directRequest.header("x-autopilot-publishable-key"))
            assertEquals("airdrop-user-42", directRequest.header("x-autopilot-user-id"))
            assertEquals("hash_42", directRequest.header("x-autopilot-identity-hash"))
            assertEquals("airdrop-android", directRequest.header("x-autopilot-client-app"))
            val body = directRequest.bodyUtf8()
            assertTrue(body.contains(""""preferred_agent":"nirvana""""))
            assertTrue(body.contains(""""accountNumber":"GA-42""""))
            assertTrue(body.contains("AirCoin balance"))
            assertTrue(body.contains("425"))
            assertTrue(body.contains("DROP-12"))
            assertTrue(body.contains("Ready for Pickup"))
            assertTrue(body.contains("ORD-22"))
            assertTrue(body.contains("Headphones"))
            assertTrue(body.contains("USD 24.00"))
            assertTrue(body.contains(""""airdrop_context_version":"3""""))
            assertTrue(body.contains("## Account summary"))
            assertTrue(body.contains("Packages in this context"))
            assertTrue(body.contains("Ready for pickup"))
            val messageBody = directServer.requests.last().bodyUtf8()
            assertTrue(messageBody.contains("DROP-12"))
            assertTrue(messageBody.contains(""""airdrop_context_version":"3""""))
            assertTrue(messageBody.contains("## Account summary"))
        }

    @Test
    fun `context builder falls back without inventing unavailable account records`() {
        val context = LiveAgentChatContextBuilder.build(
            user = testUser(),
            accountContext = LiveAgentChatAccountContext(),
            generatedAt = "2026-07-26T12:00:00Z",
        )

        assertTrue(context.contains("# AirDrop Customer Context"))
        assertTrue(context.contains("## Profile"))
        assertTrue(context.contains("## Account summary"))
        assertTrue(context.contains("Packages in this context**: 0"))
        assertTrue(context.contains("ask for the tracking number or order ID rather than guessing"))
        // Cold/empty package shortlists must NOT assert "no packages" as fact.
        assertFalse(context.contains("## Recent packages"))
        assertFalse(context.contains("No package data was available"))
        assertFalse(context.contains("## Recent orders"))
        assertFalse(context.contains("## Recent payments"))
    }

    @Test
    fun `direct chat rejects an insecure identity endpoint before dispatch`() = runBlocking {
        AuthTokenStore.save("sanctum-test")
        val identityServer = RecordingJsonInterceptor(
            """
            {
              "data": {
                "endpoint": "http://127.0.0.1:8766",
                "publishable_key": "pub_test",
                "user_id": "airdrop-user-42",
                "identity_hash": "hash_42",
                "user_profile": {}
              }
            }
            """.trimIndent(),
        )
        val directServer = RecordingJsonInterceptor()
        val repository = LiveAgentChatRepository(
            airdropClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor())
                .addInterceptor(identityServer)
                .build(),
            directClient = OkHttpClient.Builder().addInterceptor(directServer).build(),
            apiBaseUrl = "https://pre-staging.example/api/v1",
            accountContextSource = LiveAgentChatAccountContextSource {
                LiveAgentChatAccountContext()
            },
        )

        val error = runCatching { repository.startSession(testUser()) }.exceptionOrNull()

        assertTrue(error is LiveAgentChatException)
        assertEquals(LiveAgentChatErrorKind.InvalidUrl, (error as LiveAgentChatException).kind)
        assertTrue(directServer.requests.isEmpty())
    }

    private fun testUser() = AirdropUser(
        id = 42,
        accountNumber = "GA-42",
        firstName = "Chase",
        lastName = "Camp",
        email = "chase@example.com",
        phone = "8765550000",
    )

    private class RecordingJsonInterceptor(vararg bodies: String) : Interceptor {
        private val responses = ArrayDeque(bodies.toList())
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val body = checkNotNull(responses.pollFirst()) {
                "Unexpected request ${request.method} ${request.url}"
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun Request.bodyUtf8(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }
}
