package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Pagination
import com.ga.airdrop.data.model.ReferredFriend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReferredFriendsPagingTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun pagesUseTwentyAndKeepFirstAcceptedDuplicate() = runTest(dispatcher) {
        val repo = PendingRepo()
        val vm = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        runCurrent()
        assertEquals(1 to 20, repo.calls.single().let { it.page to it.limit })
        repo.calls[0].done.complete(page(1, 2, friend(1, "original"), friend(2)))
        runCurrent()
        vm.loadMore(); vm.loadMore()
        runCurrent()
        assertEquals(2, repo.calls.size)
        repo.calls[1].done.complete(page(2, 2, friend(1, "changed"), friend(3), friend(3)))
        runCurrent()
        assertEquals(listOf(1, 2, 3), vm.state.value.referrals.map { it.id })
        assertEquals("original", vm.state.value.referrals.first().friendName)
        assertFalse(vm.state.value.hasMore)
        vm.loadMore(); runCurrent()
        assertEquals(2, repo.calls.size)
    }

    @Test fun loadMoreFailureRetainsRowsAndRequiresExplicitRetry() = runTest(dispatcher) {
        val repo = PendingRepo()
        val vm = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        runCurrent(); repo.calls[0].done.complete(page(1, 2, friend(1))); runCurrent()
        vm.loadMore(); runCurrent()
        repo.calls[1].done.complete(Result.failure(IllegalStateException("offline"))); runCurrent()
        assertEquals(listOf(1), vm.state.value.referrals.map { it.id })
        assertTrue(vm.state.value.loadMoreError)
        assertFalse(vm.state.value.loadingMore)
        vm.loadMore(); runCurrent(); assertEquals(2, repo.calls.size)
        vm.retryLoadMore(); runCurrent(); assertEquals(2, repo.calls[2].page)
        repo.calls[2].done.complete(page(2, 2, friend(2))); runCurrent()
        assertEquals(listOf(1, 2), vm.state.value.referrals.map { it.id })
        assertFalse(vm.state.value.loadMoreError)
    }

    @Test fun refreshPreemptsLoadMoreEvenIfOldCallIgnoresCancellation() = runTest(dispatcher) {
        val repo = PendingRepo()
        val vm = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        runCurrent(); repo.calls[0].done.complete(page(1, 3, friend(1))); runCurrent()
        vm.loadMore(); runCurrent()
        vm.loadFriends(); runCurrent()
        assertEquals(listOf(1), vm.state.value.referrals.map { it.id })
        assertTrue(vm.state.value.loading)
        repo.calls[2].done.complete(page(1, 1, friend(8))); runCurrent()
        repo.calls[1].done.complete(page(2, 3, friend(2))); runCurrent()
        assertEquals(listOf(8), vm.state.value.referrals.map { it.id })
        assertFalse(vm.state.value.hasMore)
    }

    @Test fun rapidRefreshCannotPublishTheOlderFailure() = runTest(dispatcher) {
        val repo = PendingRepo()
        val vm = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        runCurrent(); vm.loadFriends(); runCurrent()
        repo.calls[1].done.complete(page(1, 1, friend(9))); runCurrent()
        repo.calls[0].done.complete(Result.failure(IllegalStateException("old"))); runCurrent()
        assertFalse(vm.state.value.loadFailed)
        assertEquals(listOf(9), vm.state.value.referrals.map { it.id })
    }

    @Test fun accountReplacementClearsRowsAndRejectsOldAccountCompletion() = runTest(dispatcher) {
        val repo = PendingRepo()
        val boundary = ReferralTestBoundary()
        val vm = ReferredFriendsViewModel(repo, boundary)
        runCurrent()
        boundary.owners.value = AuthenticatedSessionOwner("referral-b", 2)
        runCurrent()
        assertTrue(vm.state.value.referrals.isEmpty())
        assertEquals("referral-b", repo.calls[1].owner.sessionId)
        repo.calls[1].done.complete(page(1, 1, friend(20))); runCurrent()
        repo.calls[0].done.complete(page(1, 1, friend(10))); runCurrent()
        assertEquals(listOf(20), vm.state.value.referrals.map { it.id })
        boundary.owners.value = null; runCurrent()
        assertTrue(vm.state.value.referrals.isEmpty())
        vm.loadFriends(); runCurrent(); assertEquals(2, repo.calls.size)
    }

    @Test fun malformedPageIsAtomicAndRetryUsesTheSamePage() = runTest(dispatcher) {
        val repo = PendingRepo()
        val vm = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        runCurrent(); repo.calls[0].done.complete(page(1, 2, friend(1))); runCurrent()
        vm.loadMore(); runCurrent()
        repo.calls[1].done.complete(page(2, 2, friend(2), friend(0))); runCurrent()
        assertEquals(listOf(1), vm.state.value.referrals.map { it.id })
        vm.retryLoadMore(); runCurrent(); assertEquals(2, repo.calls[2].page)
        repo.calls[2].done.complete(page(2, 2, friend(2))); runCurrent()
        assertEquals(listOf(1, 2), vm.state.value.referrals.map { it.id })
    }

    @Test fun duplicateOnlyPageWithoutMetadataStopsFallbackPaging() = runTest(dispatcher) {
        val repo = PendingRepo()
        val vm = ReferredFriendsViewModel(repo, ReferralTestBoundary())
        val rows = (1..20).map { friend(it) }
        runCurrent(); repo.calls[0].done.complete(Result.success(Paginated(rows))); runCurrent()
        assertTrue(vm.state.value.hasMore)
        vm.loadMore(); runCurrent()
        repo.calls[1].done.complete(Result.success(Paginated(rows))); runCurrent()
        assertFalse(vm.state.value.hasMore)
        assertEquals(20, vm.state.value.referrals.size)
    }

    private fun friend(id: Int, name: String = "Friend $id") = ReferredFriend(id=id, friendName=name)
    private fun page(number: Int, last: Int, vararg rows: ReferredFriend) = Result.success(
        Paginated(rows.toList(), Pagination(currentPage=number, perPage=20, lastPage=last)),
    )
    private class PendingRepo : ReferAFriendRepository {
        data class Call(val owner: AuthTokenStore.RequestProvenance, val page: Int, val limit: Int,
                        val done: CompletableDeferred<Result<Paginated<ReferredFriend>>> = CompletableDeferred())
        val calls = mutableListOf<Call>()
        override suspend fun currentUser() = Result.success(AirdropUser(id=1))
        override suspend fun referredFriends(expectedSession: AuthTokenStore.RequestProvenance, page: Int, limit: Int): Result<Paginated<ReferredFriend>> {
            val call = Call(expectedSession, page, limit).also(calls::add)
            return withContext(NonCancellable) { call.done.await() }
        }
    }
}
