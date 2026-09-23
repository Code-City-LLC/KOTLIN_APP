package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.parseApiError
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.AuthorizedUserRequest
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// RN ships these 3 ID-type options verbatim.
/** Matches Laravel `identification_type` → `in:National ID,Drivers License,Passport`. */
internal val ID_TYPE_OPTIONS = listOf("National ID", "Drivers License", "Passport")

// The remaining StoreAuthorizedUserRequest bounds, mirrored client-side so the
// customer gets an actionable message instead of a bare 422 from the server.
internal const val TRN_DIGITS = 9 // trn_no => digits:9
internal const val ID_NUMBER_MAX = 14 // identification_id_number => max:14

data class AddAuthorizedUserUiState(
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val idType: String = "National ID",
    val idNumber: String = "",
    val email: String = "",
    /**
     * Digits only — the calling code lives in [phoneIso], never in this box
     * (Kemar 2026-09-15). The one exception is a "+CC" still being typed
     * ("+4"): it stays until it names a country, then moves into the picker.
     */
    val mobileNumber: String = "",
    val phoneIso: String = AuthorizedUserPhoneInput.DEFAULT_ISO,
    /** The customer typed a number or picked a code; the profile default must not move the picker under them. */
    val phoneTouched: Boolean = false,
    val trn: String = "",
    val isEditMode: Boolean = false,
    val loadingUser: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val validationError: String? = null,
    val error: String? = null,
    /** Under the mobile box: the client rule or the server's own 422 message. */
    val mobileError: String? = null,
    /** One-shot toast after a failed save; the screen shows it and dismisses it. */
    val saveFailure: String? = null,
)

/**
 * FigmaAddAuthorizedUserViewController: POST /authorized-users in add mode, or
 * PUT /authorized-users/{id} with the same payload in edit mode after prefill.
 */
class AddAuthorizedUserViewModel(
    private val editId: Int?,
    private val repository: More2Repository = More2Repository(),
    // Jamaica unless the device is itself in a +1 region; the profile country,
    // once [profileCountry] answers, wins over both (verifier 2026-09-22).
    defaultPhoneIso: String = AuthorizedUserPhoneInput.defaultIso(
        profileCountryName = null,
        deviceRegion = Locale.getDefault().country,
    ),
    /**
     * The customer's own country (GET /user/profile → address.country). The
     * profile is not cached anywhere, so it is read once when an ADD form
     * opens; null skips the lookup (tests, previews). Edit mode never uses it —
     * the stored row decides the picker there.
     */
    private val profileCountry: (suspend () -> String?)? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AddAuthorizedUserUiState(isEditMode = editId != null, phoneIso = defaultPhoneIso),
    )
    val state: StateFlow<AddAuthorizedUserUiState> = _state
    private var storedPhone: Pair<String, String>? = null

    init {
        if (editId != null) prefill(editId) else openOnProfileCountry()
    }

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v) }
    fun onIdType(v: String) = _state.update { it.copy(idType = v) }
    fun onIdNumber(v: String) = _state.update { it.copy(idNumber = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }

    /**
     * Digits only as the customer types; a "+CC" (or "00CC") typed or pasted
     * in front moves the picker to that country and leaves the national
     * digits: "+44 7911 123456" → 🇬🇧 +44 / 7911123456, "+1 (876) 555-1234" →
     * +1 / 8765551234.
     */
    fun onMobileNumber(v: String) = _state.update {
        val entry = AuthorizedUserPhoneInput.interpret(v, it.phoneIso)
        it.copy(
            mobileNumber = entry.number,
            phoneIso = entry.isoCode,
            mobileError = null,
            phoneTouched = true,
        )
    }

    fun onPhoneCountry(iso: String) = _state.update {
        val calling = AuthorizedUserPhoneInput.country(iso)?.callingCode ?: "+1"
        it.copy(
            phoneIso = iso,
            mobileNumber = AuthorizedUserPhoneInput.renumber(it.mobileNumber, calling),
            mobileError = null,
            phoneTouched = true,
        )
    }

    /**
     * Red when the customer leaves the box, not only on save: Britanya Brown's
     * report (2026-09-14) asks for "submit/blur", and Swift, the website and the
     * phone-width site all check on blur. Never for an empty box, and never for
     * a stored number an edit has not touched (the server keeps it as it is).
     */
    fun onMobileBlur() = _state.update {
        if (!it.phoneTouched || it.mobileNumber.isBlank()) return@update it
        if (unchangedStoredPhone(it) != null) return@update it
        val digits = AuthorizedUserPhoneInput.submissionDigits(it.mobileNumber.trim(), it.callingCode)
        val error = AuthorizedUserPhoneInput.validationError(digits, it.callingCode)
        if (error == null) it else it.copy(mobileError = error)
    }
    fun dismissSaveFailure() = _state.update { it.copy(saveFailure = null) }
    fun onTrn(v: String) = _state.update { it.copy(trn = v) }
    fun dismissValidation() = _state.update { it.copy(validationError = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    /** Moves the picker to the customer's own country, unless they already chose. */
    private fun openOnProfileCountry() {
        val lookup = profileCountry ?: return
        viewModelScope.launch {
            val name = try {
                lookup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null // no profile: the Jamaica / +1-region default stands
            }
            val iso = AuthorizedUserPhoneInput.profileIso(name) ?: return@launch
            _state.update { if (it.phoneTouched) it else it.copy(phoneIso = iso) }
        }
    }

    /** Laravel forUpdate preserves every unchanged phone during other edits. */
    private fun unchangedStoredPhone(state: AddAuthorizedUserUiState): Pair<String, String>? {
        val original = storedPhone ?: return null
        val (iso, digits) = AuthorizedUserPhoneInput.fold(original.first, original.second)
        val code = AuthorizedUserPhoneInput.country(iso)?.callingCode ?: "+1"
        if (code != state.callingCode || digits != state.mobileNumber.trim()) return null
        return original
    }

    private fun prefill(id: Int) {
        _state.update { it.copy(loadingUser = true) }
        viewModelScope.launch {
            repository.authorizedUser(id)
                .onSuccess { user ->
                    // A stored row may carry the area code inside the code
                    // column ("+1876" + 7 digits). Fold it into picker ISO +
                    // digits, the way the website does, so the box shows all
                    // ten digits the server will accept on save.
                    val (phoneIso, mobile) = AuthorizedUserPhoneInput.fold(user.countryCode, user.mobileNumber)
                    storedPhone = user.countryCode.orEmpty() to user.mobileNumber.orEmpty()
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
                            phoneIso = phoneIso,
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
        // Laravel StoreAuthorizedUserRequest: identification_id_number max:14.
        if (idNumber.length > ID_NUMBER_MAX) {
            return fail("Identification Number can be at most $ID_NUMBER_MAX characters")
        }
        if (email.isEmpty()) return fail("Please enter Email Address")
        if (!isValidEmail(email)) return fail("Please enter a valid Email Address")
        // The calling code comes from the picker and the box holds digits only
        // (Kemar 2026-09-15). A bad number is said under the field, not in a
        // dialog: "Please enter a valid phone number."
        val unchangedPhone = unchangedStoredPhone(s)
        val countryCode = unchangedPhone?.first ?: s.callingCode
        val parsedMobile = unchangedPhone?.second ?: mobile
        val validationDigits = AuthorizedUserPhoneInput.submissionDigits(mobile, countryCode)
        val phoneError = if (unchangedPhone == null) AuthorizedUserPhoneInput.validationError(validationDigits, countryCode) else null
        if (phoneError != null) {
            _state.update { it.copy(mobileError = phoneError) }
            return
        }
        if (trn.isEmpty()) return fail("Please enter Tax Registration Number")
        // Laravel: trn_no is `digits:9` — EXACTLY nine numeric digits. This used
        // to be an isEmpty() check only, so "123-456-789" or an 8-digit TRN was
        // sent and came back a bare 422 the customer could not act on. Digits are
        // stripped first (the mobile field above sets that precedent) so a
        // punctuated TRN is accepted rather than rejected.
        val trnDigits = trn.filter(Char::isDigit)
        if (trnDigits.length != TRN_DIGITS) {
            return fail("Tax Registration Number must be $TRN_DIGITS digits")
        }

        val payload = AuthorizedUserRequest(
            userFirstName = firstName,
            userMiddleName = s.middleName.ifEmpty { null },
            userLastName = lastName,
            identificationType = s.idType,
            identificationIdNumber = idNumber,
            userEmail = email,
            userCountryCode = countryCode,
            userMobileNumber = parsedMobile,
            trnNo = trnDigits,
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
                    // A failed add must never be silent (Kemar 2026-09-15), and
                    // it must say what actually failed (verifier 2026-09-22): the
                    // phone sentence only for a 422 that names the phone — whose
                    // own message goes under the field — the connection when
                    // offline, the session on a 401, the server's words for a
                    // JSON client error or another field, else "try again".
                    val parsed = e.parseApiError()
                    val http = e as? HttpException ?: e.cause as? HttpException
                    val phoneError = AuthorizedUserPhoneInput.serverPhoneError(parsed.fieldErrors)
                    val message = AuthorizedUserPhoneInput.saveFailureMessage(
                        isEdit = editId != null,
                        status = http?.code(),
                        offline = http == null && (e is IOException || e.cause is IOException),
                        serverMessage = parsed.message.takeIf { http != null },
                        fieldErrors = parsed.fieldErrors,
                    )
                    _state.update {
                        it.copy(
                            saving = false,
                            mobileError = phoneError ?: it.mobileError,
                            saveFailure = message,
                        )
                    }
                }
        }
    }

    private val AddAuthorizedUserUiState.callingCode: String
        get() = AuthorizedUserPhoneInput.country(phoneIso)?.callingCode ?: "+1"

    private fun isValidEmail(email: String): Boolean =
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
}
