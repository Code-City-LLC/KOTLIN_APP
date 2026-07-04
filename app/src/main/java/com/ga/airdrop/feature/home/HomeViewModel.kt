package com.ga.airdrop.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.ProductsRepository
import com.ga.airdrop.data.repo.UserRepository
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
    val cartCount: Int = 0,
    val auctionHighlights: List<AuctionProduct> = emptyList(),
    val loading: Boolean = false,
)

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val productsRepository: ProductsRepository = ProductsRepository(ApiClient.service),
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(greeting = greetingForNow()))
    val state: StateFlow<HomeUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            userRepository.currentUser().onSuccess { user ->
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
            miscRepository.airCoinsStatus().onSuccess { status ->
                val label = (status.available ?: status.balance)?.toString().orEmpty()
                _state.update { it.copy(airCoins = label) }
                SessionStore.update { it.copy(airCoins = label) }
            }
            productsRepository.auctionProductsShortlist().onSuccess { products ->
                _state.update { it.copy(auctionHighlights = products) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
}
