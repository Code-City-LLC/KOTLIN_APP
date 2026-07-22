package com.ga.airdrop.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.repo.ActiveDelivery
import com.ga.airdrop.data.repo.DeliveryTrackingGateway
import com.ga.airdrop.data.repo.DeliveryTrackingRepository
import com.ga.airdrop.data.repo.TrackedDelivery
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeliveryCenterContent {
    Loading,
    Error,
    Empty,
    List,
    Detail,
    NoDelivery,
}

data class DeliveryCenterUiState(
    val activeDeliveries: List<ActiveDelivery> = emptyList(),
    val selectedPackageId: Int? = null,
    val delivery: TrackedDelivery? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
    internal val retryPackageId: Int? = null,
) {
    val content: DeliveryCenterContent
        get() = when {
            loading -> DeliveryCenterContent.Loading
            error != null -> DeliveryCenterContent.Error
            delivery != null -> DeliveryCenterContent.Detail
            selectedPackageId != null -> DeliveryCenterContent.NoDelivery
            activeDeliveries.size > 1 -> DeliveryCenterContent.List
            loadedOnce -> DeliveryCenterContent.Empty
            else -> DeliveryCenterContent.Loading
        }

    val selectedSummary: ActiveDelivery?
        get() = activeDeliveries.firstOrNull { it.packageId == selectedPackageId }

    val canReturnToList: Boolean
        get() = activeDeliveries.size > 1 && selectedPackageId != null
}

/**
 * Canonical 0/1/many Delivery Center state machine.
 *
 * 0 -> honest empty state; 1 -> detail; 2+ -> list then package-id detail.
 * Every stage displayed by the screen is the ordered server projection.
 */
class DeliveryCenterViewModel(
    private val initialPackageId: Int? = null,
    private val gateway: DeliveryTrackingGateway = DeliveryTrackingRepository(ApiClient.service),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryCenterUiState())
    val state: StateFlow<DeliveryCenterUiState> = _state

    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var loadJob: Job? = null
    private val loadEpoch = AtomicInteger(0)

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionJobs.replaceSession()
                loadJob = null
                loadEpoch.incrementAndGet()
                sessionOwner = changed
                _state.value = DeliveryCenterUiState()
                if (changed != null) loadInitial()
            }
        }
        loadInitial()
    }

    fun refresh() {
        val selected = _state.value.selectedPackageId
        if (selected != null) {
            loadDetail(selected, preserveList = _state.value.activeDeliveries, isRefresh = true)
        } else {
            loadActiveDeliveries(isRefresh = _state.value.loadedOnce)
        }
    }

    fun retry() {
        val packageId = _state.value.retryPackageId
        if (packageId != null) {
            loadDetail(packageId, preserveList = _state.value.activeDeliveries, isRefresh = false)
        } else {
            loadActiveDeliveries(isRefresh = false)
        }
    }

    fun selectDelivery(packageId: Int) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        if (current.activeDeliveries.none { it.packageId == packageId }) return
        loadDetail(packageId, preserveList = current.activeDeliveries, isRefresh = false)
    }

    /** Returns true when Back was consumed by detail -> multi-delivery list. */
    fun returnToList(): Boolean {
        val current = _state.value
        if (!current.canReturnToList) return false
        loadJob?.cancel()
        loadEpoch.incrementAndGet()
        _state.update {
            it.copy(
                selectedPackageId = null,
                delivery = null,
                loading = false,
                refreshing = false,
                error = null,
                retryPackageId = null,
            )
        }
        return true
    }

    private fun loadInitial() {
        val directPackageId = initialPackageId?.takeIf { it > 0 }
        if (directPackageId != null) {
            loadDetail(directPackageId, preserveList = emptyList(), isRefresh = false)
        } else {
            loadActiveDeliveries(isRefresh = false)
        }
    }

    private fun loadActiveDeliveries(isRefresh: Boolean) {
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob?.cancel()
        val epoch = loadEpoch.incrementAndGet()
        sessionBoundary.apply(owner) {
            _state.update {
                it.copy(
                    selectedPackageId = null,
                    delivery = null,
                    loading = !isRefresh,
                    refreshing = isRefresh,
                    error = null,
                    retryPackageId = null,
                )
            }
        }
        loadJob = sessionJobs.launch {
            loadAllActiveDeliveries(owner).fold(
                onSuccess = { deliveries ->
                    if (!isCurrent(owner, epoch)) return@fold
                    when (deliveries.size) {
                        0 -> publish(
                            owner,
                            epoch,
                            DeliveryCenterUiState(loadedOnce = true, loading = false),
                        )
                        1 -> fetchAndPublishDetail(
                            owner = owner,
                            epoch = epoch,
                            packageId = deliveries.single().packageId,
                            activeDeliveries = deliveries,
                        )
                        else -> publish(
                            owner,
                            epoch,
                            DeliveryCenterUiState(
                                activeDeliveries = deliveries,
                                loadedOnce = true,
                                loading = false,
                            ),
                        )
                    }
                },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    publishError(owner, epoch, failure, retryPackageId = null)
                },
            )
        }
    }

    private fun loadDetail(
        packageId: Int,
        preserveList: List<ActiveDelivery>,
        isRefresh: Boolean,
    ) {
        if (packageId <= 0) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob?.cancel()
        val epoch = loadEpoch.incrementAndGet()
        sessionBoundary.apply(owner) {
            _state.update {
                it.copy(
                    activeDeliveries = preserveList,
                    selectedPackageId = packageId,
                    loading = !isRefresh,
                    refreshing = isRefresh,
                    error = null,
                    retryPackageId = null,
                )
            }
        }
        loadJob = sessionJobs.launch {
            fetchAndPublishDetail(owner, epoch, packageId, preserveList)
        }
    }

    private suspend fun fetchAndPublishDetail(
        owner: AuthenticatedSessionOwner,
        epoch: Int,
        packageId: Int,
        activeDeliveries: List<ActiveDelivery>,
    ) {
        gateway.deliveryTracking(packageId).fold(
            onSuccess = { result ->
                publish(
                    owner,
                    epoch,
                    DeliveryCenterUiState(
                        activeDeliveries = activeDeliveries,
                        selectedPackageId = packageId,
                        delivery = result.delivery,
                        loading = false,
                        loadedOnce = true,
                    ),
                )
            },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                publishError(
                    owner = owner,
                    epoch = epoch,
                    failure = failure,
                    retryPackageId = packageId,
                    activeDeliveries = activeDeliveries,
                )
            },
        )
    }

    private suspend fun loadAllActiveDeliveries(
        owner: AuthenticatedSessionOwner,
    ): Result<List<ActiveDelivery>> = runCatching {
        val all = mutableListOf<ActiveDelivery>()
        val seenPackageIds = mutableSetOf<Int>()
        var page = 1
        while (true) {
            if (!sessionBoundary.isCurrent(owner)) throw CancellationException("Session replaced")
            val response = gateway.activeDeliveries(page = page, perPage = ACTIVE_PAGE_SIZE)
                .getOrThrow()
            response.deliveries.forEach { delivery ->
                if (!seenPackageIds.add(delivery.packageId)) {
                    error("Delivery information is unavailable. Please try again.")
                }
                all += delivery
            }
            if (!response.hasNextPage) break
            page += 1
            if (page > MAX_ACTIVE_PAGES) {
                error("Delivery information is unavailable. Please try again.")
            }
        }
        all
    }

    private fun publish(
        owner: AuthenticatedSessionOwner,
        epoch: Int,
        value: DeliveryCenterUiState,
    ) {
        if (!isCurrent(owner, epoch)) return
        sessionBoundary.apply(owner) {
            if (epoch == loadEpoch.get()) _state.value = value
        }
    }

    private fun publishError(
        owner: AuthenticatedSessionOwner,
        epoch: Int,
        failure: Throwable,
        retryPackageId: Int?,
        activeDeliveries: List<ActiveDelivery> = _state.value.activeDeliveries,
    ) {
        if (!isCurrent(owner, epoch)) return
        sessionBoundary.apply(owner) {
            if (epoch != loadEpoch.get()) return@apply
            _state.update {
                it.copy(
                    activeDeliveries = activeDeliveries,
                    selectedPackageId = retryPackageId,
                    delivery = null,
                    loading = false,
                    refreshing = false,
                    loadedOnce = true,
                    error = failure.message?.takeIf(String::isNotBlank)
                        ?: "Delivery information is unavailable. Please try again.",
                    retryPackageId = retryPackageId,
                )
            }
        }
    }

    private fun isCurrent(owner: AuthenticatedSessionOwner, epoch: Int): Boolean =
        epoch == loadEpoch.get() && sessionBoundary.isCurrent(owner)

    companion object {
        private const val ACTIVE_PAGE_SIZE = 50
        private const val MAX_ACTIVE_PAGES = 100

        // TEMP-PREVIEW (Kemar interactive demo): when set, the factory uses this
        // gateway instead of the live repository. Remove before shipping.
        @Volatile
        var debugGateway: DeliveryTrackingGateway? = null

        fun factory(initialPackageId: Int?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val gateway = debugGateway
                    return if (gateway != null) {
                        DeliveryCenterViewModel(
                            initialPackageId = initialPackageId,
                            gateway = gateway,
                        ) as T
                    } else {
                        DeliveryCenterViewModel(initialPackageId = initialPackageId) as T
                    }
                }
            }
    }
}
