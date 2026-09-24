package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.parseApiError
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.AuthorizedUser
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
     * (Kemar 2026-09-15). The one exception is a "+CC" still being read ("+4",
     * "+876555"): it stays until it names a country, then moves into the
     * picker — on blur and Save at the latest.
     */
    val mobileNumber: String = "",
    val phoneIso: String = AuthorizedUserPhoneInput.DEFAULT_ISO,
    /**
     * The box is the national number as written (after a typed "+CC" or a
     * dropped trunk 0); see [AuthorizedUserPhoneEntry.explicitCode].
     */
    val phoneExplicitCode: Boolean = false,
    /**
     * The box dropped a trunk 0 in front of its digits; a newly picked code
     * re-reads them with it ([AuthorizedUserPhoneEntry.droppedTrunkZero]).
     */
    val phoneDroppedTrunkZero: Boolean = false,
    /**
     * The customer typed a number or picked a code. The profile default must
     * not move the picker under them, and an edit's stored phone is judged and
     * replaced only once this is true and the number differs from the stored one.
     */
    val phoneTouched: Boolean = false,
    /**
     * Edit mode: the row exactly as GET /authorized-users/{id} returned it,
     * legacy values included ("undefined" / "5551234", "john doe@gmail.com",
     * an empty TRN). A field the customer leaves alone goes back as this, not
     * as the form shows it, and is not judged: the server keeps an unchanged
     * value its rules would refuse (App\Support\AuthorizedUserPhone::forUpdate,
     * AuthorizedUserFields::forUpdate). null on an Add, or before the row loads.
     */
    val openedWith: AuthorizedUser? = null,
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
     * +1 / 8765551234. A Caribbean number written with a "+" ("+876 555
     * 1234") stays as typed until it is complete; see
     * [AuthorizedUserPhoneInput.interpret], which reads the edit against the
     * box before it.
     */
    fun onMobileNumber(v: String) = _state.update {
        val entry = AuthorizedUserPhoneInput.interpret(v, it.phoneEntry)
        it.copy(
            mobileNumber = entry.number,
            phoneIso = entry.isoCode,
            phoneExplicitCode = entry.explicitCode,
            phoneDroppedTrunkZero = entry.droppedTrunkZero,
            mobileError = null,
            phoneTouched = true,
        )
    }

    fun onPhoneCountry(iso: String) = _state.update {
        val calling = AuthorizedUserPhoneInput.country(iso)?.callingCode ?: "+1"
        // A country with the same calling code (Brazil again, or another +1
        // flag) leaves the box as it is: digits typed after a "+CC" stay whole,
        // and a stored phone is not re-read. A new code re-reads the digits,
        // with the trunk 0 the box dropped put back in front of them.
        val sameCode = calling == it.callingCode
        val (box, asWritten) = if (sameCode) {
            it.mobileNumber to it.phoneExplicitCode
        } else {
            AuthorizedUserPhoneInput.renumbered(it.mobileNumber, calling, it.phoneDroppedTrunkZero)
        }
        it.copy(
            phoneIso = iso,
            mobileNumber = box,
            phoneExplicitCode = asWritten,
            // Under a new code, only a 0 dropped by that code is remembered.
            phoneDroppedTrunkZero = if (sameCode) it.phoneDroppedTrunkZero else asWritten,
            mobileError = null,
            phoneTouched = true,
        )
    }

    /**
     * Red when the customer leaves the box, not only on save: Britanya Brown's
     * report (2026-09-14) asks for "submit/blur", and Swift, the website and the
     * phone-width site all check on blur. A number still being read is settled
     * first ("+876 555 1234" → 🇯🇲 8765551234). Never for an empty box, and
     * never for a stored number an edit has not touched (the server keeps it
     * as it is).
     */
    fun onMobileBlur() = _state.update {
        if (!it.phoneTouched) return@update it
        val settled = it.settledPhone()
        if (settled.mobileNumber.isBlank() || unchangedStoredPhone(settled) != null) return@update settled
        val box = settled.mobileNumber.trim()
        val digits = AuthorizedUserPhoneInput.submissionDigits(box, settled.callingCode)
        // A number the server cannot read either way asks, in its words (v1.29).
        val error = AuthorizedUserPhoneInput.ambiguityError(box, settled.callingCode, settled.phoneExplicitCode)
            ?: AuthorizedUserPhoneInput.validationError(digits, settled.callingCode)
        if (error == null) settled else settled.copy(mobileError = error)
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

    /**
     * Laravel forUpdate preserves every unchanged phone during other edits.
     * An edit's stored phone is untouched while neither the box nor the
     * picker has changed, or while it still shows what the form opened with
     * (the same calling code and digits: a number typed and put back, or
     * another +1 country — its one trunk 0 aside, which the box now drops
     * as the server would: 07700900123 typed back as 7700900123 under 🇬🇧 is
     * still the stored number). Untouched, it is not judged and goes back
     * exactly as the API sent it — the row returned here; null when it must
     * be judged.
     */
    private fun unchangedStoredPhone(state: AddAuthorizedUserUiState): AuthorizedUser? {
        val original = state.openedWith ?: return null
        if (!state.phoneTouched) return original
        val (iso, digits) = AuthorizedUserPhoneInput.fold(original.countryCode, original.mobileNumber)
        val code = AuthorizedUserPhoneInput.country(iso)?.callingCode ?: "+1"
        if (code != state.callingCode) return null
        val shown = state.mobileNumber.trim()
        if (shown != digits && shown != AuthorizedUserPhoneInput.submissionDigits(digits, code)) return null
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
                    _state.update {
                        it.copy(
                            loadingUser = false,
                            firstName = user.firstName.orEmpty(),
                            middleName = user.middleName.orEmpty(),
                            lastName = user.lastName.orEmpty(),
                            // The type as stored, even one the list does not
                            // offer ("other"): showing "National ID" for it
                            // sent "National ID" over it without the customer
                            // choosing it. The API already maps the old
                            // spellings ("national_id") to the list's labels.
                            idType = user.identificationType.orEmpty(),
                            idNumber = user.identificationIdNumber.orEmpty(),
                            email = user.email.orEmpty(),
                            mobileNumber = mobile,
                            phoneIso = phoneIso,
                            phoneExplicitCode = false,
                            phoneDroppedTrunkZero = false,
                            // The box holds the stored phone: untouched until
                            // the customer types in it or picks a code.
                            phoneTouched = false,
                            mobileError = null,
                            trn = user.trnNumber.orEmpty(),
                            openedWith = user,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingUser = false, error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Mirror RN AddAuthorizedUserView validation 1:1, then POST/PUT.
     *
     * On an edit, a field the customer left alone is not judged and goes back
     * exactly as the API sent it ([AddAuthorizedUserUiState.openedWith]): the
     * phone as [unchangedStoredPhone] decides, every other field while it
     * still holds the value the form opened with. The server keeps
     * such a value even where its rules would refuse it (2026-09-23 audit: an
     * edit of a row saved with "undefined" / "5551234", an empty TRN or
     * "john doe@gmail.com" was refused on the device before it was sent). A
     * changed field, and every field of an Add, is judged as before.
     */
    fun save() {
        if (_state.value.saving) return
        // A number still being read ("+876 555 1234") is settled first, so the
        // box shows what is judged and sent.
        _state.update { if (it.phoneTouched) it.settledPhone() else it }
        val s = _state.value
        val opened = s.openedWith

        /** What the API sent for a field that still holds it; null on an Add, or once changed. */
        fun kept(sent: String?, current: String): String? =
            if (opened != null && current == sent.orEmpty()) sent.orEmpty() else null

        // null from the API goes back as "", which the server reads the same.
        val keptFirstName = kept(opened?.firstName, s.firstName)
        val keptLastName = kept(opened?.lastName, s.lastName)
        val keptIdType = kept(opened?.identificationType, s.idType)
        val keptIdNumber = kept(opened?.identificationIdNumber, s.idNumber)
        val keptEmail = kept(opened?.email, s.email)
        val keptTrn = kept(opened?.trnNumber, s.trn)
        val keptPhone = unchangedStoredPhone(s)

        val firstName = s.firstName.trim()
        val lastName = s.lastName.trim()
        val idNumber = s.idNumber.trim()
        val email = s.email.trim()
        val trn = s.trn.trim()

        fun fail(message: String) = _state.update { it.copy(validationError = message) }

        if (keptFirstName == null && firstName.isEmpty()) return fail("Please enter First Name")
        if (keptLastName == null && lastName.isEmpty()) return fail("Please enter Last Name")
        if (keptIdNumber == null) {
            if (idNumber.isEmpty()) return fail("Please enter Identification Number")
            // Laravel StoreAuthorizedUserRequest: identification_id_number max:14.
            if (idNumber.length > ID_NUMBER_MAX) {
                return fail("Identification Number can be at most $ID_NUMBER_MAX characters")
            }
        }
        if (keptEmail == null) {
            if (email.isEmpty()) return fail("Please enter Email Address")
            if (!isValidEmail(email)) return fail("Please enter a valid Email Address")
        }
        // The calling code comes from the picker and the box holds digits only
        // (Kemar 2026-09-15). A bad number is said under the field, not in a
        // dialog: "Please enter a valid phone number."
        val countryCode: String
        val mobile: String
        if (keptPhone != null) {
            countryCode = keptPhone.countryCode.orEmpty()
            mobile = keptPhone.mobileNumber.orEmpty()
        } else {
            countryCode = s.callingCode
            val digits = s.mobileNumber.trim()
            // The trunk 0 the server drops is left out of the check, never
            // out of the request: Laravel normalizes the wire value once.
            val validationDigits = AuthorizedUserPhoneInput.submissionDigits(digits, countryCode)
            // A number the server cannot read either way (German 4921 1234567
            // typed without "+" or 0) is refused here, in the server's words.
            val phoneError = AuthorizedUserPhoneInput.ambiguityError(digits, countryCode, s.phoneExplicitCode)
                ?: AuthorizedUserPhoneInput.validationError(validationDigits, countryCode)
            if (phoneError != null) {
                _state.update { it.copy(mobileError = phoneError) }
                return
            }
            // Digits only, unless the server needs "+CC" in front to keep them whole.
            mobile = AuthorizedUserPhoneInput.requestNumber(digits, countryCode)
        }
        // Laravel: trn_no is `digits:9` — EXACTLY nine numeric digits. This used
        // to be an isEmpty() check only, so "123-456-789" or an 8-digit TRN was
        // sent and came back a bare 422 the customer could not act on. Digits are
        // stripped first (the mobile field above sets that precedent) so a
        // punctuated TRN is accepted rather than rejected.
        val trnDigits = trn.filter(Char::isDigit)
        if (keptTrn == null) {
            if (trn.isEmpty()) return fail("Please enter Tax Registration Number")
            if (trnDigits.length != TRN_DIGITS) {
                return fail("Tax Registration Number must be $TRN_DIGITS digits")
            }
        }

        val payload = AuthorizedUserRequest(
            userFirstName = keptFirstName ?: firstName,
            userMiddleName = s.middleName.ifEmpty { null },
            userLastName = keptLastName ?: lastName,
            identificationType = keptIdType ?: s.idType,
            identificationIdNumber = keptIdNumber ?: idNumber,
            userEmail = keptEmail ?: email,
            userCountryCode = countryCode,
            userMobileNumber = mobile,
            trnNo = keptTrn ?: trnDigits,
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

    /** The picker and the box as one entry, flags included. */
    private val AddAuthorizedUserUiState.phoneEntry: AuthorizedUserPhoneEntry
        get() = AuthorizedUserPhoneEntry(phoneIso, mobileNumber, phoneExplicitCode, phoneDroppedTrunkZero)

    /** The box read to the end ([AuthorizedUserPhoneInput.resolve]): "+876 555 1234" → 🇯🇲 8765551234. */
    private fun AddAuthorizedUserUiState.settledPhone(): AddAuthorizedUserUiState {
        val entry = AuthorizedUserPhoneInput.resolve(phoneEntry)
        return copy(
            mobileNumber = entry.number,
            phoneIso = entry.isoCode,
            phoneExplicitCode = entry.explicitCode,
            phoneDroppedTrunkZero = entry.droppedTrunkZero,
        )
    }

    private fun isValidEmail(email: String): Boolean =
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
}
