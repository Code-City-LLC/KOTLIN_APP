package com.ga.airdrop.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthInterceptorParityTest {

    @Test
    fun noAuthRequestSendsNoBearerAndDoesNotClearTokenOn401() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.save("still-valid")
        val request = Request.Builder()
            .url("https://example.com/api/v1/faqs")
            .header(AuthInterceptor.NO_AUTH_HEADER, "true")
            .build()
        val chain = RecordingChain(request, responseCode = 401)

        AuthInterceptor().intercept(chain)

        val sent = chain.proceededRequest
        assertNull(sent.header(AuthInterceptor.NO_AUTH_HEADER))
        assertNull(sent.header("Authorization"))
        assertEquals("application/json", sent.header("Accept"))
        assertEquals(
            "Swift public FAQ/CMS calls use requireAuth=false, so their 401 must not sweep auth",
            "still-valid",
            AuthTokenStore.token,
        )
        AuthTokenStore.clear()
    }

    @Test
    fun authenticatedRequestStillAttachesBearerAndClearsMatchingTokenOn401() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.save("stale-token")
        val request = Request.Builder()
            .url("https://example.com/api/v1/user/profile")
            .build()
        val chain = RecordingChain(request, responseCode = 401)

        AuthInterceptor().intercept(chain)

        assertEquals("Bearer stale-token", chain.proceededRequest.header("Authorization"))
        assertEquals("application/json", chain.proceededRequest.header("Accept"))
        assertNull(AuthTokenStore.token)
    }

    private class RecordingChain(
        private val original: Request,
        private val responseCode: Int,
    ) : Interceptor.Chain {
        lateinit var proceededRequest: Request

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode == 401) "Unauthorized" else "OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = error("Call is not used by AuthInterceptor")
        override fun connectTimeoutMillis(): Int = 0
        override fun readTimeoutMillis(): Int = 0
        override fun writeTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
