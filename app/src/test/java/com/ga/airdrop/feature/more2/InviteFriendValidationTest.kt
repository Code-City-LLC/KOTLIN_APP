package com.ga.airdrop.feature.more2

import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.MutationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Invite a Friend posts to Laravel `ReferFriendRequest`, which enforces far more
 * than "not empty":
 *
 *   friend_first_name  min:2 max:60  regex ^[a-zA-Z\s\-']+$
 *   friend_last_name   min:2 max:70  regex ^[a-zA-Z\s\-']+$
 *   friend_email       max:100
 *   description        max:500
 *
 * Both invite paths checked only emptiness, so everything else came back as a
 * bare 422 with no field named. The regex is the one that actually bites,
 * because the contacts path feeds names straight out of the device address
 * book — "José" and "Renée" are ordinary contacts and the server rejects both.
 *
 * These tests pin the client to the server's rules so the customer gets a
 * fixable message instead of a button that stops working.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteFriendValidationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a one letter first name is rejected before it can 422`() = runTest(dispatcher) {
        val repo = RecordingRepo()
        val vm = InviteFriendViewModel(repo)
        advanceUntilIdle()

        vm.onFirstName("J")
        vm.onLastName("Brown")
        vm.onEmail("j@example.com")
        vm.save()
        advanceUntilIdle()

        assertEquals(
            "First name must be at least 2 characters.",
            vm.state.value.validationError,
        )
        assertEquals("no request may reach the server", 0, repo.calls)
    }

    @Test
    fun `an accented contact name is rejected with an actionable message, not a 422`() =
        runTest(dispatcher) {
            val repo = RecordingRepo()
            val vm = InviteFriendViewModel(repo)
            advanceUntilIdle()

            vm.sendEmailInvitation(
                InviteContact(
                    displayName = "José Martínez",
                    firstName = "José",
                    lastName = "Martinez",
                    email = "jose@example.com",
                    phone = "",
                ),
            )
            advanceUntilIdle()

            val message = vm.state.value.validationError
            assertNotNull("the customer must be told which name to fix", message)
            assertTrue(
                "message should name the offending field and say what is allowed: $message",
                message!!.contains("First name") && message.contains("letters"),
            )
            assertTrue("and tell them how to recover", message.contains("Edit the name below"))
            assertEquals(0, repo.calls)
            // Prefilled so the fix is one edit away rather than re-picking the contact.
            assertEquals("José", vm.state.value.firstName)
            assertEquals("jose@example.com", vm.state.value.email)
        }

    @Test
    fun `hyphens and apostrophes are allowed, because Laravel allows them`() = runTest(dispatcher) {
        val repo = RecordingRepo()
        val vm = InviteFriendViewModel(repo)
        advanceUntilIdle()

        vm.onFirstName("Anne-Marie")
        vm.onLastName("O'Brien")
        vm.onEmail("am@example.com")
        vm.save()
        advanceUntilIdle()

        assertNull("this is a legal name and must go through", vm.state.value.validationError)
        assertEquals(1, repo.calls)
    }

    @Test
    fun `an over-long description is rejected client-side`() = runTest(dispatcher) {
        val repo = RecordingRepo()
        val vm = InviteFriendViewModel(repo)
        advanceUntilIdle()

        vm.onFirstName("Ada")
        vm.onLastName("Lovelace")
        vm.onEmail("ada@example.com")
        vm.onDescription("x".repeat(501))
        vm.save()
        advanceUntilIdle()

        assertEquals("Message can be at most 500 characters.", vm.state.value.validationError)
        assertEquals(0, repo.calls)
    }

    private class RecordingRepo : InviteFriendRepository {
        var calls = 0
        override suspend fun currentUser() = Result.failure<AirdropUser>(IllegalStateException("unused"))
        override suspend fun referFriend(
            firstName: String,
            lastName: String,
            email: String,
            description: String?,
        ): Result<MutationResponse> {
            calls += 1
            return Result.success(MutationResponse(message = "sent"))
        }
    }
}
