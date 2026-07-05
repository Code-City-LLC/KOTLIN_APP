package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.AuthorizedUserRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// RN ships these 3 ID-type options verbatim.
internal val ID_TYPE_OPTIONS = listOf("National ID", "Drivers License", "Passport")

data class AddAuthorizedUserUiState(
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val idType: String = "National ID",
    val idNumber: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val trn: String = "",
    val isEditMode: Boolean = false,
    val loadingUser: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val validationError: String? = null,
    val error: String? = null,
)

/**
 * FigmaAddAuthorizedUserViewController: POST /authorized-users in add mode, or
 * PUT /authorized-users/{id} with the same payload in edit mode after prefill.
 */
class AddAuthorizedUserViewModel(
    private val editId: Int?,
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(AddAuthorizedUserUiState(isEditMode = editId != null))
    val state: StateFlow<AddAuthorizedUserUiState> = _state

    init {
        if (editId != null) prefill(editId)
    }

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v) }
    fun onIdType(v: String) = _state.update { it.copy(idType = v) }
    fun onIdNumber(v: String) = _state.update { it.copy(idNumber = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onMobileNumber(v: String) = _state.update { it.copy(mobileNumber = v) }
    fun onTrn(v: String) = _state.update { it.copy(trn = v) }
    fun dismissValidation() = _state.update { it.copy(validationError = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private fun prefill(id: Int) {
        _state.update { it.copy(loadingUser = true) }
        viewModelScope.launch {
            repository.authorizedUser(id)
                .onSuccess { user ->
                    // Recombine country code + mobile into the single "+CC mobile"
                    // field the form uses, mirroring the Swift prefillForm.
                    val cc = user.countryCode.orEmpty()
                    val mn = user.mobileNumber.orEmpty()
                    val mobile = when {
                        cc.isNotEmpty() && mn.isNotEmpty() -> "$cc $mn"
                        else -> mn
                    }
                    _state.update {
                        it.copy(
                            loadingUser = false,
                            firstName = user.firstName.orEmpty(),
                            middleName = user.middleName.orEmpty(),
                            lastName = user.lastName.orEmpty(),
                            idType = user.identificationType
                                ?.takeIf { t -> t in ID_TYPE_OPTIONS } ?: it.idType,
                            idNumber = user.identificationIdNumber.orEmpty(),
                            email = user.email.orEmpty(),
                            mobileNumber = mobile,
                            trn = user.trnNumber.orEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingUser = false, error = e.toUserMessage()) }
                }
        }
    }

    /** Mirror RN AddAuthorizedUserView validation 1:1, then POST/PUT. */
    fun save() {
        val s = _state.value
        if (s.saving) return

        val firstName = s.firstName.trim()
        val lastName = s.lastName.trim()
        val idNumber = s.idNumber.trim()
        val email = s.email.trim()
        val mobile = s.mobileNumber.trim()
        val trn = s.trn.trim()

        fun fail(message: String) = _state.update { it.copy(validationError = message) }

        if (firstName.isEmpty()) return fail("Please enter First Name")
        if (lastName.isEmpty()) return fail("Please enter Last Name")
        if (idNumber.isEmpty()) return fail("Please enter Identification Number")
        if (email.isEmpty()) return fail("Please enter Email Address")
        if (!isValidEmail(email)) return fail("Please enter a valid Email Address")
        if (mobile.isEmpty()) return fail("Please enter Mobile Number")

        // RN parses "+CC mobile" out of the single mobile-number field.
        var countryCode = ""
        var parsedMobile = mobile.filter { it.isDigit() }
        if (mobile.startsWith("+")) {
            val match = Regex("^(\\+\\d{1,4})\\s*(.*)$").find(mobile)
            if (match != null) {
                countryCode = match.groupValues[1]
                parsedMobile = match.groupValues[2].filter { it.isDigit() }
            }
        }
        if (countryCode.isEmpty()) return fail("Please add Country Code for Mobile Number")
        if (parsedMobile.isEmpty()) return fail("Please enter valid Mobile Number")
        if (trn.isEmpty()) return fail("Please enter Tax Registration Number")

        val payload = AuthorizedUserRequest(
            userFirstName = firstName,
            userMiddleName = s.middleName.ifEmpty { null },
            userLastName = lastName,
            identificationType = s.idType,
            identificationIdNumber = idNumber,
            userEmail = email,
            userCountryCode = countryCode,
            userMobileNumber = parsedMobile,
            trnNo = trn,
            activeTimes = null,
        )

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (editId != null) {
                repository.updateAuthorizedUser(editId, payload)
            } else {
                repository.addAuthorizedUser(payload)
            }
            result
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
}
