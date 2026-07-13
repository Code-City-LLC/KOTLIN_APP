package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.SignUpRequest
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRepositoryRegistrationSessionTest {

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

    @Test
    fun `token-bearing registration response leaves the client signed out`() = runBlocking {
        AuthTokenStore.save("stale-prior-token")
        val repository = AuthRepository(registrationService("register-response-token"))

        val response = repository.signUp(request()).getOrThrow()

        assertEquals("register-response-token", response.token)
        assertNull(AuthTokenStore.token)
    }

    @Test
    fun `reactivation binds the authoritative response account id`() = runBlocking {
        val repository = AuthRepository(reactivationService())

        repository.reactivateAccount(
            email = "kemar@example.com",
            password = "password123",
            passwordConfirmation = "password123",
        ).getOrThrow()

        assertEquals("reactivated-token", AuthTokenStore.token)
        assertEquals(101, AuthTokenStore.snapshot().accountId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun registrationService(token: String): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "register" -> LoginResponse(token = token)
                "toString" -> "RegistrationService"
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as AirdropApiService

    @Suppress("UNCHECKED_CAST")
    private fun reactivationService(): AirdropApiService =
        Proxy.newProxyInstance(
            AirdropApiService::class.java.classLoader,
            arrayOf(AirdropApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "reactivateAccount" -> LoginResponse(
                    token = "reactivated-token",
                    user = AirdropUser(id = 101),
                )
                "toString" -> "ReactivationService"
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as AirdropApiService

    private fun request() = SignUpRequest(
        firstName = "Kemar",
        lastName = "Campbell",
        email = "kemar@example.com",
        password = "password123",
        confirmPassword = "password123",
        userCountryCode = "1",
        userMobile = "8765550100",
        userAddressLine1 = "1 Main Street",
        userAddressCity = "Kingston",
        userAddressState = "Kingston",
        userAddressCountry = "Jamaica",
        userHearType = "Friend",
        userPickupLocation = "Kingston",
        userTnc = true,
        userAuth = true,
    )
}
