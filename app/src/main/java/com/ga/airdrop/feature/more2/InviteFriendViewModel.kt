package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.ReferFriendRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteFriendUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val description: String = "",
    val saving: Boolean = false,
    val successMessage: String? = null,
    val validationError: String? = null,
    val error: String? = null,
)

/** FigmaInviteFriendViewController: name/email form → POST /refer-friend. */
class InviteFriendViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(InviteFriendUiState())
    val state: StateFlow<InviteFriendUiState> = _state

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun dismissValidation() = _state.update { it.copy(validationError = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    /** Prefill from a picked contact (name + email). */
    fun prefillContact(firstName: String, lastName: String, email: String) =
        _state.update { it.copy(firstName = firstName, lastName = lastName, email = email) }

    /** Android contacts return a display name; keep it in the Swift first/last field shape. */
    fun prefillContact(displayName: String, email: String) {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = parts.firstOrNull().orEmpty()
        val last = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
        prefillContact(first, last, email)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val first = s.firstName.trim()
        val last = s.lastName.trim()
        val email = s.email.trim()
        val desc = s.description.trim()

        fun fail(message: String) = _state.update { it.copy(validationError = message) }
        if (first.isEmpty()) return fail("First name is required.")
        if (last.isEmpty()) return fail("Last name is required.")
        if (!isValidEmail(email)) return fail("Please enter a valid email address.")

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.referFriend(
                ReferFriendRequest(
                    friendFirstName = first,
                    friendLastName = last,
                    friendEmail = email,
                    description = desc.ifEmpty { null },
                ),
            )
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            saving = false,
                            successMessage = response.message
                                ?: "Your invitation has been sent successfully. Your friend " +
                                "will receive an email with a unique referral link.",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotEmpty() &&
            Regex("^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email)
}
