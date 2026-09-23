package com.ga.airdrop.feature.more2

import com.ga.airdrop.data.model.AuthorizedUserRequest
import java.lang.reflect.Proxy
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The Add Authorized User form, end to end from a typed number to what the
 * customer sees when the server answers 422 (Kemar 2026-09-15: it used to be
 * silent). The API is a recording fake that answers with a real Retrofit
 * HttpException, so RepoSupport's body parsing is under test too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddAuthorizedUserPhoneViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun failingApi(status: Int, body: String, calls: MutableList<AuthorizedUserRequest>): More2Api =
        Proxy.newProxyInstance(
            More2Api::class.java.classLoader,
            arrayOf(More2Api::class.java),
        ) { _, method, args ->
            when (method.name) {
                "addAuthorizedUser" -> {
                    calls += args[0] as AuthorizedUserRequest
                    throw HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))
                }
                "toString" -> "FailingMore2Api"
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as More2Api

    private fun filledForm(api: More2Api): AddAuthorizedUserViewModel =
        AddAuthorizedUserViewModel(editId = null, repository = More2Repository(api), defaultPhoneIso = "JM").apply {
            onFirstName("Jane")
            onLastName("Smith")
            onIdNumber("194049512")
            onEmail("jane.smith@example.com")
            onTrn("123456789")
        }

    @Test
    fun `the report's number is said under the field and nothing is sent`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = filledForm(failingApi(422, "{}", calls))
        vm.onMobileNumber("15550199")
        vm.save()
        advanceUntilIdle()

        assertEquals("Please enter a valid phone number.", vm.state.value.mobileError)
        assertNull("no dialog for the phone — the field says it", vm.state.value.validationError)
        assertEquals("nothing may reach the server", 0, calls.size)
    }

    @Test
    fun `typing keeps digits only and the payload carries the calling code`() = runTest(dispatcher) {
        val calls = mutableListOf<AuthorizedUserRequest>()
        val vm = filledForm(failingApi(500, "{}", calls))
        vm.onMobileNumber("(555) 019-9821")
        assertEquals("5550199821", vm.state.value.mobileNumber)
        vm.save()
        advanceUntilIdle()

        val sent = calls.single()
        assertEquals("+1", sent.userCountryCode)
        assertEquals("5550199821", sent.userMobileNumber)
    }

    @Test
    fun `a 422 about the phone lands under the field and the failed save is announced`() = runTest(dispatcher) {
        val serverMessage = "Enter all 10 digits of the mobile number, for example 876 555 1234."
        val body = """{"message":"$serverMessage","errors":{"user_mobile_number":["$serverMessage"]}}"""
        val vm = filledForm(failingApi(422, body, mutableListOf()))
        vm.onMobileNumber("5550199821")
        vm.save()
        advanceUntilIdle()

        assertEquals(serverMessage, vm.state.value.mobileError)
        assertEquals(AuthorizedUserPhoneInput.ADD_FAILED, vm.state.value.saveFailure)
        assertNull(vm.state.value.error)
        assertEquals(false, vm.state.value.saving)
    }

    @Test
    fun `a 422 about another field is reported in the server's own words`() = runTest(dispatcher) {
        val taken = "This email address is already used by another authorized user on your account."
        val body = """{"message":"$taken","errors":{"user_email":["$taken"]}}"""
        val vm = filledForm(failingApi(422, body, mutableListOf()))
        vm.onMobileNumber("8765551234")
        vm.save()
        advanceUntilIdle()

        assertNull("a duplicate email is not a phone problem", vm.state.value.mobileError)
        assertEquals(taken, vm.state.value.saveFailure)
    }

    @Test
    fun `a server outage still announces itself, without blaming the phone`() = runTest(dispatcher) {
        val vm = filledForm(failingApi(503, "", mutableListOf()))
        vm.onMobileNumber("8765551234")
        vm.save()
        advanceUntilIdle()

        // Verifier 2026-09-22: a 503 used to say "check the phone number".
        assertEquals(AuthorizedUserPhoneInput.ADD_RETRY, vm.state.value.saveFailure)
        assertNull(vm.state.value.mobileError)
    }
}
