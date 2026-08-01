package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.prefs.AvatarStore
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
import com.ga.airdrop.feature.auth.signUpCountries

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val taxId: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val dob: String = "",           // display format MM/dd/yyyy
    val email: String = "",
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
private val LEGACY_US_STATE_FALLBACK = listOf(
    "Alabama", "Alaska", "Arizona", "California", "Florida", "New York", "Texas",
)

class ProfileViewModel(
    private val repository: MoreProfileRepository = MoreRepository(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    // RN canonical option lists (modules/Profile/ui/ProfileView/index.tsx).
    val idTypeOptions = listOf("National ID", "Drivers License", "Passport")
    val languageOptions = listOf("English", "Español")
    val countryOptions = listOf("United States", "Jamaica", "Canada", "United Kingdom", "Mexico")

    /**
     * State/Province/Department options for the SELECTED country.
     *
     * ⚠️ This was a flat `listOf("Alabama", "Alaska", "Arizona", "California",
     * "Florida", "New York", "Texas")` — seven US states, offered regardless of
     * country. In the app's HOME market that meant a Jamaican customer opened
     * the required State/Province row and could not pick their parish at all;
     * there was no "Other" escape hatch on this screen either.
     *
     * Sign Up already solved this: [signUpCountries] is the canonical catalog
     * and carries all 14 Jamaican parishes plus the full US/Canada/UK
     * subdivisions. Profile now reads the same source instead of its own stale
     * copy, so the two screens can no longer disagree. Laravel stores this as
     * free text ('state' => nullable|string|max:100 — no `in:` constraint), so
     * the wider list cannot 422.
     *
     * The legacy list survives only as a fallback for a country the catalog
     * does not cover (Mexico is offered above but absent from the catalog), so
     * no country can end up with an empty, dead picker.
     */
    fun stateOptionsFor(country: String): List<String> =
        signUpCountries.firstOrNull { it.name.equals(country.trim(), ignoreCase = true) }
            ?.states
            ?.takeIf { it.isNotEmpty() }
            ?: LEGACY_US_STATE_FALLBACK

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
        // Adopt avatar changes made on the More tab, so this screen is not the
        // stale one. Session-guarded for the same reason as the publisher.
        viewModelScope.launch {
            AvatarStore.avatar.collect { shared ->
                val owner = sessionBoundary.capture() ?: return@collect
                if (owner.sessionId != sessionOwner?.sessionId) return@collect
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatar = shared) }
                }
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
            }.onFailure {
                    // ⚠️ There was no failure branch at all. A failed
                    // /user/profile left every field at its "" default with no
                    // message and no retry, so the screen read as "AirDrop has
                    // no record of me" rather than "we couldn't load it".
                    // (Save itself is safe — the required-field guard above
                    // blocks submitting an unloaded form — so this is a
                    // silent-failure defect, not a data-loss one.)
                    sessionBoundary.apply(owner) {
                        _state.update { s ->
                            s.copy(
                                alert = "Couldn't load your profile" to
                                    "Check your connection and open Profile again.",
                            )
                        }
                    }
                }
            if (sessionBoundary.isCurrent(owner)) refreshAvatar(owner)
        }
    }

    // ─── Field setters ───

    fun set(transform: (ProfileUiState) -> ProfileUiState) = _state.update(transform)

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    // ─── Avatar ───

    private suspend fun refreshAvatar(owner: AuthenticatedSessionOwner) {
        // ⚠️ A FAILED READ IS NOT "NO PHOTO". `.getOrNull()?.resolvedUrl` is null
        // for BOTH, and treating them alike called AvatarStore.clear() on a
        // transient network failure — wiping the customer's photo from every
        // screen that reads the store. AvatarStore.publish(null) deliberately
        // no-ops for exactly this reason ("a screen that simply failed to load
        // one cannot blank it everywhere else"); calling clear() directly walked
        // straight around that guard.
        val loaded = repository.profileImage()
        if (!sessionBoundary.isCurrent(owner)) return
        if (loaded.isFailure) {
            // Keep whatever is already on screen and in the store. Stop the
            // spinner, change nothing else.
            sessionBoundary.apply(owner) {
                _state.update { it.copy(avatarLoading = false) }
            }
            return
        }
        val url = loaded.getOrNull()?.resolvedUrl
        if (url == null) {
            // The read SUCCEEDED and the customer genuinely has no photo — this
            // is the only case where clearing is the truth.
            sessionBoundary.apply(owner) {
                _state.update { it.copy(avatar = null, avatarLoading = false) }
                AvatarStore.clear()
            }
            return
        }
        repository.fetchImage(url)
            .onSuccess { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                sessionBoundary.apply(owner) {
                    _state.update { it.copy(avatar = bitmap, avatarLoading = false) }
                    // The More tab reads the same store, so a photo changed here
                    // no longer waits for that screen to reload.
                    AvatarStore.publish(bitmap)
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
                        AvatarStore.clear()
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
