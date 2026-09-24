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

    // ── 2026-09-23 audit: a "+" number is read the way the server reads it ─────

    private fun freshJamaica(calls: MutableList<AuthorizedUserRequest> = mutableListOf()) = AddAuthorizedUserViewModel(
        editId = null,
        repository = More2Repository(api(http(500, "{}"), calls)),
        defaultPhoneIso = "JM",
    )

    /** Types [text] one key at a time and returns the picker country after every key. */
    private fun AddAuthorizedUserViewModel.pickerWhileTyping(text: String): List<String> =
        text.map { key ->
            onMobileNumber(state.value.mobileNumber + key)
            state.value.phoneIso
        }

    private val AddAuthorizedUserViewModel.shown: Pair<String, String>
        get() = state.value.phoneIso to state.value.mobileNumber

    @Test
    fun `a Caribbean number typed with a plus is never another country and saves as +1`() = runTest(dispatcher) {
        val expected = listOf(
            Triple("+876 555 1234", "JM", "8765551234"), // was refused: no calling code starts 876
            Triple("+658 555 1234", "JM", "6585551234"), // was Singapore from "+65"
            Triple("+868 555 1234", "TT", "8685551234"), // was China from "+86"
            Triple("+441 555 1234", "BM", "4415551234"), // was the UK from "+44"
        )
        for ((typed, iso, number) in expected) {
            val vm = freshJamaica()
            assertEquals("$typed moved the picker while it was typed", listOf("JM"), vm.pickerWhileTyping(typed).distinct())
            vm.onMobileBlur()
            assertNull("$typed is a valid number", vm.state.value.mobileError)
            assertEquals(typed, iso to number, vm.shown)
        }

        // Straight to Save without leaving the box: settled first, then sent.
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = freshJamaica(calls).fillEverythingButThePhone()
        vm.typeIntoMobile("+658 555 1234")
        vm.save()
        advanceUntilIdle()
        assertEquals("+1", calls.single().userCountryCode)
        assertEquals("6585551234", calls.single().userMobileNumber)
        assertEquals("JM" to "6585551234", vm.shown)
    }

    @Test
    fun `a plus code that cannot be Caribbean moves the picker at the key that rules it out`() {
        val uk = freshJamaica().apply { typeIntoMobile("+44") }
        assertEquals("+44 waits: +441 is Bermuda", "JM" to "+44", uk.shown)
        uk.typeIntoMobile(" 7")
        assertEquals("GB" to "7", uk.shown)
        uk.typeIntoMobile("911 123456")
        assertEquals("GB" to "7911123456", uk.shown)

        val singapore = freshJamaica().apply { typeIntoMobile("+65") }
        assertEquals("+65 waits: +658 is Jamaica", "JM" to "+65", singapore.shown)
        singapore.typeIntoMobile(" 9123 4567")
        assertEquals("SG" to "91234567", singapore.shown)

        // Singapore already picked: its own code typed in front stays Singapore's.
        val picked = AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"))),
            defaultPhoneIso = "SG",
        ).apply { typeIntoMobile("+65 8555 1234") }
        assertEquals("SG" to "85551234", picked.shown)

        // Ten digits that start like Bermuda's still could be; an eleventh cannot.
        val london = freshJamaica().apply { typeIntoMobile("+44 1632 9600") }
        assertEquals("JM" to "+4416329600", london.shown)
        london.typeIntoMobile("00")
        london.onMobileBlur()
        assertEquals("GB" to "1632960000", london.shown)
        assertNull(london.state.value.mobileError)
    }

    @Test
    fun `a Brazilian mobile typed after +55 keeps its area code 55`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = freshJamaica(calls).fillEverythingButThePhone()
        vm.typeIntoMobile("+55 55 99123 4567")
        assertEquals("BR" to "55991234567", vm.shown)
        // Choosing Brazil again in the picker leaves the number alone.
        vm.onPhoneCountry("BR")
        assertEquals("BR" to "55991234567", vm.shown)
        vm.onMobileBlur()
        assertNull(vm.state.value.mobileError)
        vm.save()
        advanceUntilIdle()
        // As bare digits the server would cut the 55 as the code typed twice;
        // with the typed "+55" in front it keeps 55991234567, as Swift sends it.
        assertEquals("+55", calls.single().userCountryCode)
        assertEquals("+5555991234567", calls.single().userMobileNumber)
        assertEquals("the box still shows the national number", "55991234567", vm.state.value.mobileNumber)
    }

    @Test
    fun `only a number the server would cut goes with its code in front, and never under +1`() = runTest(dispatcher) {
        fun sent(typed: String, startIso: String = "JM"): Pair<String, String> {
            val calls = mutableListOf<AuthorizedUserRequest>()
            val vm = AddAuthorizedUserViewModel(
                editId = null,
                repository = More2Repository(api(http(500, "{}"), calls)),
                defaultPhoneIso = startIso,
            ).fillEverythingButThePhone()
            vm.typeIntoMobile(typed)
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            return calls.single().let { it.userCountryCode to it.userMobileNumber }
        }
        // The Laravel fixture's German twin of the Brazil case.
        assertEquals("+49" to "+4949211234567", sent("+49 4921 1234567"))
        // A typed code whose number does not start with it again: digits only.
        assertEquals("+44" to "7911123456", sent("+44 7911 123456"))
        assertEquals("+55" to "11991234567", sent("+55 11 99123 4567"))
        assertEquals("+1" to "8765551234", sent("+1 876 555 1234"))
        assertEquals("+1" to "8765551234", sent("+876 555 1234"))
        // No typed code: the digits the box shows.
        assertEquals("+44" to "7911123456", sent("447911123456", startIso = "GB"))
        // The box shows 49112345678 (its trunk 0 dropped once); bare, the server
        // would cut "49" as well, so it goes with the code in front.
        assertEquals("+49" to "+4949112345678", sent("049112345678", startIso = "DE"))
    }

    // ── 2026-09-23 audit: an edit sends back what it did not change ───────────
    // The server keeps a value an edit sends back unchanged, even one its rules
    // would refuse (AuthorizedUserPhone::forUpdate, AuthorizedUserFields::forUpdate),
    // so the device must neither refuse it nor tidy it.

    private fun storedRow(
        countryCode: String? = "+1",
        mobileNumber: String? = "8765551234",
        firstName: String? = "Chase",
        lastName: String? = "Camp",
        identificationType: String? = "National ID",
        idNumber: String? = "194049512",
        email: String? = "chase@example.com",
        trn: String? = "123456789",
    ) = AuthorizedUser(
        id = 7,
        firstName = firstName,
        lastName = lastName,
        identificationType = identificationType,
        identificationIdNumber = idNumber,
        email = email,
        countryCode = countryCode,
        mobileNumber = mobileNumber,
        trnNumber = trn,
    )

    private fun editing(stored: AuthorizedUser, calls: MutableList<AuthorizedUserRequest>) = AddAuthorizedUserViewModel(
        editId = 7,
        repository = More2Repository(api(http(500, "{}"), calls, stored)),
        defaultPhoneIso = "JM",
    )

    @Test
    fun `an edit that leaves a legacy phone alone sends it back exactly as stored`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = editing(storedRow(countryCode = "undefined", mobileNumber = "5551234"), calls)
        advanceUntilIdle()
        vm.onEmail("chase.camp@example.com")
        vm.onMobileBlur()
        assertNull("an untouched stored phone is not judged on blur", vm.state.value.mobileError)
        vm.save()
        advanceUntilIdle()
        assertNull("nor on save", vm.state.value.mobileError)
        val sent = calls.single()
        assertEquals("undefined", sent.userCountryCode)
        assertEquals("5551234", sent.userMobileNumber)
        assertEquals("chase.camp@example.com", sent.userEmail)

        // Once the customer types in the box the phone is theirs, and judged:
        // eight digits are no +1 number.
        vm.typeIntoMobile("5")
        vm.onMobileBlur()
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, vm.state.value.mobileError)
        vm.save()
        advanceUntilIdle()
        assertEquals(AuthorizedUserPhoneInput.FIELD_ERROR, vm.state.value.mobileError)
        assertEquals("nothing more was sent", 1, calls.size)
    }

    @Test
    fun `an untouched stored phone goes back byte for byte, not as the box shows it`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = editing(storedRow(countryCode = "+1", mobileNumber = "876 87 548 50"), calls)
        advanceUntilIdle()
        assertEquals("the box shows it folded", "JM" to "8768754850", vm.shown)
        vm.onLastName("Updated")
        vm.save()
        advanceUntilIdle()
        assertEquals("+1", calls.single().userCountryCode)
        assertEquals("876 87 548 50", calls.single().userMobileNumber)
    }

    @Test
    fun `a changed phone or code on an edit is judged and sent as picker code and digits`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val picked = editing(storedRow(countryCode = "undefined", mobileNumber = "5551234"), calls)
        advanceUntilIdle()
        picked.onPhoneCountry("GB")
        picked.save()
        advanceUntilIdle()
        assertNull(picked.state.value.mobileError)
        assertEquals("+44", calls.single().userCountryCode)
        assertEquals("5551234", calls.single().userMobileNumber)
        calls.clear()

        val retyped = editing(storedRow(countryCode = "undefined", mobileNumber = "5551234"), calls)
        advanceUntilIdle()
        retyped.onMobileNumber("")
        retyped.typeIntoMobile("876 555 123")
        retyped.save()
        advanceUntilIdle()
        assertEquals("nine digits are no +1 number", AuthorizedUserPhoneInput.FIELD_ERROR, retyped.state.value.mobileError)
        assertTrue(calls.isEmpty())
        retyped.typeIntoMobile("4")
        retyped.save()
        advanceUntilIdle()
        assertEquals("+1", calls.single().userCountryCode)
        assertEquals("8765551234", calls.single().userMobileNumber)
    }

    @Test
    fun `a stored phone the customer puts back, or moves to another +1 country, is still kept`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        // Typed away and back: the same digits under the same code.
        val putBack = editing(storedRow(countryCode = "undefined", mobileNumber = "5551234"), calls)
        advanceUntilIdle()
        putBack.typeIntoMobile("5")
        putBack.onMobileNumber("5551234")
        putBack.onMobileBlur()
        assertNull(putBack.state.value.mobileError)
        putBack.save()
        advanceUntilIdle()
        assertEquals("undefined", calls.single().userCountryCode)
        assertEquals("5551234", calls.single().userMobileNumber)
        calls.clear()

        // Another +1 flag is still +1 and the same digits: nothing to judge.
        val otherFlag = editing(storedRow(countryCode = "undefined", mobileNumber = "5551234"), calls)
        advanceUntilIdle()
        otherFlag.onPhoneCountry("US")
        otherFlag.save()
        advanceUntilIdle()
        assertNull(otherFlag.state.value.mobileError)
        assertEquals("undefined", calls.single().userCountryCode)
        assertEquals("5551234", calls.single().userMobileNumber)
    }

    @Test
    fun `an edit sends the fields it did not change exactly as the API sent them`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = editing(storedRow(email = "john doe@example.com", trn = "", identificationType = "other"), calls)
        advanceUntilIdle()
        assertEquals("the stored type is shown, not a default", "other", vm.state.value.idType)
        vm.onFirstName("Chasen")
        vm.save()
        advanceUntilIdle()
        assertNull(vm.state.value.validationError)
        assertNull(vm.state.value.mobileError)
        val sent = calls.single()
        assertEquals("Chasen", sent.userFirstName)
        assertEquals("john doe@example.com", sent.userEmail)
        assertEquals("", sent.trnNo)
        assertEquals("other", sent.identificationType)

        // A changed field is judged as before.
        vm.onEmail("jane doe@example.com")
        vm.save()
        advanceUntilIdle()
        assertEquals("Please enter a valid Email Address", vm.state.value.validationError)
        assertEquals("nothing more was sent", 1, calls.size)
    }

    @Test
    fun `an edit keeps an empty name, an over-long ID number and a missing TRN it did not change`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = editing(
            storedRow(firstName = "", lastName = null, identificationType = null, idNumber = "ABCDEFGHIJKLMNOP", trn = null),
            calls,
        )
        advanceUntilIdle()
        vm.onEmail("chase.camp@example.com")
        vm.save()
        advanceUntilIdle()
        assertNull(vm.state.value.validationError)
        // null from the API goes back as "", which the server reads the same.
        val sent = calls.single()
        assertEquals("", sent.userFirstName)
        assertEquals("", sent.userLastName)
        assertEquals("", sent.identificationType)
        assertEquals("ABCDEFGHIJKLMNOP", sent.identificationIdNumber)
        assertEquals("", sent.trnNo)
        assertEquals("chase.camp@example.com", sent.userEmail)

        // Each one is judged again once it changes, and kept again once it is
        // back to what was stored.
        vm.onIdNumber("ABCDEFGHIJKLMNOPQ")
        vm.save()
        advanceUntilIdle()
        assertEquals("Identification Number can be at most 14 characters", vm.state.value.validationError)
        vm.dismissValidation()
        vm.onIdNumber("ABCDEFGHIJKLMNOP")
        vm.onTrn("12345")
        vm.save()
        advanceUntilIdle()
        assertEquals("Tax Registration Number must be 9 digits", vm.state.value.validationError)
        vm.dismissValidation()
        vm.onTrn("")
        vm.onLastName(" ")
        vm.save()
        advanceUntilIdle()
        assertEquals("Please enter Last Name", vm.state.value.validationError)
        assertEquals("nothing more was sent", 1, calls.size)
    }

    @Test
    fun `an Add still judges every field`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = freshJamaica(calls).fillEverythingButThePhone()
        vm.typeIntoMobile("8765551234")
        vm.onEmail("john doe@example.com")
        vm.save()
        advanceUntilIdle()
        assertEquals("Please enter a valid Email Address", vm.state.value.validationError)
        vm.dismissValidation()
        vm.onEmail("john@example.com")
        vm.onTrn("")
        vm.save()
        advanceUntilIdle()
        assertEquals("Please enter Tax Registration Number", vm.state.value.validationError)
        assertTrue(calls.isEmpty())
    }

    // ── 2026-09-23 display gaps: the box shows the number the server stores ────

    private fun freshForm(iso: String, calls: MutableList<AuthorizedUserRequest> = mutableListOf()) =
        AddAuthorizedUserViewModel(
            editId = null,
            repository = More2Repository(api(http(500, "{}"), calls)),
            defaultPhoneIso = iso,
        ).fillEverythingButThePhone()

    private val List<AuthorizedUserRequest>.sentPhone: Pair<String, String>
        get() = single().let { it.userCountryCode to it.userMobileNumber }

    @Test
    fun `a trunk 0 after the code is dropped once, shown and sent as the server keeps it`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val typed = freshForm("JM", calls)
        typed.typeIntoMobile("+44 07911 123456")
        assertEquals("GB" to "7911123456", typed.shown)
        typed.onMobileBlur()
        assertNull(typed.state.value.mobileError)
        typed.save()
        advanceUntilIdle()
        assertEquals("+44" to "7911123456", calls.sentPhone)

        // The UK already picked, the number written the national way.
        assertEquals("GB" to "7911123456", freshForm("GB").apply { typeIntoMobile("07911 123456") }.shown)
        // The UK picked after the digits.
        val later = freshForm("JM").apply { typeIntoMobile("07911123456") }
        later.onPhoneCountry("GB")
        assertEquals("GB" to "7911123456", later.shown)
        // Only where the server drops it: Italy keeps its 0.
        assertEquals("IT" to "0612345678", freshForm("JM").apply { typeIntoMobile("+39 06 1234 5678") }.shown)
    }

    @Test
    fun `a stored phone with a trunk 0 goes back as stored until its number changes`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val stored = storedRow(countryCode = "+44", mobileNumber = "07700900123")

        // Left alone: shown and sent exactly as stored, never normalized.
        val untouched = editing(stored, calls)
        advanceUntilIdle()
        assertEquals("GB" to "07700900123", untouched.shown)
        untouched.onEmail("chase.camp@example.com")
        untouched.save()
        advanceUntilIdle()
        assertEquals("+44" to "07700900123", calls.sentPhone)
        calls.clear()

        // The UK picked again: nothing is re-read.
        val repicked = editing(stored, calls)
        advanceUntilIdle()
        repicked.onPhoneCountry("GB")
        assertEquals("GB" to "07700900123", repicked.shown)
        repicked.save()
        advanceUntilIdle()
        assertEquals("+44" to "07700900123", calls.sentPhone)
        calls.clear()

        // Away to +1 and back, or typed back the national way: the box drops
        // the 0 as the server would, and it is still the stored number.
        val roundTrip = editing(stored, calls)
        advanceUntilIdle()
        roundTrip.onPhoneCountry("US")
        roundTrip.onPhoneCountry("GB")
        assertEquals("GB" to "7700900123", roundTrip.shown)
        roundTrip.save()
        advanceUntilIdle()
        assertEquals("+44" to "07700900123", calls.sentPhone)
        calls.clear()

        val retyped = editing(stored, calls)
        advanceUntilIdle()
        retyped.onMobileNumber("")
        retyped.typeIntoMobile("07700 900123")
        assertEquals("GB" to "7700900123", retyped.shown)
        retyped.onMobileBlur()
        assertNull(retyped.state.value.mobileError)
        retyped.save()
        advanceUntilIdle()
        assertEquals("+44" to "07700900123", calls.sentPhone)
        calls.clear()

        // Another number is judged and sent as the box shows it.
        val changed = editing(stored, calls)
        advanceUntilIdle()
        changed.onMobileNumber("")
        changed.typeIntoMobile("07700 900124")
        changed.save()
        advanceUntilIdle()
        assertEquals("+44" to "7700900124", calls.sentPhone)
    }

    @Test
    fun `+1 typed from the UK waits for its area code, and 868 is Trinidad and Tobago`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = freshForm("GB", calls)
        assertEquals(listOf("GB", "GB", "GB", "GB", "GB", "TT"), vm.pickerWhileTyping("+1 868"))
        vm.typeIntoMobile(" 555 1234")
        assertEquals("TT" to "8685551234", vm.shown)
        vm.save()
        advanceUntilIdle()
        assertEquals("+1" to "8685551234", calls.sentPhone)

        assertEquals("US" to "2125551234", freshForm("GB").apply { typeIntoMobile("+1 212 555 1234") }.shown)
        // A +1 country already picked stays as it is.
        assertEquals("JM" to "8685551234", freshForm("JM").apply { typeIntoMobile("+1 868 555 1234") }.shown)
    }

    @Test
    fun `a Brazilian number typed without a plus keeps area code 55, and a code typed again still goes`() =
        runTest(dispatcher) {
            fun typedUnderBrazil(typed: String): Triple<String, String, String> {
                val calls = mutableListOf<AuthorizedUserRequest>()
                val vm = freshForm("BR", calls)
                vm.typeIntoMobile(typed)
                vm.save()
                dispatcher.scheduler.advanceUntilIdle()
                val (code, wire) = calls.sentPhone
                return Triple(vm.state.value.mobileNumber, code, wire)
            }
            // Kept whole, and sent so that any server keeps it whole.
            assertEquals(Triple("55991234567", "+55", "+5555991234567"), typedUnderBrazil("55 99123 4567"))
            // The code typed again: cut once.
            assertEquals(Triple("55991234567", "+55", "+5555991234567"), typedUnderBrazil("55 55 99123 4567"))
            assertEquals(Triple("1134567890", "+55", "1134567890"), typedUnderBrazil("55 11 3456 7890"))
        }

    @Test
    fun `a pasted plus behind an invisible mark, in brackets or after tel is read and sent as on the server`() =
        runTest(dispatcher) {
            fun pasted(text: String, iso: String): Triple<String, String, Pair<String, String>> {
                val calls = mutableListOf<AuthorizedUserRequest>()
                val vm = freshForm(iso, calls)
                vm.onMobileNumber(text)
                vm.onMobileBlur()
                assertNull(text, vm.state.value.mobileError)
                vm.save()
                dispatcher.scheduler.advanceUntilIdle()
                return Triple(vm.state.value.phoneIso, vm.state.value.mobileNumber, calls.sentPhone)
            }
            assertEquals(Triple("GB", "7911123456", "+44" to "7911123456"), pasted("\u200E+44 7911 123456", "JM"))
            assertEquals(Triple("GB", "7911123456", "+44" to "7911123456"), pasted("(+44) 7911 123456", "JM"))
            assertEquals(Triple("JM", "8765551234", "+1" to "8765551234"), pasted("tel:+1 876 555 1234", "GB"))
            // A "+" after a digit: the digits, under the picker.
            assertEquals(Triple("JM", "8765551234", "+1" to "8765551234"), pasted("876+5551234", "JM"))
        }

    @Test
    fun `a German number that could be read two ways is refused under the field, and nothing is sent`() =
        runTest(dispatcher) {
            val message = "Please enter a valid phone number. For a German number, start with +49, or with 0 as dialled in Germany."
            val calls = mutableListOf<AuthorizedUserRequest>()
            val vm = freshForm("DE", calls)
            vm.typeIntoMobile("4921 1234567")
            assertEquals("kept as typed", "DE" to "49211234567", vm.shown)
            vm.onMobileBlur()
            assertEquals(message, vm.state.value.mobileError)
            vm.save()
            advanceUntilIdle()
            assertEquals(message, vm.state.value.mobileError)
            assertTrue("nothing may be sent", calls.isEmpty())

            // The four shapes the server reads as they are.
            fun sent(typed: String): Pair<String, String> {
                val sentCalls = mutableListOf<AuthorizedUserRequest>()
                val form = freshForm("DE", sentCalls)
                form.typeIntoMobile(typed)
                form.onMobileBlur()
                assertNull(typed, form.state.value.mobileError)
                form.save()
                dispatcher.scheduler.advanceUntilIdle()
                return sentCalls.sentPhone
            }
            assertEquals("+49" to "+4949211234567", sent("+49 4921 1234567"))
            assertEquals("+49" to "2111234567", sent("0049 211 1234567"))
            assertEquals("+49" to "+4949211234567", sent("04921 1234567"))
            assertEquals("+49" to "3012345678", sent("49 30 12345678"))
        }

    // ── 2026-09-24 audit: what the box remembers belongs to the digits it was for ──

    @Test
    fun `a number pasted over the box is read afresh, not as the national number it replaced`() =
        runTest(dispatcher) {
            // 07911 123456 under 🇬🇧 shows 7911123456, and the dropped trunk 0
            // marks the box as the national number as written. Select all and
            // paste 447911123456 over it: the mark stayed, "44" was kept as
            // part of the number, and +44 447911123456 was stored, which cannot
            // be dialled. Pasted into an empty box it is 7911123456.
            fun pastedOver(iso: String, typed: String, pasted: String): Pair<String, String> {
                val calls = mutableListOf<AuthorizedUserRequest>()
                val vm = freshForm(iso, calls)
                vm.typeIntoMobile(typed)
                vm.onMobileNumber(pasted)
                vm.save()
                dispatcher.scheduler.advanceUntilIdle()
                return calls.sentPhone
            }
            assertEquals("+44" to "7911123456", pastedOver("GB", "07911 123456", "447911123456"))
            // Typed over it key by key after select-all: the first key replaces it.
            val uk = freshForm("GB").apply { typeIntoMobile("07911 123456") }
            uk.onMobileNumber("4")
            uk.typeIntoMobile("47911123456")
            assertEquals("GB" to "7911123456", uk.shown)
            // India's code starts like the mobile it is pasted over (9…), so a
            // shared first digit is no continuation either.
            assertEquals("+91" to "9876543210", pastedOver("IN", "+91 98765 43210", "919876543210"))

            // Germany: 030 1234567, then 49301234567 pasted over it. The server
            // cannot tell Berlin with "+49" typed again from an 0493 number and
            // asks; the stale mark sent +4949301234567 past that question.
            val german = "Please enter a valid phone number. For a German number, start with +49, or with 0 as dialled in Germany."
            val calls = mutableListOf<AuthorizedUserRequest>()
            val de = freshForm("DE", calls)
            de.typeIntoMobile("030 1234567")
            de.onMobileNumber("49301234567")
            de.save()
            advanceUntilIdle()
            assertEquals(german, de.state.value.mobileError)
            assertTrue("nothing may be sent", calls.isEmpty())
        }

    @Test
    fun `typing on, deleting back or correcting a later digit keeps the number as written`() = runTest(dispatcher) {
        fun sentAfter(typed: String, vararg edits: String): Pair<String, String> {
            val calls = mutableListOf<AuthorizedUserRequest>()
            val vm = freshForm("DE", calls)
            vm.typeIntoMobile(typed)
            edits.forEach(vm::onMobileNumber)
            vm.onMobileBlur()
            assertNull("$typed then ${edits.toList()}", vm.state.value.mobileError)
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            return calls.sentPhone
        }
        // Emden, typed after +49: the last digit fixed in place, or deleted and typed again.
        assertEquals("+49" to "+4949211234568", sentAfter("+49 4921 1234567", "49211234568"))
        assertEquals("+49" to "+4949211234568", sentAfter("+49 4921 1234567", "4921123456", "49211234568"))
        // A digit further along fixed with the cursor: deleted, then typed.
        assertEquals("+49" to "+4949219234567", sentAfter("+49 4921 1234567", "4921234567", "49219234567"))
        // Leer, typed the German way with its trunk 0, then one more digit.
        assertEquals("+49" to "+49491123456789", sentAfter("0491 12345678", "491123456789"))
    }

    @Test
    fun `a trunk 0 the box dropped goes back when a country that keeps it is picked`() = runTest(dispatcher) {
        // UK picked, 06 1234 5678 typed: the box drops the 0 and shows
        // 612345678. Then Italy: re-read from 612345678, the app sent
        // +39 612345678, while the server — and the same keys under Italy —
        // keep 0612345678.
        fun picked(typed: String, from: String, vararg to: String): Triple<String, String, Pair<String, String>> {
            val calls = mutableListOf<AuthorizedUserRequest>()
            val vm = freshForm(from, calls)
            vm.typeIntoMobile(typed)
            to.forEach(vm::onPhoneCountry)
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            return Triple(vm.state.value.phoneIso, vm.state.value.mobileNumber, calls.sentPhone)
        }
        assertEquals(Triple("IT", "0612345678", "+39" to "0612345678"), picked("06 1234 5678", "GB", "IT"))
        // Every country whose numbers keep that 0 after the code.
        for ((iso, code) in listOf("CI" to "+225", "SM" to "+378", "VA" to "+39", "GA" to "+241", "CG" to "+242", "BJ" to "+229")) {
            assertEquals(iso, code to "0612345678", picked("06 1234 5678", "GB", iso).third)
        }
        // Dropped after a typed "+44" as well; and through Germany, which drops it too.
        assertEquals("+39" to "0612345678", picked("+44 06 1234 5678", "JM", "IT").third)
        assertEquals(Triple("IT", "0612345678", "+39" to "0612345678"), picked("06 1234 5678", "GB", "DE", "IT"))
        // Back to the UK it goes again.
        assertEquals(Triple("GB", "612345678", "+44" to "612345678"), picked("06 1234 5678", "GB", "IT", "GB"))
        // No 0 typed, none added.
        assertEquals("+39" to "612345678", picked("6 1234 5678", "GB", "IT").third)
        assertEquals("+39" to "612345678", picked("+44 6 1234 5678", "JM", "IT").third)
    }

    @Test
    fun `011 dialled from a +1 country is the exit code, as on the server`() = runTest(dispatcher) {
        // The server reads 011 in front of more than eleven digits under +1 as
        // the NANP exit code (normalize step 1; its own fixture keeps
        // +1 / 0118765551234 as 8765551234). The app refused both.
        fun sent(text: String, pasted: Boolean): Triple<String, String, Pair<String, String>> {
            val calls = mutableListOf<AuthorizedUserRequest>()
            val vm = freshForm("JM", calls)
            if (pasted) vm.onMobileNumber(text) else vm.typeIntoMobile(text)
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(text, vm.state.value.mobileError)
            return Triple(vm.state.value.phoneIso, vm.state.value.mobileNumber, calls.sentPhone)
        }
        for (pasted in listOf(false, true)) {
            assertEquals(Triple("GB", "7911123456", "+44" to "7911123456"), sent("011 44 7911 123456", pasted))
            assertEquals(Triple("JM", "8765551234", "+1" to "8765551234"), sent("011 876 555 1234", pasted))
        }
        // Eleven digits or fewer are no exit code, and under any other code 011 is a trunk 0 and 11…
        val short = freshForm("JM").apply { typeIntoMobile("011 44 791 112") }
        assertEquals("JM" to "01144791112", short.shown)
        assertEquals("GB" to "11447911123456", freshForm("GB").apply { typeIntoMobile("011 44 7911 123456") }.shown)
    }
}
