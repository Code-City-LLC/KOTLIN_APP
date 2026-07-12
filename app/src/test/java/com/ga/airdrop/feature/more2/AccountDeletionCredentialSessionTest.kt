package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.model.LoginResponse
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionCredentialSessionTest {

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

    @Test
    fun `credential verification does not replace the logged-in bearer`() = runBlocking {
        AuthTokenStore.save("logged-in-token")
        val repository = More2Repository(verificationApi("verification-only-token"))

        assertTrue(repository.verifyCredentials("kemar@example.com", "secret-password").getOrThrow())
        assertEquals("logged-in-token", AuthTokenStore.token)
    }

    @Suppress("UNCHECKED_CAST")
    private fun verificationApi(returnedToken: String): More2Api =
        Proxy.newProxyInstance(
            More2Api::class.java.classLoader,
            arrayOf(More2Api::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "verifyLogin" -> LoginResponse(token = returnedToken)
                "toString" -> "VerificationMore2Api"
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as More2Api
}
