package com.ga.airdrop.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.ProductsRepository
import com.ga.airdrop.data.repo.UserRepository
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val greeting: String = "",
    val firstName: String = "",
    val tierName: String = "",
    val airCoins: String = "",
    val cartCount: Int = 0,
    val auctionHighlights: List<AuctionProduct> = emptyList(),
    val loading: Boolean = false,
    /**
     * User-initiated pull-to-refresh (Swift homeRefreshControl). Kept distinct
     * from the initial/resume [loading] so the pull spinner shows without the
     * screen dropping into its full cold-load state.
     */
    val refreshing: Boolean = false,
)

interface HomeRepository {
    suspend fun currentUser(): Result<AirdropUser>
    suspend fun airCoinsStatus(): Result<AirCoinsStatus>
    suspend fun auctionProductsShortlist(): Result<List<AuctionProduct>>
}

private class DefaultHomeRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val productsRepository: ProductsRepository = ProductsRepository(ApiClient.service),
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : HomeRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun airCoinsStatus(): Result<AirCoinsStatus> = miscRepository.airCoinsStatus()

    override suspend fun auctionProductsShortlist(): Result<List<AuctionProduct>> =
        productsRepository.auctionProductsShortlist()
}

class HomeViewModel(
    private val repository: HomeRepository = DefaultHomeRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(greeting = greetingForNow()))
    val state: StateFlow<HomeUiState> = _state
    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var refreshJob: Job? = null

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
                refreshJob = null
                sessionOwner = changed
                _state.value = HomeUiState(greeting = greetingForNow())
                if (changed != null) refresh()
            }
        }
        refresh()
    }

    /**
     * @param isPull true when triggered by the Home pull-to-refresh gesture —
     * drives [HomeUiState.refreshing] instead of [HomeUiState.loading] so the
     * pull spinner shows without the cold-load state (Swift onPullToRefresh).
     */
    fun refresh(isPull: Boolean = false) {
        if (refreshJob?.isActive == true) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        refreshJob = sessionJobs.launch {
            sessionBoundary.apply(owner) {
                _state.update { if (isPull) it.copy(refreshing = true) else it.copy(loading = true) }
            }
            repository.currentUser().onSuccess { user ->
                val userId = user.id
                if (userId != null && !sessionBoundary.bindAccountId(owner, userId)) return@onSuccess
                sessionBoundary.apply(owner) {
                    _state.update {
                        it.copy(
                            firstName = user.firstName.orEmpty(),
                            tierName = user.customerTierName.orEmpty(),
                        )
                    }
                    SessionStore.updateForSession(owner) {
                        it.copy(
                            greeting = _state.value.greeting,
                            firstName = user.firstName.orEmpty(),
                            tierName = user.customerTierName.orEmpty(),
                        )
                    }
                }
            }
            if (!sessionBoundary.isCurrent(owner)) return@launch
            repository.airCoinsStatus().onSuccess { status ->
                val label = (status.available ?: status.balance)?.toString().orEmpty()
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(airCoins = label) }
                    SessionStore.updateForSession(owner) { it.copy(airCoins = label) }
                }
            }
            if (!sessionBoundary.isCurrent(owner)) return@launch
            repository.auctionProductsShortlist()
                .onSuccess { products ->
                    sessionBoundary.apply(owner) {
                        _state.update { it.copy(auctionHighlights = products) }
                    }
                }
                .onFailure {
                    sessionBoundary.apply(owner) {
                        _state.update { it.copy(auctionHighlights = emptyList()) }
                    }
                }
            sessionBoundary.apply(owner) {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
}
