package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteFriendUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val referralLink: String = "https://airdropja.com/refer",
    val selectedContact: InviteContact? = null,
    val saving: Boolean = false,
    val successMessage: String? = null,
    val validationError: String? = null,
    val error: String? = null,
)

data class InviteContact(
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
)

interface InviteFriendRepository {
    suspend fun currentUser(): Result<AirdropUser>

    suspend fun referFriend(
        firstName: String,
        lastName: String,
        email: String,
        description: String?,
    ): Result<MutationResponse>
}

private class DefaultInviteFriendRepository(
    private val userRepository: UserRepository = UserRepository(ApiClient.service),
) : InviteFriendRepository {
    override suspend fun currentUser(): Result<AirdropUser> = userRepository.currentUser()

    override suspend fun referFriend(
        firstName: String,
        lastName: String,
        email: String,
        description: String?,
    ): Result<MutationResponse> =
        userRepository.referFriend(firstName, lastName, email, description)
}

/** FigmaInviteFriendViewController: name/email form → POST /refer-friend. */
class InviteFriendViewModel(
    private val repository: InviteFriendRepository = DefaultInviteFriendRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(InviteFriendUiState())
    val state: StateFlow<InviteFriendUiState> = _state

    init {
        loadReferralLink()
    }

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun dismissValidation() = _state.update { it.copy(validationError = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissContactOptions() = _state.update { it.copy(selectedContact = null) }

    private fun loadReferralLink() {
        viewModelScope.launch {
            repository.currentUser().onSuccess { user ->
                val account = user.accountNumber?.trim().orEmpty()
                if (account.isNotEmpty()) {
                    _state.update { it.copy(referralLink = "https://airdropja.com/refer/$account") }
                }
            }
        }
    }

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

    fun onContactPicked(displayName: String, email: String, phone: String) {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = parts.firstOrNull().orEmpty()
        val last = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
        _state.update {
            it.copy(
                selectedContact = InviteContact(
                    displayName = displayName.trim().ifEmpty { "this contact" },
                    firstName = first,
                    lastName = last,
                    email = email.trim(),
                    phone = phone.trim(),
                ),
            )
        }
    }

    fun useContactInForm(contact: InviteContact) {
        _state.update {
            it.copy(
                firstName = contact.firstName,
                lastName = contact.lastName,
                email = contact.email,
                selectedContact = null,
            )
        }
    }

    fun onInvitationShared() {
        _state.update {
            it.copy(
                selectedContact = null,
                successMessage = "Your invitation has been shared successfully. Your friend will receive a " +
                    "message with a unique referral link.",
            )
        }
    }

    fun sendEmailInvitation(contact: InviteContact) {
        if (_state.value.saving) return
        val first = contact.firstName.ifBlank { "Friend" }
        val last = contact.lastName.ifBlank { "Friend" }
        val email = contact.email.trim()
        if (!isValidEmail(email)) {
            _state.update {
                it.copy(
                    selectedContact = null,
                    validationError = "Please enter a valid email address.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                saving = true,
                selectedContact = null,
                successMessage = null,
                validationError = null,
                error = null,
            )
        }
        viewModelScope.launch {
            repository.referFriend(
                firstName = first,
                lastName = last,
                email = email,
                description = "Invited from Android contacts",
            )
                .onSuccess { response ->
                    val message = response.message
                        ?: "Your invitation has been sent successfully. Your friend " +
                        "will receive an email with a unique referral link."
                    _state.update {
                        it.copy(
                            firstName = "",
                            lastName = "",
                            email = "",
                            selectedContact = null,
                            saving = false,
                            successMessage = message,
                            validationError = null,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = e.toUserMessage(),
                            successMessage = null,
                        )
                    }
                }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val first = s.firstName.trim()
        val last = s.lastName.trim()
        val email = s.email.trim()

        fun fail(message: String) = _state.update { it.copy(validationError = message) }
        if (first.isEmpty()) return fail("First name is required.")
        if (last.isEmpty()) return fail("Last name is required.")
        if (!isValidEmail(email)) return fail("Please enter a valid email address.")

        _state.update {
            it.copy(
                saving = true,
                successMessage = null,
                validationError = null,
                error = null,
            )
        }
        viewModelScope.launch {
            repository.referFriend(
                firstName = first,
                lastName = last,
                email = email,
                description = null,
            )
                .onSuccess { response ->
                    val message = response.message
                        ?: "Your invitation has been sent successfully. Your friend " +
                        "will receive an email with a unique referral link."
                    _state.update {
                        it.copy(
                            firstName = "",
                            lastName = "",
                            email = "",
                            saving = false,
                            successMessage = message,
                            validationError = null,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = e.toUserMessage(),
                            successMessage = null,
                        )
                    }
                }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotEmpty() &&
            Regex("^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email)
}
