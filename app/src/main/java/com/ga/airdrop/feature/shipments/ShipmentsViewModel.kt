package com.ga.airdrop.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AirCoinsStatus
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.UserRepository
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Swift default before /exchange-rate resolves. */
const val DEFAULT_USD_TO_JMD = 160.625

data class ShipmentsUiState(
    val loading: Boolean = false,
    val exchangeRate: Double = DEFAULT_USD_TO_JMD,
    val summary: ShipmentsSummary = ShipmentsSummary(),
    val packages: List<ShipmentPackage> = emptyList(),
    val payments: List<ShipmentPayment> = emptyList(),
    val orders: List<ShipmentOrder> = emptyList(),
    val error: String? = null,
)

interface ShipmentsHeaderRepository {
    suspend fun currentUser(): Result<AirdropUser>
    suspend fun airCoinsStatus(): Result<AirCoinsStatus>
}

private class DefaultShipmentsHeaderRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ShipmentsHeaderRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun airCoinsStatus(): Result<AirCoinsStatus> = miscRepository.airCoinsStatus()
}

/**
 * Shipments hub — FigmaShipmentsViewController: summary tiles, packages
 * shortlist (10), payments shortlist (4), orders shortlist (6).
 */
class ShipmentsViewModel(
    private val repo: ShipmentsHubRepository = ShipmentsRepoProvider.hub,
    private val headerRepo: ShipmentsHeaderRepository = DefaultShipmentsHeaderRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ShipmentsUiState())
    val state: StateFlow<ShipmentsUiState> = _state
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.exchangeRate().onSuccess { rate ->
                _state.update { it.copy(exchangeRate = rate) }
            }
            repo.summary().onSuccess { summary ->
                _state.update { it.copy(summary = summary) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
            repo.packagesShortlist().onSuccess { packages ->
                _state.update { it.copy(packages = packages.take(10)) }
            }
            repo.paymentsShortlist().onSuccess { payments ->
                _state.update { it.copy(payments = payments.take(4)) }
            }
            repo.ordersShortlist().onSuccess { orders ->
                _state.update { it.copy(orders = orders.take(6)) }
            }
            refreshSharedHeader()
            _state.update { it.copy(loading = false) }
        }
    }

    fun toggleCart(pkg: ShipmentPackage) {
        // Swift FigmaShipmentsViewController.onTapAddPackageToCart — the ONE
        // shared cart; membership is observed via CartStore.items so the UI
        // recomposes without a fake state write.
        com.ga.airdrop.feature.cart.CartStore.toggle(pkg.toCartLine())
    }

    private suspend fun refreshSharedHeader() {
        // Swift FigmaShipmentsViewController.viewDidAppear refreshes both the
        // user header and AirCoins; keep Android's shared tab header cache in
        // sync even when Shipments is entered directly instead of through Home.
        headerRepo.currentUser().onSuccess { user ->
            SessionStore.update {
                it.copy(
                    greeting = greetingForNow(),
                    firstName = user.firstName.orEmpty(),
                    tierName = user.customerTierName.orEmpty(),
                )
            }
        }
        headerRepo.airCoinsStatus().onSuccess { status ->
            val label = (status.available ?: status.balance)?.toString().orEmpty()
            SessionStore.update { it.copy(airCoins = label) }
        }
    }

    private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
}
