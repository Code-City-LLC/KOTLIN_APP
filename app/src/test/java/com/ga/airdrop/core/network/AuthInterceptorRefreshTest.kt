package com.ga.airdrop.core.network

import com.ga.airdrop.core.auth.AuthTokenStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Token-refresh 401 recovery — Swift AirdropAPI parity
 * (makeRequestWithResponse:347 refresh-then-retry; refreshToken():678
 * single-flight + body-less-200 rejection; RN refreshToken.ts).
 *
 * Pre-fix Kotlin had ZERO refreshToken callers: any 401 cleared the token and
 * hard-logged the user out. These tests drive the interceptor with a scripted
 * fake Chain (pure JVM, no new dependencies) and pin the recovered flow.
 */
class AuthInterceptorRefreshTest {

    private lateinit var interceptor: AuthInterceptor

    @Before
    fun setUp() {
        interceptor = AuthInterceptor()
        AuthTokenStore.save("old-token")
    }

    @After
    fun tearDown() {
        AuthTokenStore.clear()
    }

    // ── scripted chain ──────────────────────────────────────────────────────

    /** Serves canned responses per request; records every proceeded request. */
    private class ScriptedChain(
        private val original: Request,
        private val script: (Request, Int) -> Response,
    ) : Interceptor.Chain {
        val proceeded = mutableListOf<Request>()

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            proceeded += request
            return script(request, proceeded.size)
        }

        override fun connection(): Connection? = null
        override fun call(): Call = NoopCall(original)
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private class NoopCall(private val request: Request) : Call {
        override fun request(): Request = request
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException()
        override fun cancel() {}
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = NoopCall(request)
    }

    private fun response(request: Request, code: Int, body: String = "{}"): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 401) "Unauthorized" else "OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun apiRequest(path: String = "/api/user/packages"): Request =
        Request.Builder().url("https://example.test$path").get().build()

    private val isRefresh: (Request) -> Boolean =
        { it.url.encodedPath.endsWith("auth/refresh") }

    // ── tests ───────────────────────────────────────────────────────────────

    @Test
    fun `401 refreshes once and retries with the rotated bearer`() {
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            when {
                isRefresh(req) -> response(req, 200, """{"token":"new-token"}""")
                req.header("Authorization") == "Bearer new-token" -> response(req, 200, """{"data":[]}""")
                else -> response(req, 401)
            }
        }

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals("new-token", AuthTokenStore.token)
        // original(old) → refresh → retry(new): exactly three proceeds.
        assertEquals(3, chain.proceeded.size)
        assertEquals("Bearer old-token", chain.proceeded[0].header("Authorization"))
        assertEquals("Bearer old-token", chain.proceeded[1].header("Authorization"))
        assertEquals("POST", chain.proceeded[1].method)
        assertEquals("Bearer new-token", chain.proceeded[2].header("Authorization"))
    }

    @Test
    fun `failed refresh clears the session and returns the original 401`() {
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            if (isRefresh(req)) response(req, 401) else response(req, 401)
        }

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        assertNull(AuthTokenStore.token)
        assertEquals(2, chain.proceeded.size) // original + refresh, no retry
    }

    @Test
    fun `body-less 200 refresh is rejected like Swift, session cleared`() {
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            if (isRefresh(req)) response(req, 200, "") else response(req, 401)
        }

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        assertNull(AuthTokenStore.token)
    }

    @Test
    fun `pre-auth 401 is returned untouched — no refresh, token preserved`() {
        val chain = ScriptedChain(apiRequest("/api/auth/login")) { req, _ ->
            response(req, 401, """{"message":"bad credentials"}""")
        }

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        assertEquals("old-token", AuthTokenStore.token)
        assertEquals(1, chain.proceeded.size)
        assertNull(chain.proceeded[0].header("Authorization"))
    }

    @Test
    fun `single-flight - a caller queued behind a rotation skips the network`() {
        val networkRefreshes = AtomicInteger(0)
        // First caller rotates the bearer.
        assertEquals(
            true,
            TokenRefresher.refresh("old-token") {
                networkRefreshes.incrementAndGet(); "new-token"
            },
        )
        // Second caller still holds the OLD failed token (it 401'd before the
        // rotation): sees the rotated bearer and must NOT hit the network —
        // Swift's "await the existing task" arm.
        assertEquals(
            true,
            TokenRefresher.refresh("old-token") {
                networkRefreshes.incrementAndGet(); "should-not-run"
            },
        )
        assertEquals(1, networkRefreshes.get())
        assertEquals("new-token", AuthTokenStore.token)
    }
}
