package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
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
)

interface ReferAFriendRepository {
    suspend fun currentUser(): Result<AirdropUser>
    suspend fun referredFriends(limit: Int = 20): Result<List<ReferredFriend>>
}

private class DefaultReferAFriendRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : ReferAFriendRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun referredFriends(limit: Int): Result<List<ReferredFriend>> =
        userRepository.referredFriends(limit = limit)
}

/** FigmaReferAFriendViewController: static carousel landing + Invite CTA. */
class ReferAFriendViewModel(
    @Suppress("unused") private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferAFriendUiState())
    val state: StateFlow<ReferAFriendUiState> = _state
}

/** FigmaReferredFriendsViewController: GET /refer-friend/history?limit=200 history list. */
class ReferredFriendsViewModel(
    private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferredFriendsUiState())
    val state: StateFlow<ReferredFriendsUiState> = _state

    init {
        loadFriends()
    }

    fun loadFriends() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.referredFriends(limit = 200)
                .onSuccess { friends ->
                    _state.update {
                        it.copy(loading = false, referrals = friends, error = null, loadFailed = false)
                    }
                }
                .onFailure { error ->
                    // Do NOT wipe `referrals`. On a retry that would blank a list
                    // the customer is already reading, and on first load there is
                    // nothing to clear anyway — so clearing only ever destroys.
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message,
                            loadFailed = true,
                        )
                    }
                }
        }
    }
}
