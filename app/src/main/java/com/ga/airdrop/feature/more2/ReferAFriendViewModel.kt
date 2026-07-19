package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.Paginated
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
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
)

interface ReferAFriendRepository {
    suspend fun currentUser(): Result<AirdropUser>
    suspend fun referredFriends(limit: Int = 20): Result<List<ReferredFriend>>
    suspend fun referredFriendsPage(page: Int, limit: Int = 20): Result<Paginated<ReferredFriend>>
}

private class DefaultReferAFriendRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : ReferAFriendRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun referredFriends(limit: Int): Result<List<ReferredFriend>> =
        userRepository.referredFriends(limit = limit)

    override suspend fun referredFriendsPage(page: Int, limit: Int): Result<Paginated<ReferredFriend>> =
        userRepository.referredFriendsPage(page = page, limit = limit)
}

/** FigmaReferAFriendViewController: static carousel landing + Invite CTA. */
class ReferAFriendViewModel(
    @Suppress("unused") private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferAFriendUiState())
    val state: StateFlow<ReferAFriendUiState> = _state
}

/**
 * FigmaReferredFriendsViewController: GET /refer-friend/history history list.
 * Swift paginates the "View History" list at pageSize 20 with pull-to-refresh
 * and load-more on scroll (AirdropAPI.referredFriendsPage(page:limit:)).
 */
class ReferredFriendsViewModel(
    private val repository: ReferAFriendRepository = DefaultReferAFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferredFriendsUiState())
    val state: StateFlow<ReferredFriendsUiState> = _state

    private var page = 0
    private val perPage = REFERRED_FRIENDS_PER_PAGE

    init {
        refresh()
    }

    /** Swift onRefresh — reset to page 1. */
    fun refresh() {
        if (_state.value.loadingMore) return
        _state.update { it.copy(loading = true, error = null, endReached = false) }
        viewModelScope.launch {
            repository.referredFriendsPage(page = 1, limit = perPage)
                .onSuccess { result ->
                    page = result.pagination?.currentPage ?: 1
                    _state.update {
                        it.copy(
                            loading = false,
                            loadedOnce = true,
                            referrals = result.items,
                            error = null,
                            endReached = computeEndReached(result),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadedOnce = true,
                            referrals = emptyList(),
                            error = error.message,
                        )
                    }
                }
        }
    }

    /** Swift loadNextPage — append the next page when scrolled near the bottom. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.referredFriendsPage(page = page + 1, limit = perPage)
                .onSuccess { result ->
                    page = result.pagination?.currentPage ?: (page + 1)
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            referrals = it.referrals + result.items,
                            endReached = computeEndReached(result),
                        )
                    }
                }
                .onFailure {
                    // Swift stops paging on a failed load-more.
                    _state.update { it.copy(loadingMore = false, endReached = true) }
                }
        }
    }

    /** Swift: currentPage < lastPage, else fall back to friends.count >= pageSize. */
    private fun computeEndReached(result: Paginated<ReferredFriend>): Boolean {
        val lastPage = result.pagination?.lastPage
        val currentPage = result.pagination?.currentPage ?: page
        return if (lastPage != null) currentPage >= lastPage else result.items.size < perPage
    }

    companion object {
        const val REFERRED_FRIENDS_PER_PAGE = 20
    }
}
