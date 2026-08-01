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
    /**
     * Set when the balance read failed.
     *
     * ⚠️ Without this the screen could not tell "we don't know" from "you have
     * nothing": [status] stayed null, every getter below falls back to `?: 0.0`,
     * and a customer holding 462.25 AirCoin was shown a confident
     * Accumulated 0 / Redeemed 0 / Available 0 — a false statement about their
     * money, rendered identically to a real zero, with no error and no retry.
     */
    val error: String? = null,
) {
    /** True only when we actually have the server's numbers. */
    val hasBalance: Boolean get() = status != null

    // Doubles: Laravel emits fractional balances (462.25) and the old Int
    // model truncated them, so a customer silently lost the fraction of a coin.
    val accumulated: Double get() = status?.accumulated ?: status?.balance ?: 0.0
    val redeemed: Double get() = status?.redeemed ?: 0.0
    val available: Double get() = status?.available ?: status?.balance ?: 0.0
}

class AirCoinBalanceViewModel(
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(AirCoinBalanceUiState())
    val state: StateFlow<AirCoinBalanceUiState> = _state

    init {
        load()
    }

    /** Also the retry entry point — the screen previously had no way to reload. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            miscRepository.airCoinsStatus()
                .onSuccess { status ->
                    _state.update { it.copy(status = status, error = null, loading = false) }
                }
                .onFailure {
                    // Never fall through to the 0.0 getters on failure — say we
                    // could not load it and offer a retry instead of inventing
                    // a balance.
                    _state.update {
                        it.copy(
                            error = "We couldn't load your AirCoin balance.",
                            loading = false,
                        )
                    }
                }
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
