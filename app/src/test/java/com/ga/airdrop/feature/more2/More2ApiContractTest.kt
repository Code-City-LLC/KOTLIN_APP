package com.ga.airdrop.feature.more2

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.POST

class More2ApiContractTest {

    @Test
    fun verifyLoginUsesSwiftAuthLoginEndpoint() {
        val method = More2Api::class.java.declaredMethods.single { it.name == "verifyLogin" }
        val post = method.getAnnotation(POST::class.java)

        assertEquals(
            "Swift verifyAccountCredentials calls APIEndpoints.login, which is /auth/login.",
            "auth/login",
            post?.value,
        )
    }
}
