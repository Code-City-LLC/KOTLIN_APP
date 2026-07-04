package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GoldPriorityUiState(
    /** Index into [tierPages] resolved from the user's customer tier, or null until loaded. */
    val resolvedTierIndex: Int? = null,
)

/**
 * Resolves the user's `customer_tier.name` to a pager index — Swift
 * FigmaGoldPriorityViewController.loadUserTier(). Defaults to Gold Standard
 * when the API fails or returns no tier.
 */
class GoldPriorityViewModel(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(GoldPriorityUiState())
    val state: StateFlow<GoldPriorityUiState> = _state

    init {
        viewModelScope.launch {
            userRepository.currentUser().onSuccess { user ->
                val tierName = user.customerTierName?.trim()?.lowercase().orEmpty()
                if (tierName.isEmpty()) return@onSuccess
                val resolved = tierPages.indexOfFirst { tier ->
                    val lc = tier.name.lowercase()
                    lc == tierName ||
                        lc.startsWith(tierName) ||
                        tierName.startsWith(lc.substringBefore(" "))
                }
                if (resolved >= 0) {
                    _state.value = GoldPriorityUiState(resolvedTierIndex = resolved)
                }
            }
        }
    }
}
