package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedSession
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.core.session.captureOwnedRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
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
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    // RN canonical option lists (modules/Profile/ui/ProfileView/index.tsx).
    val idTypeOptions = listOf("National ID", "Drivers License", "Passport")
    val languageOptions = listOf("English", "Español")
    val countryOptions = listOf("United States", "Jamaica", "Canada", "United Kingdom", "Mexico")
    val stateOptions = listOf("Alabama", "Alaska", "Arizona", "California", "Florida", "New York", "Texas")

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var loadJob: Job? = null
    private var avatarJob: Job? = null
    private var saveJob: Job? = null
    private var userId: Int? = null
    private var pickupLocation: String? = null
    private var paymentCurrency: String? = null

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
                avatarJob = null
                saveJob = null
                sessionOwner = changed
                resetOwnedState()
                if (changed != null) load()
            }
        }
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        val owner = sessionBoundary.captureOwnedSession(sessionOwner) ?: return
        loadJob = sessionJobs.launch {
            repository.currentUser().onSuccess { user ->
                val loadedUserId = user.id
                if (loadedUserId != null && !sessionBoundary.bindAccountId(owner, loadedUserId)) return@onSuccess
                sessionBoundary.apply(owner) {
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
            }
            if (sessionBoundary.isCurrent(owner)) refreshAvatar(owner)
        }
    }

    // ─── Field setters ───

    fun set(transform: (ProfileUiState) -> ProfileUiState) = _state.update(transform)

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun toggleConfirmVisible() =
        _state.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    // ─── Avatar ───

    private suspend fun refreshAvatar(owner: AuthenticatedSessionOwner) {
        val url = repository.profileImage().getOrNull()?.resolvedUrl
        if (!sessionBoundary.isCurrent(owner)) return
        if (url == null) {
            sessionBoundary.apply(owner) {
                _state.update { it.copy(avatar = null, avatarLoading = false) }
            }
            return
        }
        repository.fetchImage(url)
            .onSuccess { bytes ->
                sessionBoundary.apply(owner) {
                    _state.update {
                        it.copy(
                            avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size),
                            avatarLoading = false,
                        )
                    }
                }
            }
            .onFailure {
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatarLoading = false) }
                }
            }
    }

    fun uploadAvatar(bitmap: Bitmap) {
        if (avatarJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        if (!sessionBoundary.apply(requestOwner.session) {
                _state.update { it.copy(avatar = bitmap, avatarLoading = true) }
            }
        ) return
        avatarJob = sessionJobs.launch {
            repository.uploadProfileImage(
                bytes = bitmap.toUploadJpeg(),
                fileName = "profile.jpg",
                mimeType = "image/jpeg",
                expectedSession = requestOwner.provenance,
            )
                .onSuccess { refreshAvatar(requestOwner.session) }
                .onFailure { e ->
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update {
                            it.copy(
                                avatarLoading = false,
                                alert = "Upload failed" to (e.message ?: "Please try again."),
                            )
                        }
                    }
                }
        }
    }

    fun deleteAvatar() {
        if (avatarJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        if (!sessionBoundary.apply(requestOwner.session) {
                _state.update { it.copy(avatarLoading = true) }
            }
        ) return
        avatarJob = sessionJobs.launch {
            repository.deleteProfileImage(requestOwner.provenance)
                .onSuccess {
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update { it.copy(avatar = null, avatarLoading = false) }
                    }
                }
                .onFailure { e ->
                    sessionBoundary.apply(requestOwner.session) {
                        _state.update {
                            it.copy(
                                avatarLoading = false,
                                alert = "Remove failed" to (e.message ?: "Please try again."),
                            )
                        }
                    }
                }
        }
    }

    // ─── Save ───

    fun save() {
        if (saveJob?.isActive == true) return
        val requestOwner = sessionBoundary.captureOwnedRequest(sessionOwner) ?: return
        val owner = requestOwner.session
        val s = _state.value
        if (s.saving) return
        if (s.firstName.isBlank() || s.lastName.isBlank() || s.email.isBlank()) {
            sessionBoundary.apply(owner) {
                _state.update {
                    it.copy(alert = "Missing fields" to "Please fill the required fields marked with *.")
                }
            }
            return
        }
        if (s.password.isNotEmpty() && s.password != s.confirmPassword) {
            sessionBoundary.apply(owner) {
                _state.update { it.copy(alert = "Passwords do not match" to "Confirm your password.") }
            }
            return
        }
        if (!sessionBoundary.apply(owner) {
                _state.update { it.copy(saving = true) }
            }
        ) return
        val requestUserId = userId
        val requestPickupLocation = pickupLocation
        val requestPaymentCurrency = paymentCurrency
        saveJob = sessionJobs.launch {
            val mobileSource = s.mobile.ifBlank { s.phone }
            val (countryCode, mobileNumber) = mobileParts(mobileSource)
            val fields = mapOf(
                "user_id" to requestUserId?.toString(),
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
                "pickup_location" to requestPickupLocation?.trim()?.takeIf { it.isNotEmpty() },
                "payment_currency" to (requestPaymentCurrency?.trim()?.takeIf { it.isNotEmpty() } ?: "JMD"),
            )
            repository.updateProfile(fields, requestOwner.provenance)
                .onSuccess {
                    sessionBoundary.apply(owner) {
                        _state.update { it.copy(saving = false, alert = "Saved" to "Profile updated.") }
                    }
                }
                .onFailure { e ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(saving = false, alert = "Save failed" to (e.message ?: "Please try again."))
                        }
                    }
                }
        }
    }

    private fun resetOwnedState() {
        userId = null
        pickupLocation = null
        paymentCurrency = null
        _state.value = ProfileUiState()
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
