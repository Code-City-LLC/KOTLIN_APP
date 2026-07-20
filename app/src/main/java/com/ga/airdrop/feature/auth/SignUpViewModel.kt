package com.ga.airdrop.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.SignUpRequest
import com.ga.airdrop.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Country options — the RN useSignUp defaultCountries (Jamaica pinned
 * first, then US / Canada / UK) with their dial codes and state lists
 * (states from the Swift bundled countries.json for these markets).
 */
data class SignUpCountry(
    val name: String,
    val phoneCode: String,
    val states: List<String>,
)

val signUpCountries = listOf(
    SignUpCountry(
        name = "Jamaica",
        phoneCode = "1-876",
        states = listOf(
            "Clarendon", "Hanover", "Kingston", "Manchester", "Portland",
            "Saint Andrew", "Saint Ann", "Saint Catherine", "Saint Elizabeth",
            "Saint James", "Saint Mary", "Saint Thomas", "Trelawny", "Westmoreland",
        ),
    ),
    SignUpCountry(
        name = "United States",
        phoneCode = "1",
        states = listOf(
            "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado",
            "Connecticut", "Delaware", "District of Columbia", "Florida", "Georgia",
            "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky",
            "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan",
            "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada",
            "New Hampshire", "New Jersey", "New Mexico", "New York",
            "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon",
            "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota",
            "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington",
            "West Virginia", "Wisconsin", "Wyoming",
        ),
    ),
    SignUpCountry(
        name = "Canada",
        phoneCode = "1",
        states = listOf(
            "Alberta", "British Columbia", "Manitoba", "New Brunswick",
            "Newfoundland and Labrador", "Northwest Territories", "Nova Scotia",
            "Nunavut", "Ontario", "Prince Edward Island", "Quebec", "Saskatchewan",
            "Yukon",
        ),
    ),
    SignUpCountry(
        name = "United Kingdom",
        phoneCode = "44",
        states = listOf("England", "Northern Ireland", "Scotland", "Wales"),
    ),
)

/** RN Profile MainPicker values for pickup_location. */
// Canonical three pickup counters — single source in data/model/Delivery.kt.
val pickupLocationOptions: List<String> = com.ga.airdrop.data.model.PICKUP_COUNTER_NAMES

/** Swift SignUpViewController "channel" options (user_hear_type). */
val hearAboutUsOptions = listOf(
    "Instagram/Facebook",
    "Google/YouTube",
    "Radio",
    "Recommended by Someone",
    "Airdrop Outdoor Promotion",
    "Other",
)

// KEMAR RULING 2026-07-19 (Swift 64f4fdc): no TRN/identity fields at sign-up —
// RN had them, deliberately not ported. Customers add identity via Profile
// after shipping a package. Do not re-add.

data class SignUpUiState(
    val firstName: String = "",
    val lastName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val email: String = "",
    val confirmEmail: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val pickupLocation: String = "",
    val hearType: String = "",
    val mobile: String = "",
    val acceptTerms: Boolean = false,
    val acceptCustomsAuth: Boolean = false,
    val loading: Boolean = false,
    val alert: Pair<String, String>? = null, // title to message
    val registered: Boolean = false,
) {
    val stateOptions: List<String>
        get() = signUpCountries.firstOrNull { it.name == country }?.states.orEmpty()
}

class SignUpViewModel(
    private val repository: AuthRepository =
        AuthRepository(com.ga.airdrop.core.network.ApiClient.service),
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state

    fun set(transform: (SignUpUiState) -> SignUpUiState) = _state.update(transform)

    /** One-shot: the success dialog consumes `registered` when dismissed. */
    fun consumeRegistered() = _state.update { it.copy(registered = false) }

    fun togglePasswordVisible() =
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun toggleConfirmPasswordVisible() =
        _state.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }

    /** RN parity: picking a country resets the dependent state selection. */
    fun selectCountry(name: String) =
        _state.update { it.copy(country = name, state = "", city = "") }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    fun signUp() {
        val s = _state.value
        if (s.loading) return

        validationError(s)?.let { message ->
            _state.update { it.copy(alert = "Attention" to message) }
            return
        }

        _state.update { it.copy(loading = true) }
        val countryCode = signUpCountries.firstOrNull { it.name == s.country }?.phoneCode ?: "1"
        val request = SignUpRequest(
            firstName = s.firstName.trim(),
            lastName = s.lastName.trim(),
            email = s.email.trim(),
            password = s.password,
            confirmPassword = s.confirmPassword,
            userCountryCode = countryCode,
            userMobile = s.mobile.trim(),
            userAddressLine1 = s.addressLine1.trim(),
            userAddressLine2 = s.addressLine2.trim().takeIf { it.isNotEmpty() },
            userAddressCity = s.city.trim(),
            userAddressState = s.state,
            userAddressCountry = s.country,
            userHearType = s.hearType,
            userPickupLocation = s.pickupLocation,
            userTnc = s.acceptTerms,
            userAuth = s.acceptCustomsAuth,
        )
        viewModelScope.launch {
            repository.signUp(request)
                .onSuccess {
                    _state.update { it.copy(loading = false, registered = true) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            alert = "Attention" to
                                (e.message ?: "Unable to sign up. Please try again."),
                        )
                    }
                }
        }
    }

    /** Swift SignUpViewController-style pre-flight checks. */
    private fun validationError(s: SignUpUiState): String? {
        val required = listOf(
            s.firstName to "First Name",
            s.lastName to "Last Name",
            s.password to "Password",
            s.confirmPassword to "Confirm Password",
            s.email to "Email Address",
            s.confirmEmail to "Confirm Email Address",
            s.addressLine1 to "Address line 1",
            s.country to "Country",
            s.state to "State/Province/Department",
            s.city to "City",
            s.pickupLocation to "Pickup Location",
            s.hearType to "How did you hear about us",
            s.mobile to "Mobile Number",
        )
        required.firstOrNull { it.first.isBlank() }?.let {
            return "Please fill in ${it.second}."
        }
        // Swift FigmaSignUpViewController.validate(): 8-char minimum before
        // the confirm-match check.
        if (s.password.length < 8) {
            return "Password must be at least 8 characters long."
        }
        if (s.password != s.confirmPassword) {
            return "Passwords do not match."
        }
        // Swift SSOT email rule (ProfileValidator.isValidEmail — 2+ letter TLD).
        if (!com.ga.airdrop.core.validation.ProfileRules.isValidEmail(s.email.trim())) {
            return "Please enter a valid email address."
        }
        if (s.email.trim() != s.confirmEmail.trim()) {
            return "Email addresses do not match."
        }
        // Swift: ProfileValidator.isValidMobile — 10-15 digits.
        if (!com.ga.airdrop.core.validation.ProfileRules.isValidMobile(s.mobile.trim())) {
            return "Invalid mobile number. Must be 10-15 digits."
        }
        if (!s.acceptTerms) {
            return "Please accept the Terms and Conditions."
        }
        if (!s.acceptCustomsAuth) {
            return "Please accept the customs authorization form."
        }
        return null
    }
}
