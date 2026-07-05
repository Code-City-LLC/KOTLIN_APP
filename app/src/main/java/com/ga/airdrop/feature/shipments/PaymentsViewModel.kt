package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Payments type filter — Swift action sheet: All / Package / Product. */
enum class PaymentTypeFilter(val label: String, val queryValue: String?) {
    All("All", null),
    Package("Package", "package"),
    Product("Product", "product"),
}

data class PaymentsUiState(
    val items: List<ShipmentPayment> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val searchText: String = "",
    // Swift/RN default the list to package payments, not All.
    val typeFilter: PaymentTypeFilter = PaymentTypeFilter.Package,
    val showTypeFilter: Boolean = false,
    val downloadingInvoiceId: Int? = null,
    val error: String? = null,
)

/** One-shot navigation event: open the invoice viewer with a resolved URL. */
data class InvoiceOpenEvent(val url: String, val title: String)

/**
 * Payments list — FigmaPaymentsViewController: GET /payments paginated
 * (perPage 15), 300ms debounce search (min 3 chars), All/Package/Product
 * filter, HTML-entity-decoded descriptions, and the per-row invoice download
 * (Swift gap; RN downloadPaymentInvoice → GET /payments/{id}/invoice).
 */
class PaymentsViewModel(
    private val repo: ShipmentsPaymentsRepository = ShipmentsRepoProvider.payments,
) : ViewModel() {

    companion object {
        const val PER_PAGE = 15
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_MIN_CHARS = 3
    }

    private val _state = MutableStateFlow(PaymentsUiState())
    val state: StateFlow<PaymentsUiState> = _state

    private val _invoiceEvents = MutableStateFlow<InvoiceOpenEvent?>(null)
    val invoiceEvents: StateFlow<InvoiceOpenEvent?> = _invoiceEvents

    private var currentPage = 1
    private var loadJob: kotlinx.coroutines.Job? = null
    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    fun onSearchTextChange(text: String) {
        _state.update { it.copy(searchText = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(reset = true)
        }
    }

    fun showTypeFilter(show: Boolean) = _state.update { it.copy(showTypeFilter = show) }

    fun selectTypeFilter(filter: PaymentTypeFilter) {
        _state.update { it.copy(typeFilter = filter, showTypeFilter = false) }
        load(reset = true)
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMorePages) return
        load(reset = false)
    }

    fun refresh() = load(reset = true)

    /** Swift gap — resolve GET /payments/{id}/invoice then open the viewer. */
    fun downloadInvoice(payment: ShipmentPayment) {
        if (_state.value.downloadingInvoiceId != null) return
        viewModelScope.launch {
            _state.update { it.copy(downloadingInvoiceId = payment.id) }
            repo.paymentInvoiceUrl(payment.id)
                .onSuccess { url ->
                    _invoiceEvents.value = InvoiceOpenEvent(
                        url = url,
                        title = payment.invoiceId ?: "Invoice",
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
            _state.update { it.copy(downloadingInvoiceId = null) }
        }
    }

    fun consumeInvoiceEvent() {
        _invoiceEvents.value = null
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun load(reset: Boolean) {
        if (reset) { currentPage = 1; loadJob?.cancel() }
        val requestedPage = currentPage
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = reset, loadingMore = !reset) }
            val s = _state.value
            val search = s.searchText.trim().takeIf { it.length >= SEARCH_MIN_CHARS }
            repo.payments(
                page = requestedPage,
                perPage = PER_PAGE,
                type = s.typeFilter.queryValue,
                search = search,
            ).onSuccess { batch ->
                _state.update { current ->
                    val merged = if (reset) batch else {
                        val known = current.items.map { it.id }.toHashSet()
                        current.items + batch.filter { it.id !in known }
                    }
                    current.copy(
                        items = merged,
                        loading = false,
                        loadingMore = false,
                        hasMorePages = batch.size >= PER_PAGE,
                    )
                }
                currentPage = requestedPage + 1
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = e.message)
                }
            }
        }
    }
}
