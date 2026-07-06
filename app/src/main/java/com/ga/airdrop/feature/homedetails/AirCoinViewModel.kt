package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── Balance (GET /aircoins/status) ────────────────────────────────────────

internal const val AIRCOIN_HISTORY_PER_PAGE = 50

data class AirCoinBalanceUiState(
    val status: AirCoinsStatus? = null,
    val loading: Boolean = false,
    val accountNumber: String? = null,
    val userId: Int? = null,
) {
    // Swift applyBalance fallbacks.
    val accumulated: Int get() = status?.accumulated ?: status?.balance ?: 0
    val redeemed: Int get() = status?.redeemed ?: 0
    val available: Int get() = status?.available ?: status?.balance ?: 0
    val redeemAccount: String?
        get() = accountNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: userId?.takeIf { it > 0 }?.toString()
}

class AirCoinBalanceViewModel(
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(
        SessionStore.identity.value.let { identity ->
            AirCoinBalanceUiState(
                accountNumber = identity.accountNumber,
                userId = identity.userId,
            )
        }
    )
    val state: StateFlow<AirCoinBalanceUiState> = _state

    init {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            miscRepository.airCoinsStatus().onSuccess { status ->
                _state.update { it.copy(status = status) }
            }
            userRepository.currentUser().onSuccess { user ->
                SessionStore.updateIdentity(
                    accountNumber = user.accountNumber,
                    userId = user.id,
                )
                val identity = SessionStore.identity.value
                _state.update {
                    it.copy(
                        accountNumber = identity.accountNumber,
                        userId = identity.userId,
                    )
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }
}

// ─── History ledger (GET /aircoins/history, paginated) ────────────────────

data class AirCoinHistoryUiState(
    val transactions: List<AirCoinTransaction> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val loadedOnce: Boolean = false,
)

class AirCoinHistoryViewModel(
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(AirCoinHistoryUiState())
    val state: StateFlow<AirCoinHistoryUiState> = _state

    private var page = 1
    private val perPage = AIRCOIN_HISTORY_PER_PAGE

    init {
        refresh()
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(loading = true, error = null, endReached = false) }
        viewModelScope.launch {
            miscRepository.airCoinHistory(page, perPage)
                .onSuccess { batch ->
                    _state.update {
                        it.copy(
                            transactions = batch,
                            loading = false,
                            loadedOnce = true,
                            endReached = batch.size < perPage,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadedOnce = true,
                            error = err.message ?: "Unable to load history",
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            miscRepository.airCoinHistory(page + 1, perPage)
                .onSuccess { batch ->
                    page += 1
                    _state.update {
                        it.copy(
                            transactions = it.transactions + batch,
                            loadingMore = false,
                            endReached = batch.size < perPage,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }
}
