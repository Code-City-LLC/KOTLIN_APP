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
    val description: String = "",
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
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun dismissValidation() = _state.update { it.copy(validationError = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissContactOptions() = _state.update { it.copy(selectedContact = null) }

    fun requireReferralLink(): Boolean {
        if (_state.value.referralLink.contains("/refer/")) return true
        _state.update {
            it.copy(validationError = "Your referral code is still loading. Please try again in a moment.")
        }
        return false
    }

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

    fun sendEmailInvitation(
        contact: InviteContact,
        showSuccess: Boolean = true,
        onSuccess: (() -> Unit)? = null,
    ) {
        if (_state.value.saving) return
        val first = contact.firstName.trim().ifBlank { "Friend" }
        val last = contact.lastName.trim().ifBlank { "Friend" }
        val email = contact.email.trim()

        // ⚠️ ifBlank covers an EMPTY name and nothing else. These names come
        // straight out of the device address book, so they routinely break the
        // server's other rules — a single-letter first name fails min:2, and any
        // accented name ("José", "Renée") fails the letters-only regex. Both used
        // to reach the customer as a bare 422 from a button that just stopped
        // working. Now they land on validationError, which the screen shows, and
        // the customer can fix it in the form.
        val nameProblem = validateReferralName(first, "First name", REFERRAL_FIRST_NAME_MAX)
            ?: validateReferralName(last, "Last name", REFERRAL_LAST_NAME_MAX)
        if (nameProblem != null) {
            _state.update {
                it.copy(
                    // Prefill so the fix is one edit away, not a re-pick.
                    firstName = first,
                    lastName = last,
                    email = email,
                    selectedContact = null,
                    validationError = "$nameProblem Edit the name below and send again.",
                )
            }
            return
        }
        if (!isValidEmail(email) || email.length > REFERRAL_EMAIL_MAX) {
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
                            successMessage = message.takeIf { showSuccess },
                            validationError = null,
                            error = null,
                        )
                    }
                    onSuccess?.invoke()
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
        val description = s.description.trim()

        fun fail(message: String) = _state.update { it.copy(validationError = message) }
        if (first.isEmpty()) return fail("First name is required.")
        if (last.isEmpty()) return fail("Last name is required.")
        validateReferralName(first, "First name", REFERRAL_FIRST_NAME_MAX)?.let { return fail(it) }
        validateReferralName(last, "Last name", REFERRAL_LAST_NAME_MAX)?.let { return fail(it) }
        if (!isValidEmail(email)) return fail("Please enter a valid email address.")
        if (email.length > REFERRAL_EMAIL_MAX) {
            return fail("Email address can be at most $REFERRAL_EMAIL_MAX characters.")
        }
        if (description.length > REFERRAL_DESCRIPTION_MAX) {
            return fail("Message can be at most $REFERRAL_DESCRIPTION_MAX characters.")
        }

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
                description = description.ifEmpty { null },
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
                            description = "",
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

    /**
     * Laravel `ReferFriendRequest` in full, enforced client-side.
     *
     * Both invite paths previously checked only `isEmpty()` / `isBlank()`, so
     * every other rule reached the customer as a bare 422 with no field named:
     *
     *   friend_first_name  min:2 max:60  regex ^[a-zA-Z\s\-']+$
     *   friend_last_name   min:2 max:70  regex ^[a-zA-Z\s\-']+$
     *   friend_email       max:100
     *   description        max:500
     *
     * The regex is the one that actually bites, because the contacts path feeds
     * names straight from the device address book: "José", "Renée", "Seán" and
     * anything with a digit are all rejected by the server. Telling the customer
     * which name to edit is the difference between a fixable form and a dead
     * button.
     *
     * Names are NOT silently transliterated — rewriting someone's name to make
     * a request pass is not a fix.
     */
    private fun validateReferralName(value: String, label: String, max: Int): String? = when {
        value.length < REFERRAL_NAME_MIN -> "$label must be at least $REFERRAL_NAME_MIN characters."
        value.length > max -> "$label can be at most $max characters."
        !REFERRAL_NAME_REGEX.matches(value) ->
            "$label can only contain letters, spaces, hyphens and apostrophes."
        else -> null
    }

    private companion object {
        const val REFERRAL_NAME_MIN = 2
        const val REFERRAL_FIRST_NAME_MAX = 60
        const val REFERRAL_LAST_NAME_MAX = 70
        const val REFERRAL_EMAIL_MAX = 100
        const val REFERRAL_DESCRIPTION_MAX = 500
        val REFERRAL_NAME_REGEX = Regex("^[a-zA-Z\\s\\-']+$")
    }
}
