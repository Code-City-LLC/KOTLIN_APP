package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.navigation.resolveAirdropRoute
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.repo.MiscRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val items: List<AirdropNotification> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    /** True once the first page has answered (success or failure). */
    val loadedOnce: Boolean = false,
)

/**
 * Live notifications inbox — closes the known Swift gap (the Swift VC is a
 * static empty-state): GET /user/notifications paginated, optimistic
 * POST /user/notifications/mark-read on tap, deep-link route resolution.
 */
class NotificationsViewModel(
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state

    private var page = 1
    private val perPage = 20

    init {
        refresh()
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(loading = true, error = null, endReached = false) }
        viewModelScope.launch {
            miscRepository.notifications(page, perPage)
                .onSuccess { batch ->
                    _state.update {
                        it.copy(
                            items = batch,
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
                            error = err.message ?: "Unable to load notifications",
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
            miscRepository.notifications(page + 1, perPage)
                .onSuccess { batch ->
                    page += 1
                    _state.update {
                        it.copy(
                            items = it.items + batch,
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

    /**
     * Optimistically flips the row to read and fires the mark-read mutation
     * (Swift comment: failure keeps the local flip until the next refresh).
     * Returns the resolved in-app route to navigate to, or null.
     */
    fun onNotificationTapped(notification: AirdropNotification): String? {
        if (!notification.isRead) {
            _state.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it.id == notification.id) it.copy(isRead = true) else it
                    }
                )
            }
            viewModelScope.launch {
                miscRepository.markNotificationRead(notification.id)
            }
        }
        return resolveAirdropRoute(notification.route, notification.referenceId)
    }
}
