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
    val referralLink: String = "https://airdropja.com/refer",
    val referrals: List<ReferredFriend> = emptyList(),
    val loadingReferrals: Boolean = true,
    val error: String? = null,
)

data class ReferredFriendsUiState(
    val referrals: List<ReferredFriend> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
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

/** Swift-local FigmaReferAFriendViewController: referral link + inline referrals. */
class ReferAFriendViewModel(
    private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferAFriendUiState())
    val state: StateFlow<ReferAFriendUiState> = _state

    init {
        loadReferralData()
        loadFriends()
    }

    private fun loadReferralData() {
        viewModelScope.launch {
            repository.currentUser()
                .onSuccess { user ->
                    val account = user.accountNumber.orEmpty()
                    if (account.isNotBlank()) {
                        _state.update { it.copy(referralLink = "https://airdropja.com/refer/$account") }
                    }
                }
        }
    }

    fun loadFriends() {
        _state.update { it.copy(loadingReferrals = true, error = null) }
        viewModelScope.launch {
            repository.referredFriends(limit = 200)
                .onSuccess { friends ->
                    _state.update {
                        it.copy(
                            loadingReferrals = false,
                            referrals = friends,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loadingReferrals = false,
                            referrals = emptyList(),
                            error = error.message,
                        )
                    }
                }
        }
    }
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
                    _state.update { it.copy(loading = false, referrals = friends, error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            referrals = emptyList(),
                            error = error.message,
                        )
                    }
                }
        }
    }
}
