package com.ga.airdrop.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthInterceptorParityTest {

    @After
    fun tearDown() {
        // The store is a singleton; two of these tests now deliberately leave a
        // live session behind, so wipe it rather than leak it into the next test.
        AuthTokenStore.clear()
    }

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

    /**
     * Was `authenticatedRequestStillAttachesBearerAndClearsMatchingTokenOn401`,
     * and its last assertion was `assertNull(AuthTokenStore.token)` — it pinned
     * the behaviour where a 401 whose refresh did not produce a new token wiped
     * the session and dumped the customer back on the sign-in screen.
     *
     * That was the defect. `AuthInterceptor.performRefresh` ends in
     * `runCatching { ... }.getOrNull()`, so "no new token" was returned for ANY
     * failure — a dropped connection, a timeout, a captive portal, a body-less
     * 200 — not just for a token the server had genuinely retired. Pinning the
     * clear meant pinning "log the customer out whenever the refresh call is
     * unlucky", which is exactly the "app logs me out too often" report.
     *
     * The rule now: the app never logs the customer out (Kemar, 2026-07-26).
     * Only the customer tapping Log Out, the biometric lock screen, and
     * post-registration end a session. So the 401 is handed back to the caller
     * — the screen shows its own error and its own retry — and the session
     * survives untouched.
     *
     * The half of the old test that was always right is kept verbatim: an
     * authenticated request still gets the bearer and the JSON Accept header.
     */
    @Test
    fun authenticatedRequestAttachesBearerAndKeepsCustomerSignedInOn401() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.save("stale-token")
        val sessionBefore = AuthTokenStore.currentSessionId()
        val request = Request.Builder()
            .url("https://example.com/api/v1/user/profile")
            .build()
        val chain = RecordingChain(request, responseCode = 401)

        val response = AuthInterceptor().intercept(chain)

        val sent = chain.proceededRequests.first()
        assertEquals("https://example.com/api/v1/user/profile", sent.url.toString())
        assertEquals("Bearer stale-token", sent.header("Authorization"))
        assertEquals("application/json", sent.header("Accept"))

        // Exactly one refresh attempt is still made — the parity behaviour with
        // Swift's refresh-then-retry — it just no longer costs the session.
        assertEquals(2, chain.proceededRequests.size)
        assertTrue(
            "the second dispatch should be the single /auth/refresh attempt",
            chain.proceededRequests[1].url.encodedPath.endsWith("auth/refresh"),
        )

        // The caller gets the 401 back so the screen can show its own error.
        assertEquals(401, response.code)
        // INVERTED: the customer stays signed in.
        assertEquals(
            "a 401 the refresh could not resolve must not log the customer out",
            "stale-token",
            AuthTokenStore.token,
        )
        assertEquals(
            "the session generation must survive intact, not be torn down and rebuilt",
            sessionBefore,
            AuthTokenStore.currentSessionId(),
        )
    }

    /**
     * The failure mode the old assertion actually punished most often: the 401
     * is real, but the refresh call never reaches the server. `performRefresh`
     * swallows the IOException into `null`, which used to be indistinguishable
     * from "the server retired your token" and cost the customer their session.
     * A lost connection must cost nothing.
     */
    @Test
    fun droppedConnectionDuringRefreshKeepsCustomerSignedIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AuthTokenStore.save("token-on-a-bad-network")
        val sessionBefore = AuthTokenStore.currentSessionId()
        val request = Request.Builder()
            .url("https://example.com/api/v1/user/profile")
            .build()
        val chain = RecordingChain(request, responseCode = 401, dropConnectionOnRefresh = true)

        val response = AuthInterceptor().intercept(chain)

        assertEquals("Bearer token-on-a-bad-network", chain.proceededRequests.first().header("Authorization"))
        assertEquals(401, response.code)
        assertEquals(
            "a refresh that died on the wire must never sign the customer out",
            "token-on-a-bad-network",
            AuthTokenStore.token,
        )
        assertEquals(sessionBefore, AuthTokenStore.currentSessionId())
    }

    private class RecordingChain(
        private val original: Request,
        private val responseCode: Int,
        private val dropConnectionOnRefresh: Boolean = false,
    ) : Interceptor.Chain {
        val proceededRequests = mutableListOf<Request>()

        /** The first dispatch — the caller's own request, not the refresh that follows it. */
        val proceededRequest: Request get() = proceededRequests.first()

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            proceededRequests += request
            if (dropConnectionOnRefresh && request.url.encodedPath.endsWith("auth/refresh")) {
                throw IOException("Software caused connection abort")
            }
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
