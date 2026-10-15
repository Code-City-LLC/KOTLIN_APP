package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.data.model.Paginated
import kotlinx.coroutines.flow.MutableStateFlow
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.ReferredFriend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "You haven't referred any friends yet." was shown on every failure of
 * `GET /refer-friend/history` — offline, 401, 5xx, decode error — and stated as
 * fact, about referral credit the customer may be owed.
 *
 * The ViewModel explicitly wrote `referrals = emptyList()` on failure and parked
 * the message in `error`, a field `ReferredFriendsScreen` reads **zero** times.
 * The screen had two arms — loading, then `referrals.isEmpty()` — and no third.
 *
 * Swift closed the identical defect on 2026-08-01 with `lastFetchFailed`
 * (FigmaReferredFriendsViewController.swift:49,199,427,444). This is the port.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferredFriendsFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failed history read is distinguishable from having referred nobody`() =
        runTest(dispatcher) {
            val viewModel = ReferredFriendsViewModel(FailingRepo(), ReferralTestBoundary())
            advanceUntilIdle()

            assertTrue(
                "the screen cannot tell the truth unless the failure reaches it — " +
                    "`error` alone is never read",
                viewModel.state.value.loadFailed,
            )
            assertFalse(viewModel.state.value.loading)
        }

    @Test
    fun `a genuinely empty account is NOT reported as a failure`() = runTest(dispatcher) {
        val viewModel = ReferredFriendsViewModel(EmptyRepo(), ReferralTestBoundary())
        advanceUntilIdle()

        assertFalse(
            "an empty success is the one case where \"you haven't referred anyone\" is true",
            viewModel.state.value.loadFailed,
        )
        assertTrue(viewModel.state.value.referrals.isEmpty())
    }

    /**
     * The half that clearing `referrals` broke. A retry that fails must not blank
     * a list the customer is already reading.
     */
    @Test
    fun `a failed retry keeps the referrals already on screen`() = runTest(dispatcher) {
        val repo = FlakyRepo()
        val viewModel = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.referrals.size)

        repo.failNext = true
        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(
            "a failed refresh must not erase what is already displayed",
            1,
            viewModel.state.value.referrals.size,
        )
        assertTrue(viewModel.state.value.loadFailed)
    }

    @Test
    fun `a successful reload clears the failure flag`() = runTest(dispatcher) {
        val repo = FlakyRepo().apply { failNext = true }
        val viewModel = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadFailed)

        repo.failNext = false
        viewModel.loadFriends()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(1, viewModel.state.value.referrals.size)
    }

    private class FailingRepo : ReferAFriendRepository {
        override suspend fun currentUser() = Result.failure<AirdropUser>(UnsupportedOperationException())
        override suspend fun referredFriends(expectedSession: AuthTokenStore.RequestProvenance, page: Int, limit: Int) =
            Result.failure<Paginated<ReferredFriend>>(IllegalStateException("network"))
    }

    private class EmptyRepo : ReferAFriendRepository {
        override suspend fun currentUser() = Result.failure<AirdropUser>(UnsupportedOperationException())
        override suspend fun referredFriends(expectedSession: AuthTokenStore.RequestProvenance, page: Int, limit: Int) = Result.success(Paginated(emptyList<ReferredFriend>()))
    }

    private class FlakyRepo : ReferAFriendRepository {
        var failNext = false
        override suspend fun currentUser() = Result.failure<AirdropUser>(UnsupportedOperationException())
        override suspend fun referredFriends(expectedSession: AuthTokenStore.RequestProvenance, page: Int, limit: Int) =
            if (failNext) Result.failure(IllegalStateException("network"))
            else Result.success(Paginated(listOf(ReferredFriend(id = 1, friendName = "Ada Lovelace"))))
    }
}

internal class ReferralTestBoundary : AuthenticatedSessionBoundary {
    val owners = MutableStateFlow<AuthenticatedSessionOwner?>(AuthenticatedSessionOwner("referral-a", 1))
    override val changes = owners
    override fun capture() = owners.value
    override fun isCurrent(owner: AuthenticatedSessionOwner) = owner == owners.value
    override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
        if (!isCurrent(owner)) return false
        action()
        return true
    }
    override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean) =
        isCurrent(owner) && action()
    override fun requestOwner(owner: AuthenticatedSessionOwner) =
        if (isCurrent(owner)) AuthenticatedRequestOwner(owner, AuthTokenStore.RequestProvenance(1L, owner.sessionId)) else null
    override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int) = isCurrent(owner)
}
