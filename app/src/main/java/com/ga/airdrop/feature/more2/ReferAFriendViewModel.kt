package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.ReferredFriend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReferAFriendUiState(
    // Backend hasn't shipped /refer-friend/code (Swift note 2026-05-22), so the
    // link falls back to the account-number URL pattern from RN ReferView.
    val referralLink: String = "https://airdropja.com/refer",
    val referrals: List<ReferredFriend> = emptyList(),
    val loadingReferrals: Boolean = false,
)

/** FigmaReferAFriendViewController: referral link + GET /refer-friend list. */
class ReferAFriendViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReferAFriendUiState())
    val state: StateFlow<ReferAFriendUiState> = _state

    init {
        loadReferralLink()
        loadReferredFriends()
    }

    private fun loadReferralLink() {
        viewModelScope.launch {
            repository.profile().onSuccess { user ->
                val account = user.accountNumber
                if (!account.isNullOrEmpty()) {
                    _state.update { it.copy(referralLink = "https://airdropja.com/refer/$account") }
                }
            }
            // Failure is silent, matching the Swift print-only handling.
        }
    }

    fun loadReferredFriends() {
        _state.update { it.copy(loadingReferrals = true) }
        viewModelScope.launch {
            repository.referredFriends(limit = 20)
                .onSuccess { friends ->
                    _state.update { it.copy(loadingReferrals = false, referrals = friends) }
                }
                .onFailure {
                    _state.update { it.copy(loadingReferrals = false, referrals = emptyList()) }
                }
        }
    }
}
