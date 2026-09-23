package com.ga.airdrop.feature.more2

import com.ga.airdrop.data.model.AuthorizedUser
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The four follow-ups an independent verifier executed against the first
 * phone fix (PR #242), driven through the ViewModel the way the screen drives
 * it — a TextField hands [AddAuthorizedUserViewModel.onMobileNumber] the whole
 * box after every key — against a recording fake of the API that fails with
 * real Retrofit exceptions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddAuthorizedUserFollowupsTest {

    private val dispatcher = StandardTestDispatcher()
    private val originalLocale: Locale = Locale.getDefault()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(originalLocale)
    }

    /** POST/PUT fail with [failure]; GET /authorized-users/{id} answers [stored] for edit mode. */
    private fun api(
        failure: () -> Throwable,
        calls: MutableList<AuthorizedUserRequest> = mutableListOf(),
        stored: AuthorizedUser? = null,
    ): More2Api = Proxy.newProxyInstance(
        More2Api::class.java.classLoader,
        arrayOf(More2Api::class.java),
    ) { _, method, args ->
        when (method.name) {
            "addAuthorizedUser" -> {
                calls += args[0] as AuthorizedUserRequest
                throw failure()
            }
            "updateAuthorizedUser" -> {
                calls += args[1] as AuthorizedUserRequest
                throw failure()
            }
            "authorizedUser" -> AuthorizedUserEnvelope(stored)
            "toString" -> "FollowupsMore2Api"
            else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
        }
    } as More2Api

    private fun http(status: Int, body: String, type: String = "application/json"): () -> Throwable = {
        HttpException(Response.error<Any>(status, body.toResponseBody(type.toMediaType())))
    }

    private fun AddAuthorizedUserViewModel.fillEverythingButThePhone() = apply {
        onFirstName("Jane")
        onLastName("Smith")
        onIdNumber("194049512")
        onEmail("jane.smith@example.com")
        onTrn("123456789")
    }

    /** What a TextField does: after every key it hands over the whole box. */
    private fun AddAuthorizedUserViewModel.typeIntoMobile(text: String) {
        text.forEach { key -> onMobileNumber(state.value.mobileNumber + key) }
    }

    // ── Bug 1: the picker opened on the device region, not Jamaica ─────────────

    @Test
    fun `a UK or Spanish phone still opens the picker on Jamaica`() {
        for (tag in listOf("en-GB", "es-ES", "fr-FR", "de-DE")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val vm = AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api(http(500, "{}"))))
            assertEquals("device $tag is not a +1 region", "JM", vm.state.value.phoneIso)
        }
    }

    @Test
    fun `a +1 device region may still choose the +1 country it names`() {
        Locale.setDefault(Locale.US)
        assertEquals("US", AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api(http(500, "{}")))).state.value.phoneIso)
        Locale.setDefault(Locale.CANADA)
        assertEquals("CA", AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api(http(500, "{}")))).state.value.phoneIso)
    }

    @Test
    fun `the report's inputs on a UK phone are never saved as +44 numbers`() = runTest(dispatcher) {
        Locale.setDefault(Locale.UK)
        val calls = mutableListOf<AuthorizedUserRequest>()

        val short = AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api(http(500, "{}"), calls)))
            .fillEverythingButThePhone()
        short.typeIntoMobile("15550199")
        short.save()
        advanceUntilIdle()
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, short.state.value.mobileError)
        assertTrue("an 8-digit number is not a +1 number and must not be sent", calls.isEmpty())

        val formatted = AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api(http(500, "{}"), calls)))
            .fillEverythingButThePhone()
        formatted.typeIntoMobile("(555) 019-9821")
        formatted.save()
        advanceUntilIdle()
        val sent = calls.single()
        assertEquals("+1", sent.userCountryCode)
        assertEquals("5550199821", sent.userMobileNumber)
    }

    @Test
    fun `the picker moves to the customer's profile country once the profile answers`() = runTest(dispatcher) {
        Locale.setDefault(Locale.UK)
        val british = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            profileCountry = { "United Kingdom" },
        )
        assertEquals("before the profile answers", "JM", british.state.value.phoneIso)
        advanceUntilIdle()
        assertEquals("GB", british.state.value.phoneIso)

        Locale.setDefault(Locale.US)
        val jamaican = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            profileCountry = { "Jamaica" },
        )
        advanceUntilIdle()
        assertEquals("the profile beats a +1 device region", "JM", jamaican.state.value.phoneIso)
    }

    @Test
    fun `a late profile never moves a picker the customer already used`() = runTest(dispatcher) {
        val typed = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            defaultPhoneIso = "JM",
            profileCountry = { "United Kingdom" },
        )
        typed.onMobileNumber("8765551234")
        advanceUntilIdle()
        assertEquals("JM", typed.state.value.phoneIso)
        assertEquals("8765551234", typed.state.value.mobileNumber)

        val picked = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            defaultPhoneIso = "JM",
            profileCountry = { "United Kingdom" },
        )
        picked.onPhoneCountry("CA")
        advanceUntilIdle()
        assertEquals("CA", picked.state.value.phoneIso)
    }

    @Test
    fun `no profile country, an unknown one, or a failed lookup keeps Jamaica`() = runTest(dispatcher) {
        val lookups = listOf<suspend () -> String?>(
            { null },
            { "Atlantis" },
            { throw IOException("Unable to resolve host") },
        )
        for (lookup in lookups) {
            val vm = AddAuthorizedUserViewModel(
                editId = null,
                repository = More2Repository(api(http(500, "{}"))),
                defaultPhoneIso = "JM",
                profileCountry = lookup,
            )
            advanceUntilIdle()
            assertEquals("JM", vm.state.value.phoneIso)
        }
    }

    @Test
    fun `edit mode never asks the profile - the stored row decides the picker`() = runTest(dispatcher) {
        var asked = 0
        val stored = AuthorizedUser(
            id = 7,
            firstName = "Chase",
            lastName = "Camp",
            identificationType = "National ID",
            identificationIdNumber = "194049512",
            email = "chase@example.com",
            countryCode = "+44",
            mobileNumber = "7911123456",
            trnNumber = "123456789",
        )
        val vm = AddAuthorizedUserViewModel(
            editId = 7,
            repository = More2Repository(api(http(500, "{}"), stored = stored)),
            defaultPhoneIso = "JM",
            profileCountry = { asked++; "Jamaica" },
        )
        advanceUntilIdle()
        assertEquals(0, asked)
        assertEquals("GB", vm.state.value.phoneIso)
        assertEquals("7911123456", vm.state.value.mobileNumber)
    }

    // ── Bug 2: a "+CC" typed one key at a time stayed inside the number ────────

    @Test
    fun `+44 typed key by key under the UK keeps only the national digits`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"), calls)),
            defaultPhoneIso = "GB",
        ).fillEverythingButThePhone()

        vm.typeIntoMobile("+44 7911 123456")
        assertEquals("GB", vm.state.value.phoneIso)
        assertEquals("7911123456", vm.state.value.mobileNumber)

        vm.save()
        advanceUntilIdle()
        assertEquals("+44", calls.single().userCountryCode)
        assertEquals("7911123456", calls.single().userMobileNumber)
    }

    @Test
    fun `a typed or pasted +CC switches the picker to that country`() {
        fun freshJamaica() = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            defaultPhoneIso = "JM",
        )

        val typed = freshJamaica().apply { typeIntoMobile("+44 7911 123456") }
        assertEquals("GB", typed.state.value.phoneIso)
        assertEquals("7911123456", typed.state.value.mobileNumber)

        val pasted = freshJamaica().apply { onMobileNumber("+44 7911 123456") }
        assertEquals("GB", pasted.state.value.phoneIso)
        assertEquals("7911123456", pasted.state.value.mobileNumber)

        val doubleZero = freshJamaica().apply { typeIntoMobile("0044 7911 123456") }
        assertEquals("GB", doubleZero.state.value.phoneIso)
        assertEquals("7911123456", doubleZero.state.value.mobileNumber)

        val ireland = freshJamaica().apply { typeIntoMobile("+353 87 123 4567") }
        assertEquals("IE", ireland.state.value.phoneIso)
        assertEquals("871234567", ireland.state.value.mobileNumber)
    }

    @Test
    fun `the selected country's own code typed in front of a full number is dropped`() {
        val uk = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            defaultPhoneIso = "GB",
        )
        uk.typeIntoMobile("447911123456")
        assertEquals("GB", uk.state.value.phoneIso)
        assertEquals("7911123456", uk.state.value.mobileNumber)
    }

    @Test
    fun `picking the country after typing its full number drops the repeated code`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"), calls)),
            defaultPhoneIso = "JM",
        ).fillEverythingButThePhone()
        vm.typeIntoMobile("447911123456")
        vm.onPhoneCountry("GB")
        assertEquals("7911123456", vm.state.value.mobileNumber)
        vm.save()
        advanceUntilIdle()
        assertEquals("+44", calls.single().userCountryCode)
        assertEquals("7911123456", calls.single().userMobileNumber)
    }

    @Test
    fun `an unfinished +CC is refused under the field and nothing is sent`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"), calls)),
            defaultPhoneIso = "JM",
        ).fillEverythingButThePhone()
        vm.typeIntoMobile("+99")
        assertEquals("+99", vm.state.value.mobileNumber)
        vm.save()
        advanceUntilIdle()
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, vm.state.value.mobileError)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `+1 keeps working typed, pasted, and with the trunk 1`() {
        fun fresh(iso: String) = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            defaultPhoneIso = iso,
        )

        val typedFromUk = fresh("GB").apply { typeIntoMobile("+1 876 555 1234") }
        assertEquals("JM", typedFromUk.state.value.phoneIso)
        assertEquals("8765551234", typedFromUk.state.value.mobileNumber)

        val pastedFromUk = fresh("GB").apply { onMobileNumber("+1 (876) 555-1234") }
        assertEquals("JM", pastedFromUk.state.value.phoneIso)
        assertEquals("8765551234", pastedFromUk.state.value.mobileNumber)

        val pastedUnderUs = fresh("US").apply { onMobileNumber("+1 (555) 019-9821") }
        assertEquals("US", pastedUnderUs.state.value.phoneIso)
        assertEquals("5550199821", pastedUnderUs.state.value.mobileNumber)

        val trunk = fresh("JM").apply { typeIntoMobile("1 876 555 1234") }
        assertEquals("JM", trunk.state.value.phoneIso)
        assertEquals("8765551234", trunk.state.value.mobileNumber)
    }

    // ── Bug 3: every failure blamed the phone ───────────────────────────────────

    private fun failedAddMessage(failure: () -> Throwable): Pair<String?, String?> {
        val vm = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(failure)),
            defaultPhoneIso = "JM",
        ).fillEverythingButThePhone()
        vm.onMobileNumber("8765551234")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.saving)
        return vm.state.value.saveFailure to vm.state.value.mobileError
    }

    @Test
    fun `only a 422 that names the phone blames the phone`() {
        val phone = "Please enter a valid phone number. Enter all 10 digits, for example 876 555 1234."
        val (toast, field) = failedAddMessage(
            http(422, """{"success":false,"message":"$phone","errors":{"user_mobile_number":["$phone"]}}"""),
        )
        assertEquals("Failed to add authorized user. Please check the phone number and try again.", toast)
        assertEquals(phone, field)

        val code = "Please enter a valid phone number. Choose a country code from the list."
        val (codeToast, codeField) = failedAddMessage(
            http(422, """{"success":false,"message":"$code","errors":{"user_country_code":["$code"]}}"""),
        )
        assertEquals("Failed to add authorized user. Please check the phone number and try again.", codeToast)
        assertEquals(code, codeField)
    }

    @Test
    fun `an offline save says to check the connection`() {
        val (toast, field) = failedAddMessage { IOException("Unable to resolve host \"airdropja.com\"") }
        assertEquals("Failed to add authorized user. Check your connection and try again.", toast)
        assertNull(field)
    }

    @Test
    fun `a 401 says the session expired`() {
        val (toast, field) = failedAddMessage(http(401, """{"message":"Unauthenticated."}"""))
        assertEquals("Your session has expired. Please sign in again.", toast)
        assertNull(field)
    }

    @Test
    fun `a server failure asks to try again and never shows the phone sentence or HTML`() {
        val generic = "Failed to add authorized user. Please try again."
        assertEquals(generic, failedAddMessage(http(500, """{"success":false,"message":"Server Error"}""")).first)
        assertEquals(generic, failedAddMessage(http(500, "")).first)
        assertEquals(generic, failedAddMessage(http(503, "{}")).first)
        val html = "<!DOCTYPE html><html><head><title>502 Bad Gateway</title></head><body>nginx</body></html>"
        val (htmlToast, htmlField) = failedAddMessage(http(502, html, "text/html"))
        assertEquals(generic, htmlToast)
        assertNull(htmlField)
    }

    @Test
    fun `a JSON client error is reported in the server's own words`() {
        assertEquals("Too Many Attempts.", failedAddMessage(http(429, """{"message":"Too Many Attempts."}""")).first)
        val taken = "This email address is already used by another authorized user on your account."
        val (toast, field) = failedAddMessage(
            http(422, """{"success":false,"message":"$taken","errors":{"user_email":["$taken"]}}"""),
        )
        assertEquals(taken, toast)
        assertNull("a duplicate email is not a phone problem", field)
    }

    @Test
    fun `edit mode names the update, not the add`() {
        fun failedUpdate(failure: () -> Throwable): String? {
            val stored = AuthorizedUser(
                id = 7,
                firstName = "Chase",
                lastName = "Camp",
                identificationType = "National ID",
                identificationIdNumber = "194049512",
                email = "chase@example.com",
                countryCode = "+1",
                mobileNumber = "8765551234",
                trnNumber = "123456789",
            )
            val vm = AddAuthorizedUserViewModel(
                editId = 7,
                repository = More2Repository(api(failure, stored = stored)),
                defaultPhoneIso = "JM",
            )
            dispatcher.scheduler.advanceUntilIdle()
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            return vm.state.value.saveFailure
        }
        assertEquals("Failed to update authorized user. Please try again.", failedUpdate(http(500, "")))
        assertEquals(
            "Failed to update authorized user. Check your connection and try again.",
            failedUpdate { IOException("timeout") },
        )
        assertEquals("Your session has expired. Please sign in again.", failedUpdate(http(401, """{"message":"Unauthenticated."}""")))
        val phone = "Please enter a valid phone number."
        assertEquals(
            "Failed to update authorized user. Please check the phone number and try again.",
            failedUpdate(http(422, """{"message":"$phone","errors":{"user_mobile_number":["$phone"]}}""")),
        )
    }

    // ── Bug 4: territories with a real calling code were missing ────────────────

    @Test
    fun `territories the catalog lists without a dial code are in the picker`() {
        val expected = mapOf(
            "GG" to "+44", "IM" to "+44", "JE" to "+44", "SS" to "+211", "XK" to "+383",
            "CW" to "+599", "SX" to "+1", "BQ" to "+599", "PS" to "+970", "AX" to "+358",
            "MF" to "+590", "BL" to "+590", "SH" to "+290",
        )
        // The catalog is every region the runtime lists: Android lists Kosovo
        // (XK), the desktop JVM does not. Check each one this runtime has.
        val listed = Locale.getISOCountries().toSet()
        for ((iso, calling) in expected) {
            if (iso !in listed) continue
            assertEquals("$iso calling code", calling, AuthorizedUserPhoneInput.country(iso)?.callingCode)
        }
        assertTrue(AuthorizedUserPhoneInput.search("jersey").any { it.isoCode == "JE" })
        assertTrue(AuthorizedUserPhoneInput.search("+599").map { it.isoCode }.containsAll(listOf("CW", "BQ")))
    }

    // ── Red on blur, not only on save (Britanya Brown 2026-09-14) ──────────────
    // Swift (validatePhoneOnBlur), the website and the phone-width site already
    // judge the number when the customer leaves the box; Android only did on save.

    private fun freshForm() = AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api(http(500, "{}"))))

    @Test
    fun `leaving a short +1 number says why under the field`() {
        Locale.setDefault(Locale.forLanguageTag("en-JM"))
        val vm = freshForm()
        vm.typeIntoMobile("555")
        assertNull("typing never nags", vm.state.value.mobileError)
        vm.onMobileBlur()
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, vm.state.value.mobileError)
    }

    @Test
    fun `leaving an empty or untouched box says nothing`() {
        val vm = freshForm()
        vm.onMobileBlur()
        assertNull("an untouched box is not judged", vm.state.value.mobileError)
        vm.typeIntoMobile("8")
        vm.onMobileNumber("")
        vm.onMobileBlur()
        assertNull("an emptied box is not judged", vm.state.value.mobileError)
    }

    @Test
    fun `a valid number passes on blur and typing clears a blur error`() {
        Locale.setDefault(Locale.forLanguageTag("en-JM"))
        val vm = freshForm()
        vm.typeIntoMobile("8765551234")
        vm.onMobileBlur()
        assertNull(vm.state.value.mobileError)
        vm.onMobileNumber("876")
        vm.onMobileBlur()
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, vm.state.value.mobileError)
        vm.typeIntoMobile("5551234")
        assertNull("the next key clears it", vm.state.value.mobileError)
        assertEquals("8765551234", vm.state.value.mobileNumber)
    }
}

