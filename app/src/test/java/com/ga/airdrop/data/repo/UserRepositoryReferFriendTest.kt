package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.CurrentUserResponse
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.ReferFriendRequest
import com.ga.airdrop.data.model.ReferredFriend
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.http.GET
import com.ga.airdrop.data.api.AirdropApiFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue

class UserRepositoryReferFriendTest {

    @Test
    fun userRepositoryOwnsSwiftReferFriendContracts() = runBlocking {
        val capture = CapturedReferCalls()
        val repository = UserRepository(referService(capture))
        val expectedRequest = ReferFriendRequest(
            friendFirstName = "Chase",
            friendLastName = "Campbell",
            friendEmail = "chase@example.com",
            description = "Good friend",
        )

        val user = repository.currentUser().getOrThrow()
        val friends = repository.referredFriends(AuthTokenStore.RequestProvenance(7L, "referral-session"), page = 2, limit = 20).getOrThrow()
        val response = repository
            .referFriend(
                firstName = "Chase",
                lastName = "Campbell",
                email = "chase@example.com",
                description = "Good friend",
            )
            .getOrThrow()

        assertEquals("AD-2048", user.accountNumber)
        assertEquals(listOf("maya@example.com"), friends.items.map { it.friendEmail })
        assertEquals("Referral sent", response.message)

        assertEquals(20, capture.referredFriendsLimit)
        assertEquals(2, capture.referredFriendsPage)
        assertEquals("7", capture.referredFriendsRevision)
        assertEquals("referral-session", capture.referredFriendsSession)
        assertEquals(expectedRequest, capture.referFriendRequest)
        assertEquals(
            """{"friend_first_name":"Chase","friend_last_name":"Campbell","friend_email":"chase@example.com","description":"Good friend"}""",
            AirdropJson.encodeToString(ReferFriendRequest.serializer(), expectedRequest),
        )
    }

    @Test
    fun referFriendFalseSuccessIsFailureLikeSwift() = runBlocking {
        val capture = CapturedReferCalls(referFriendResponse = MutationResponse(success = false, message = "Referral failed"))
        val repository = UserRepository(referService(capture))

        val response = repository.referFriend(
            firstName = "Chase",
            lastName = "Campbell",
            email = "chase@example.com",
            description = null,
        )

        assertEquals(true, response.isFailure)
        assertEquals("Referral failed", response.exceptionOrNull()?.message)
    }

    @Test
    fun referredFriendsUsesSwiftHistoryEndpoint() {
        val annotation = AirdropApiService::class.java
            .methods
            .first { it.name == "referredFriends" }
            .getAnnotation(GET::class.java)

        assertEquals("refer-friend/history", annotation?.value)
    }

    @Test fun malformedHistoryCannotBecomeAValidEmptyAccount() = runBlocking {
        for (body in listOf(
            """{"data":[{"id":0}]}""", """{"data":[{}]}""",
            """{"data":[{"id":"not-an-id"}]}""", """{"data":{"wrong":"shape"}}""",
            """{"data":[{"id":1},{"id":2,"friend_name":{}}]}""",
        )) {
            val service = Proxy.newProxyInstance(AirdropApiService::class.java.classLoader,
                arrayOf(AirdropApiService::class.java)) { _, _, _ ->
                AirdropJson.parseToJsonElement(body) as JsonObject
            } as AirdropApiService
            assertTrue(body, UserRepository(service).referredFriends(AuthTokenStore.RequestProvenance(1L, "test")).isFailure)
        }
    }

    @Test fun realRetrofitRequestUsesPageTwentyAndDecodesTheReferralWireShape() = runBlocking {
        var requested: okhttp3.Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requested = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":[{"id":21}],"pagination":{"current_page":2,"per_page":20,"total_records":41,"total_pages":3}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = UserRepository(AirdropApiFactory.create("https://referral-test.invalid/api/v1/", client))
        val result = repo.referredFriends(AuthTokenStore.RequestProvenance(8L,"owned-session"),2,20).getOrThrow()
        assertEquals("/api/v1/refer-friend/history", requested?.url?.encodedPath)
        assertEquals("2", requested?.url?.queryParameter("page"))
        assertEquals("20", requested?.url?.queryParameter("limit"))
        assertNull(requested?.url?.queryParameter("user_id"))
        assertEquals("8", requested?.header(AuthTokenStore.REQUEST_REVISION_HEADER))
        assertEquals("owned-session", requested?.header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
        assertEquals(3, result.pagination?.lastPage)
        assertEquals(41, result.pagination?.total)
    }

    @Test fun serverValidationErrorCannotBecomeAnEmptyHistory() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                .body("""{"error":"validation_error","message":"Limit cannot exceed 100 records per page"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = UserRepository(AirdropApiFactory.create("https://referral-test.invalid/api/v1/",client))
        assertTrue(repo.referredFriends(AuthTokenStore.RequestProvenance(1L,"test")).isFailure)
    }

    private class CapturedReferCalls {
        constructor(
            referFriendResponse: MutationResponse = MutationResponse(success = true, message = "Referral sent"),
        ) {
            this.referFriendResponse = referFriendResponse
        }

        var referredFriendsLimit: Int? = null
        var referredFriendsPage: Int? = null
        var referredFriendsRevision: String? = null
        var referredFriendsSession: String? = null
        var referFriendRequest: ReferFriendRequest? = null
        val referFriendResponse: MutationResponse
    }

    @Suppress("UNCHECKED_CAST")
    private fun referService(capture: CapturedReferCalls): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "currentUser" -> CurrentUserResponse(AirdropUser(accountNumber = "AD-2048"))
                "referredFriends" -> {
                    capture.referredFriendsRevision = args?.getOrNull(0) as? String
                    capture.referredFriendsSession = args?.getOrNull(1) as? String
                    capture.referredFriendsPage = args?.getOrNull(2) as? Int
                    capture.referredFriendsLimit = args?.getOrNull(3) as? Int
                    AirdropJson.parseToJsonElement("""{"data":[{"id":7,"friend_first_name":"Maya","friend_last_name":"Lee","friend_email":"maya@example.com","status":1}]}""") as JsonObject
                }
                "referFriend" -> {
                    capture.referFriendRequest = args?.getOrNull(0) as? ReferFriendRequest
                    capture.referFriendResponse
                }
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService
}
