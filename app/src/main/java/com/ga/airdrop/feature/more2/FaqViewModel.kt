package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.FaqItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The hardcoded FALLBACK_FAQS list was deleted on 2026-07-26.
//
// It was the INITIAL value of FaqUiState.faqs and the screen rendered it with
// no loading gate, so it painted on every open and a failed or slow /faqs was
// indistinguishable from a successful load — "fetch failures are silent, the
// fallback stays". That would be tolerable for marketing copy. It was not
// marketing copy: entry #5 answered "What is the mailing address?" with
//
//     Address Line 1: 1905 NW 51st Street 43A
//     City: Fort Lauderdale   State: Florida   Zip: 33309
//     Phone: 954-692-6659
//
// against the address the rest of the app and Laravel's own seeder give —
// 6175 NW 167th Street, Unit G36, Hialeah 33015, 1 (786) 558-9023. A customer
// following the FAQ shipped to the wrong state. Another entry still referred
// to "our Fort Lauderdale warehouse", and others quoted prices and hours no
// system had asserted in years.
//
// The FAQ is now the server's list or an honest empty/error state.

data class FaqUiState(
    val faqs: List<FaqItem> = emptyList(),
    val loading: Boolean = true,
    /** Non-null when the list could not be fetched. */
    val error: String? = null,
    // None expanded on cold open, mirroring RN.
    val expandedIds: Set<String> = emptySet(),
    /** Swift figma.faq.search: instant filter over question + answer. */
    val searchQuery: String = "",
)

/**
 * Swift FigmaFAQViewController.filteredList(for:): a blank query returns the
 * full list; otherwise keep entries whose question OR answer contains the
 * trimmed, lowercased query.
 */
fun filterFaqs(faqs: List<FaqItem>, query: String): List<FaqItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return faqs
    return faqs.filter {
        it.question.lowercase().contains(q) || it.answer.lowercase().contains(q)
    }
}

/** FigmaFAQViewController: fallback list + GET /faqs swap-in accordion. */
class FaqViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(FaqUiState())
    val state: StateFlow<FaqUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.faqs()
                .onSuccess { remote ->
                    val validIds = remote.map { it.id }.toSet()
                    _state.update {
                        it.copy(
                            faqs = remote,
                            loading = false,
                            error = null,
                            expandedIds = it.expandedIds intersect validIds,
                        )
                    }
                }
                .onFailure {
                    // No client-authored answers. An empty FAQ is honest; a
                    // stale mailing address sends a package to the wrong state.
                    _state.update {
                        it.copy(loading = false, error = "We couldn't load the FAQs.")
                    }
                }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(
            expandedIds = if (id in it.expandedIds) it.expandedIds - id else it.expandedIds + id,
        )
    }

    fun onSearchChange(query: String) = _state.update { it.copy(searchQuery = query) }
}
