package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.Warehouse
import com.ga.airdrop.data.repo.MiscRepository
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
interface WarehousesRepository {
    suspend fun currentUser(): Result<AirdropUser>
    suspend fun warehouses(): Result<List<Warehouse>>
}

private class DefaultWarehousesRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
    private val miscRepository: MiscRepository = MiscRepository(ApiClient.service),
) : WarehousesRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()
    override suspend fun warehouses(): Result<List<Warehouse>> = miscRepository.warehouses()
}

class WarehousesViewModel(
    private val repository: WarehousesRepository = DefaultWarehousesRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(WarehousesUiState())
    val state: StateFlow<WarehousesUiState> = _state
    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var loadJob: Job? = null

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
                sessionOwner = changed
                _state.value = WarehousesUiState()
                if (changed != null) load()
            }
        }
        load()
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob = sessionJobs.launch {
            sessionBoundary.apply(owner) { _state.update { it.copy(loading = true) } }
            val user = async { repository.currentUser() }
            val warehouses = async { repository.warehouses() }
            val userResult = user.await()
            val warehouseResult = warehouses.await()
            val loadedUserId = userResult.getOrNull()?.id
            if (loadedUserId != null && !sessionBoundary.bindAccountId(owner, loadedUserId)) return@launch
            sessionBoundary.apply(owner) {
                userResult.onSuccess { value -> _state.update { it.copy(user = value) } }
                warehouseResult.onSuccess { rows -> _state.update { it.copy(warehouses = rows) } }
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Pick the row whose name matches the tab, else the first row — Swift matchingWarehouse. */
    fun warehouseFor(typeKey: String): Warehouse? =
        _state.value.warehouses.firstOrNull {
            (it.name ?: "").lowercase().contains(typeKey.lowercase())
        } ?: _state.value.warehouses.firstOrNull()
}
