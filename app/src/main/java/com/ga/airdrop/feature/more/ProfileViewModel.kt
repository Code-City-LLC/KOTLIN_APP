package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val taxId: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val dob: String = "",           // display format MM/dd/yyyy
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val language: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val phone: String = "",
    val mobile: String = "",
    val avatar: Bitmap? = null,
    val avatarLoading: Boolean = false,
    val saving: Boolean = false,
    val alert: Pair<String, String>? = null, // title to message
)

/**
 * Edit Profile — FigmaProfileViewController behavior: seed the form from
 * GET /user/profile, avatar upload/delete via /user/profile/image (fully
 * wired, this was a Swift gap), sparse PUT /user/profile on Save with the
 * snake_case field names the Laravel API expects.
 */
class ProfileViewModel(
    private val repository: MoreProfileRepository = MoreRepository(),
) : ViewModel() {

    // RN canonical option lists (modules/Profile/ui/ProfileView/index.tsx).
    val idTypeOptions = listOf("National ID", "Drivers License", "Passport")
    val languageOptions = listOf("English", "Español")
    val countryOptions = listOf("United States", "Jamaica", "Canada", "United Kingdom", "Mexico")
    val stateOptions = listOf("Alabama", "Alaska", "Arizona", "California", "Florida", "New York", "Texas")

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    private var userId: Int? = null
    private var pickupLocation: String? = null
    private var paymentCurrency: String? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repository.currentUser().onSuccess { user ->
                userId = user.id
                pickupLocation = user.pickupLocation
                paymentCurrency = user.paymentCurrency
                _state.update {
                    it.copy(
                        firstName = user.firstName.orEmpty(),
                        lastName = user.lastName.orEmpty(),
                        taxId = user.trnNumber.orEmpty(),
                        idType = user.identityType.orEmpty(),
                        idNumber = user.identityNumber.orEmpty(),
                        email = user.email.orEmpty(),
                        language = user.language.orEmpty(),
                        addressLine1 = user.addressLine1.orEmpty(),
                        addressLine2 = user.addressLine2.orEmpty(),
                        country = user.country.orEmpty(),
                        state = user.state.orEmpty(),
                        city = user.city.orEmpty(),
                        phone = user.phone.orEmpty(),
                        mobile = user.mobile.orEmpty(),
                    )
                }
            }
            refreshAvatar()
        }
    }

    // ─── Field setters ───

    fun set(transform: (ProfileUiState) -> ProfileUiState) = _state.update(transform)

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun toggleConfirmVisible() =
        _state.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    // ─── Avatar ───

    private suspend fun refreshAvatar() {
        val url = repository.profileImage().getOrNull()?.resolvedUrl
        if (url == null) {
            _state.update { it.copy(avatar = null, avatarLoading = false) }
            return
        }
        repository.fetchImage(url)
            .onSuccess { bytes ->
                _state.update {
                    it.copy(
                        avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size),
                        avatarLoading = false,
                    )
                }
            }
            .onFailure { _state.update { it.copy(avatarLoading = false) } }
    }

    fun uploadAvatar(bitmap: Bitmap) {
        _state.update { it.copy(avatar = bitmap, avatarLoading = true) }
        viewModelScope.launch {
            repository.uploadProfileImage(
                bytes = bitmap.toUploadJpeg(),
                fileName = "profile.jpg",
                mimeType = "image/jpeg",
            )
                .onSuccess { refreshAvatar() }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            avatarLoading = false,
                            alert = "Upload failed" to (e.message ?: "Please try again."),
                        )
                    }
                }
        }
    }

    fun deleteAvatar() {
        _state.update { it.copy(avatarLoading = true) }
        viewModelScope.launch {
            repository.deleteProfileImage()
                .onSuccess { _state.update { it.copy(avatar = null, avatarLoading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            avatarLoading = false,
                            alert = "Remove failed" to (e.message ?: "Please try again."),
                        )
                    }
                }
        }
    }

    // ─── Save ───

    fun save() {
        val s = _state.value
        if (s.saving) return
        // Swift ProfileValidator.validate() ported rule-for-rule, surfacing the
        // first error in the same canonical field order and copy.
        profileValidationError(s)?.let { message ->
            _state.update { it.copy(alert = "Invalid Information" to message) }
            return
        }
        if (s.password.isNotEmpty() && s.password != s.confirmPassword) {
            _state.update { it.copy(alert = "Passwords do not match" to "Confirm your password.") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val mobileSource = s.mobile.ifBlank { s.phone }
            val (countryCode, mobileNumber) = mobileParts(mobileSource)
            val fields = mapOf(
                "user_id" to userId?.toString(),
                "email" to s.email.blankToNull(),
                "first_name" to s.firstName.blankToNull(),
                "last_name" to s.lastName.blankToNull(),
                "user_phone" to s.phone.blankToNull(),
                "user_mobile" to mobileNumber,
                "user_country_code" to countryCode,
                "user_trn_number" to s.taxId.blankToNull(),
                "user_identity_type" to s.idType.blankToNull(),
                "user_identity_number" to s.idNumber.blankToNull(),
                "user_dob" to s.dob.toApiDob(),
                "user_language" to s.language.blankToNull(),
                "user_address_line_1" to s.addressLine1.blankToNull(),
                "user_address_line_2" to s.addressLine2.blankToNull(),
                "user_address_city" to s.city.blankToNull(),
                "user_address_state" to s.state.blankToNull(),
                "user_address_country" to s.country.blankToNull(),
                "pickup_location" to pickupLocation?.trim()?.takeIf { it.isNotEmpty() },
                "payment_currency" to (paymentCurrency?.trim()?.takeIf { it.isNotEmpty() } ?: "JMD"),
            )
            repository.updateProfile(fields)
                .onSuccess {
                    _state.update { it.copy(saving = false, alert = "Saved" to "Profile updated.") }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(saving = false, alert = "Save failed" to (e.message ?: "Please try again."))
                    }
                }
        }
    }

    private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }

    /** MM/dd/yyyy (display) → dd-MM-yyyy (API), Swift apiDOBFormatter parity. */
    private fun String.toApiDob(): String? {
        val parts = trim().split("/")
        if (parts.size != 3) return null
        val (mm, dd, yyyy) = parts
        return "$dd-$mm-$yyyy"
    }

    /** Swift FigmaProfileViewController.mobileParts port. */
    private fun mobileParts(value: String): Pair<String?, String?> {
        val cleaned = value.trim().replace(" ", "")
        if (cleaned.isEmpty()) return null to null
        if (cleaned.startsWith("+1876") && cleaned.length > 5) {
            return "+1876" to cleaned.drop(5)
        }
        if (cleaned.startsWith("+1") && cleaned.length > 2) {
            return "+1" to cleaned.drop(2)
        }
        if (cleaned.startsWith("+")) {
            val digits = cleaned.drop(1).filter { it.isDigit() }
            if (digits.isEmpty()) return null to null
            val codeLength = minOf(4, maxOf(1, digits.length - 7))
            return "+${digits.take(codeLength)}" to digits.drop(codeLength)
        }
        return "+1" to cleaned.filter { it.isDigit() }
    }
}

/**
 * Swift ProfileValidator.validate() + firstError(in:) — the 14-field rule set
 * in canonical order, returning the first failure's exact Swift copy.
 */
internal fun profileValidationError(s: ProfileUiState): String? {
    val rules = com.ga.airdrop.core.validation.ProfileRules
    val email = s.email.trim()
    val trn = s.taxId.trim()
    val idNumber = s.idNumber.trim()
    val phone = s.phone.trim()
    val mobile = s.mobile.trim()
    return when {
        s.firstName.trim().length < 2 -> "First name must be at least 2 characters"
        s.lastName.trim().length < 2 -> "Last name must be at least 2 characters"
        email.isEmpty() -> "Email is required"
        !rules.isValidEmail(email) -> "Invalid email format"
        trn.isNotEmpty() && !rules.isValidTrn(trn) -> "TRN must be 9 digits"
        s.idType.trim().isEmpty() -> "Identity type is required"
        idNumber.isEmpty() -> "Identity number is required"
        !rules.isValidIdentityNumber(idNumber, s.idType) -> "Invalid identity number format"
        s.dob.trim().isEmpty() -> "Date of birth is required"
        !rules.isAtLeast15(s.dob.trim()) -> "Invalid date of birth. Must be at least 15 years old"
        s.language.trim().isEmpty() -> "Language is required"
        s.addressLine1.trim().length < 3 -> "Address must be at least 3 characters"
        s.city.trim().length < 2 -> "City is required"
        s.state.trim().length < 2 -> "State/Province is required"
        phone.isNotEmpty() && !rules.isValidPhone(phone) -> "Invalid phone number format"
        mobile.isEmpty() -> "Mobile number is required"
        !rules.isValidMobile(mobile) -> "Invalid mobile number. Must be 10-15 digits"
        else -> null
    }
}
