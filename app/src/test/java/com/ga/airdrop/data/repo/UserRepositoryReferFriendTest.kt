package com.ga.airdrop.data.repo

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
        val friends = repository.referredFriends(limit = 20).getOrThrow()
        val response = repository
            .referFriend(
                firstName = "Chase",
                lastName = "Campbell",
                email = "chase@example.com",
                description = "Good friend",
            )
            .getOrThrow()

        assertEquals("AD-2048", user.accountNumber)
        assertEquals(listOf("maya@example.com"), friends.map { it.friendEmail })
        assertEquals("Referral sent", response.message)

        assertEquals(20, capture.referredFriendsLimit)
        assertNull("Swift /refer-friend/history list is not user_id-scoped", capture.referredFriendsUserId)
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

    private class CapturedReferCalls {
        constructor(
            referFriendResponse: MutationResponse = MutationResponse(success = true, message = "Referral sent"),
        ) {
            this.referFriendResponse = referFriendResponse
        }

        var referredFriendsLimit: Int? = null
        var referredFriendsUserId: Int? = null
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
                    capture.referredFriendsLimit = args?.getOrNull(0) as? Int
                    capture.referredFriendsUserId = args?.getOrNull(1) as? Int
                    Paginated(
                        listOf(
                            ReferredFriend(
                                id = 7,
                                friendFirstName = "Maya",
                                friendLastName = "Lee",
                                friendEmail = "maya@example.com",
                                status = 1,
                            ),
                        ),
                    )
                }
                "referFriend" -> {
                    capture.referFriendRequest = args?.getOrNull(0) as? ReferFriendRequest
                    capture.referFriendResponse
                }
                else -> throw UnsupportedOperationException("Unexpected service call: ${method.name}")
            }
        } as AirdropApiService
}
