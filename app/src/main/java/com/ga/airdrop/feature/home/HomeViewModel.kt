package com.ga.airdrop.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.ProductsRepository
import com.ga.airdrop.data.repo.TierRepository
import com.ga.airdrop.data.repo.UserRepository
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.CustomerTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val greeting: String = "",
    val firstName: String = "",
    val tierName: String = "",
    val airCoins: String = "",
    // Tier API says who earns AirCoins (RUBY/Sapphire Saver do not) — the
    // header pill renders only when the backend says eligible.
    val aircoinsEligible: Boolean = true,
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
    suspend fun customerTier(): Result<CustomerTier>
    suspend fun airCoinsStatus(): Result<AirCoinsStatus>
    suspend fun auctionProductsShortlist(): Result<List<AuctionProduct>>
}

private class DefaultHomeRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val productsRepository: ProductsRepository = ProductsRepository(ApiClient.service),
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
    private val tierRepository: TierRepository = TierRepository(ApiClient.service),
) : HomeRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun customerTier(): Result<CustomerTier> = tierRepository.customerTier()

    override suspend fun airCoinsStatus(): Result<AirCoinsStatus> = miscRepository.airCoinsStatus()

    override suspend fun auctionProductsShortlist(): Result<List<AuctionProduct>> =
        productsRepository.auctionProductsShortlist()
}

class HomeViewModel(
    private val repository: HomeRepository = DefaultHomeRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(greeting = greetingForNow()))
    val state: StateFlow<HomeUiState> = _state
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    /**
     * @param isPull true when triggered by the Home pull-to-refresh gesture —
     * drives [HomeUiState.refreshing] instead of [HomeUiState.loading] so the
     * pull spinner shows without the cold-load state (Swift onPullToRefresh).
     */
    fun refresh(isPull: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { if (isPull) it.copy(refreshing = true) else it.copy(loading = true) }
            repository.currentUser().onSuccess { user ->
                _state.update {
                    it.copy(
                        firstName = user.firstName.orEmpty(),
                        tierName = user.customerTierName.orEmpty(),
                    )
                }
                SessionStore.update {
                    it.copy(
                        greeting = _state.value.greeting,
                        firstName = user.firstName.orEmpty(),
                        tierName = user.customerTierName.orEmpty(),
                    )
                }
            }
            // Tier API is the authority for the badge label and AirCoins
            // eligibility (RUBY/Sapphire Saver earn none). On failure the
            // legacy user-payload tier name above stays — never guess.
            repository.customerTier().onSuccess { tier ->
                _state.update {
                    it.copy(
                        tierName = tier.displayName.ifBlank { it.tierName },
                        aircoinsEligible = tier.aircoinsEligible,
                    )
                }
                SessionStore.update {
                    it.copy(
                        tierName = tier.displayName.ifBlank { it.tierName },
                        aircoinsEligible = tier.aircoinsEligible,
                    )
                }
            }
            repository.airCoinsStatus().onSuccess { status ->
                val label = (status.available ?: status.balance)?.toString().orEmpty()
                _state.update { it.copy(airCoins = label) }
                SessionStore.update { it.copy(airCoins = label) }
            }
            repository.auctionProductsShortlist()
                .onSuccess { products ->
                    _state.update { it.copy(auctionHighlights = products) }
                }
                .onFailure {
                    _state.update { it.copy(auctionHighlights = emptyList()) }
                }
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
}
