package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LegalContentUiState(
    val liveContent: String? = null,
    val expandedIds: Set<String>,
)

/**
 * FigmaTermsConditionsViewController: renders the RN fallback sections, then
 * swaps in GET /content/terms-conditions when it responds. Fetch failures are
 * silent (fallback stays).
 */
class TermsViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    // RN seeds the first section open (`new Set(['1'])`).
    private val _state = MutableStateFlow(LegalContentUiState(expandedIds = setOf("1")))
    val state: StateFlow<LegalContentUiState> = _state

    init {
        viewModelScope.launch {
            repository.termsContent().onSuccess { content ->
                _state.update { it.copy(liveContent = content) }
            }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(
            expandedIds = if (id in it.expandedIds) it.expandedIds - id else it.expandedIds + id,
        )
    }
}

/**
 * FigmaPrivacyPolicyViewController: same pattern against
 * GET /content/privacy-policy, "overview" seeded open.
 */
class PrivacyPolicyViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LegalContentUiState(expandedIds = setOf("overview")))
    val state: StateFlow<LegalContentUiState> = _state

    init {
        viewModelScope.launch {
            repository.privacyContent().onSuccess { content ->
                _state.update { it.copy(liveContent = content) }
            }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(
            expandedIds = if (id in it.expandedIds) it.expandedIds - id else it.expandedIds + id,
        )
    }
}
