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
    val loading: Boolean = true,
    /** Non-null when the document could not be fetched. */
    val error: String? = null,
)

/**
 * Terms & Conditions, from GET /content/terms-conditions.
 *
 * ⚠️ There used to be a hardcoded fallback here, rendered silently whenever
 * the fetch failed — and it was a verbatim copy of a DIFFERENT product's legal
 * text. It named "the etoy app" four times and described a platform "designed
 * to facilitate the exchange or giving away of toys to promote recycling...
 * and help declutter your home." AirDrop is a Jamaican freight forwarder.
 * Offline, on a 5xx, or with an empty CMS, the screen presented that as
 * AirDrop's terms, under AirDrop's header, indistinguishable from a successful
 * load.
 *
 * A legal document is the last thing a client should be authoring. There is no
 * fallback now: the screen shows the live document, or it says it could not
 * load it and offers to try again.
 */
class TermsViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    // RN seeds the first section open (`new Set(['1'])`).
    private val _state = MutableStateFlow(LegalContentUiState(expandedIds = setOf("1")))
    val state: StateFlow<LegalContentUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.termsContent()
                .onSuccess { content ->
                    val text = content.trim()
                    if (text.isEmpty()) {
                        _state.update {
                            it.copy(loading = false, error = "Terms & Conditions are unavailable right now.")
                        }
                    } else {
                        _state.update { it.copy(loading = false, liveContent = content, error = null) }
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(loading = false, error = "We couldn't load the Terms & Conditions.")
                    }
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
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.privacyContent()
                .onSuccess { content ->
                    _state.update { it.copy(loading = false, liveContent = content, error = null) }
                }
                .onFailure {
                    _state.update {
                        it.copy(loading = false, error = "We couldn't load the Privacy Policy.")
                    }
                }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(
            expandedIds = if (id in it.expandedIds) it.expandedIds - id else it.expandedIds + id,
        )
    }
}
