package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.Warehouse
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarehousesUiState(
    val user: AirdropUser? = null,
    val warehouses: List<Warehouse> = emptyList(),
    val loading: Boolean = false,
)

/**
 * Data source for FigmaWarehousesViewController parity: current user (full
 * name + account number) and the `/warehouse` rows, fetched in parallel,
 * each best-effort — the screen falls back to the RN constants otherwise.
 */
class WarehousesViewModel(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(WarehousesUiState())
    val state: StateFlow<WarehousesUiState> = _state

    init {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val user = async { userRepository.currentUser() }
            val warehouses = async { miscRepository.warehouses() }
            user.await().onSuccess { u -> _state.update { it.copy(user = u) } }
            warehouses.await().onSuccess { rows -> _state.update { it.copy(warehouses = rows) } }
            _state.update { it.copy(loading = false) }
        }
    }

    /** Pick the row whose name matches the tab, else the first row — Swift matchingWarehouse. */
    fun warehouseFor(typeKey: String): Warehouse? =
        _state.value.warehouses.firstOrNull {
            (it.name ?: "").lowercase().contains(typeKey.lowercase())
        } ?: _state.value.warehouses.firstOrNull()
}
