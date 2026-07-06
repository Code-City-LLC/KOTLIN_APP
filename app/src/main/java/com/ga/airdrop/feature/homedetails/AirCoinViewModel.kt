package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.repo.MiscRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── Balance (GET /aircoins/status) ────────────────────────────────────────

internal const val AIRCOIN_HISTORY_PER_PAGE = 50

data class AirCoinBalanceUiState(
    val status: AirCoinsStatus? = null,
    val loading: Boolean = false,
) {
    // Swift applyBalance fallbacks.
    val accumulated: Int get() = status?.accumulated ?: status?.balance ?: 0
    val redeemed: Int get() = status?.redeemed ?: 0
    val available: Int get() = status?.available ?: status?.balance ?: 0
}

class AirCoinBalanceViewModel(
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(AirCoinBalanceUiState())
    val state: StateFlow<AirCoinBalanceUiState> = _state

    init {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            miscRepository.airCoinsStatus().onSuccess { status ->
                _state.update { it.copy(status = status) }
            }
            _state.update { it.copy(loading = false) }
        }
    }
}

// ─── History ledger (GET /aircoins/history) ───────────────────────────────

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

    private val perPage = AIRCOIN_HISTORY_PER_PAGE

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null, endReached = true) }
        viewModelScope.launch {
            // Swift FigmaAirCoinHistoryViewController.loadHistory calls only
            // page 1 with limit 50. Do not auto-paginate this ledger unless
            // Swift gains the same behavior.
            miscRepository.airCoinHistory(page = 1, limit = perPage)
                .onSuccess { batch ->
                    _state.update {
                        it.copy(
                            transactions = batch,
                            loading = false,
                            loadedOnce = true,
                            endReached = true,
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
        // No-op by Swift precedence: current iOS history screen has no
        // load-more rail beyond page 1 / limit 50.
    }
}
