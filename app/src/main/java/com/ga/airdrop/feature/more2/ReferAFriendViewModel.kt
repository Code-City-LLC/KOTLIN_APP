package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedRequest
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.model.Paginated
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReferAFriendUiState(
    val rendered: Boolean = true,
)

data class ReferredFriendsUiState(
    val referrals: List<ReferredFriend> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,

    /**
     * "The fetch failed", as distinct from "you have referred nobody".
     *
     * Without this the screen has only two arms — loading, then
     * `referrals.isEmpty()` — so every failure of GET /refer-friend/history
     * landed on "You haven't referred any friends yet." and stated it as fact,
     * about referral credit the customer may be owed. `error` was written but
     * `ReferredFriendsScreen` never read it.
     *
     * Swift closed this same defect on 2026-08-01 with `lastFetchFailed`
     * (FigmaReferredFriendsViewController.swift:49,199,427,444) and its own
     * comment says why: *"Without this, a network/API error rendered the same
     * 'You haven't referred any friends yet.' copy as a real empty account,
     * silently lying to the user."* This is that fix, and the replacement copy
     * below is theirs verbatim so the platforms say the same thing.
     */
    val loadFailed: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadMoreError: Boolean = false,
)

interface ReferAFriendRepository {
    suspend fun currentUser(): Result<AirdropUser>
    suspend fun referredFriends(
        expectedSession: AuthTokenStore.RequestProvenance,
        page: Int = 1,
        limit: Int = 20,
    ): Result<Paginated<ReferredFriend>>
}

private class DefaultReferAFriendRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : ReferAFriendRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun referredFriends(
        expectedSession: AuthTokenStore.RequestProvenance,
        page: Int,
        limit: Int,
    ): Result<Paginated<ReferredFriend>> =
        userRepository.referredFriends(expectedSession, page, limit)
}

/** FigmaReferAFriendViewController: static carousel landing + Invite CTA. */
class ReferAFriendViewModel(
    @Suppress("unused") private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferAFriendUiState())
    val state: StateFlow<ReferAFriendUiState> = _state
}

/** Existing referral history, paged in Swift's 20-row batches. */
class ReferredFriendsViewModel(
    private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {
    private val _state = MutableStateFlow(ReferredFriendsUiState())
    val state: StateFlow<ReferredFriendsUiState> = _state
    private var sessionOwner = sessionBoundary.capture()
    private var generation = 0L
    private var loadJob: Job? = null
    private var currentPage = 0

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                generation++
                loadJob?.cancel()
                sessionOwner = changed
                currentPage = 0
                _state.value = ReferredFriendsUiState(loading = false)
                if (changed != null) loadFriends()
            }
        }
        loadFriends()
    }

    fun loadFriends() = load(reset = true)
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore || s.loadMoreError || s.loadFailed) return
        load(reset = false)
    }
    fun retryLoadMore() {
        val s = _state.value
        if (!s.loadMoreError || s.loading || s.loadingMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: run {
            if (sessionBoundary.capture() == null) _state.value = ReferredFriendsUiState(loading = false)
            return
        }
        if (reset) {
            generation++
            loadJob?.cancel()
        }
        val requestGeneration = generation
        val requestedPage = if (reset) 1 else currentPage + 1
        _state.update { it.copy(loading = reset, loadingMore = !reset, error = null, loadMoreError = false) }
        loadJob = viewModelScope.launch {
            val result = try {
                repository.referredFriends(requestOwner.provenance, requestedPage, 20).mapCatching { page ->
                    require(page.items.all { it.id > 0 }) { "Unable to read referral history" }
                    require(page.pagination?.currentPage?.let { it == requestedPage } != false) {
                        "Unable to read referral history page"
                    }
                    page
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (requestGeneration != generation) return@launch
            sessionBoundary.apply(requestOwner.session) {
                result.onSuccess { page ->
                    val existing = if (reset) emptyList() else _state.value.referrals
                    val merged = (existing + page.items).distinctBy { it.id }
                    val gained = merged.size > existing.size
                    currentPage = requestedPage
                    _state.update {
                        it.copy(
                            referrals = merged, loading = false, loadingMore = false,
                            error = null, loadFailed = false, loadMoreError = false,
                            hasMore = page.pagination?.lastPage?.let { last -> requestedPage < last }
                                ?: (page.items.size >= 20 && gained),
                        )
                    }
                }.onFailure {
                    _state.update { state ->
                        state.copy(
                            loading = false, loadingMore = false,
                            error = if (reset) "Couldn't refresh your referrals." else state.error,
                            loadFailed = if (reset) true else state.loadFailed,
                            loadMoreError = !reset,
                        )
                    }
                }
            }
        }
    }
}
