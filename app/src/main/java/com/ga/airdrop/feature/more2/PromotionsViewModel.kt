package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.PromotionalBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromotionsUiState(
    val banners: List<PromotionalBanner> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

/** FigmaPromotionsViewController: GET /promotional-banners (active only). */
class PromotionsViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(PromotionsUiState())
    val state: StateFlow<PromotionsUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.promotionalBanners(activeOnly = true)
                .onSuccess { banners ->
                    _state.update {
                        it.copy(banners = banners, loading = false, hasLoaded = true)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, hasLoaded = true, error = e.toUserMessage())
                    }
                }
        }
    }
}
