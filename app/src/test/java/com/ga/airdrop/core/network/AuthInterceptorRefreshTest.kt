package com.ga.airdrop.core.network

import com.ga.airdrop.core.auth.AuthTokenStore
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
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
import org.junit.Assert.assertTrue
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
        private val trackedCall = NoopCall(original)
        val isCanceled: Boolean get() = trackedCall.isCanceled()

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            proceeded += request
            return script(request, proceeded.size)
        }

        override fun connection(): Connection? = null
        override fun call(): Call = trackedCall
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private class NoopCall(private val request: Request) : Call {
        @Volatile
        private var canceled = false

        override fun request(): Request = request
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException()
        override fun cancel() { canceled = true }
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = canceled
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

    private fun sessionBoundRequest(): Request {
        val provenance = requireNotNull(AuthTokenStore.requestProvenance(AuthTokenStore.snapshot()))
        return apiRequest("/api/user/notifications/mark-read").newBuilder()
            .header(AuthTokenStore.REQUEST_REVISION_HEADER, provenance.revision.toString())
            .header(AuthTokenStore.REQUEST_SESSION_ID_HEADER, provenance.sessionId)
            .build()
    }

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
        assertNull(AuthTokenStore.snapshot().sessionId)
        assertEquals(2, chain.proceeded.size) // original + refresh, no retry
    }

    @Test
    fun `failed stale refresh cannot clear fresh session with same bearer text`() {
        val originalSession = AuthTokenStore.snapshot()
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            if (isRefresh(req)) {
                AuthTokenStore.save("old-token")
                response(req, 401)
            } else {
                response(req, 401)
            }
        }

        val result = interceptor.intercept(chain)
        val replacement = AuthTokenStore.snapshot()

        assertEquals(401, result.code)
        assertEquals("old-token", replacement.token)
        assertTrue(replacement.sessionId != null)
        assertTrue(replacement.sessionId != originalSession.sessionId)
        assertEquals(2, chain.proceeded.size)
        assertEquals("Bearer old-token", chain.proceeded[1].header("Authorization"))
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
        val failedSession = AuthTokenStore.snapshot()
        // First caller rotates the bearer.
        val firstRotation = TokenRefresher.refresh(failedSession) { expectedToken ->
            assertEquals("old-token", expectedToken)
            networkRefreshes.incrementAndGet(); "new-token"
        }
        assertEquals("new-token", firstRotation?.token)
        // Second caller still holds the OLD failed token (it 401'd before the
        // rotation): sees the rotated bearer and must NOT hit the network —
        // Swift's "await the existing task" arm.
        val coalesced = TokenRefresher.refresh(failedSession) { _ ->
            networkRefreshes.incrementAndGet(); "should-not-run"
        }
        assertEquals("new-token", coalesced?.token)
        assertEquals(1, networkRefreshes.get())
        assertEquals("new-token", AuthTokenStore.token)
    }

    @Test
    fun `single-flight replacement session never reuses stale refresh`() {
        val failedSession = AuthTokenStore.snapshot()
        val refreshCalls = AtomicInteger()
        AuthTokenStore.save("replacement-login-token")

        val refreshed = TokenRefresher.refresh(failedSession) { _ ->
            refreshCalls.incrementAndGet()
            "must-not-install"
        }

        assertNull(refreshed)
        assertEquals(0, refreshCalls.get())
        assertEquals("replacement-login-token", AuthTokenStore.token)
    }

    @Test
    fun `session-bound mutation switched before auth attachment never proceeds`() {
        val expected = AuthTokenStore.snapshot()
        val provenance = requireNotNull(AuthTokenStore.requestProvenance(expected))
        val request = apiRequest("/api/user/notifications/mark-read").newBuilder()
            .header(AuthTokenStore.REQUEST_REVISION_HEADER, provenance.revision.toString())
            .header(AuthTokenStore.REQUEST_SESSION_ID_HEADER, provenance.sessionId)
            .build()
        val chain = ScriptedChain(request) { req, _ -> response(req, 200) }

        AuthTokenStore.save("replacement-account-token")
        val failure = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertTrue("Expected stale-session body failure, got $failure", failure is StaleAuthSessionException)
        assertEquals(0, chain.proceeded.size)
    }

    @Test
    fun `session-bound mutation rotated before auth attachment never proceeds`() {
        val expected = AuthTokenStore.snapshot()
        val provenance = requireNotNull(AuthTokenStore.requestProvenance(expected))
        val request = apiRequest("/api/user/notifications/mark-read").newBuilder()
            .header(AuthTokenStore.REQUEST_REVISION_HEADER, provenance.revision.toString())
            .header(AuthTokenStore.REQUEST_SESSION_ID_HEADER, provenance.sessionId)
            .build()
        val chain = ScriptedChain(request) { req, _ -> response(req, 200) }

        requireNotNull(AuthTokenStore.rotate(expected, "rotated-token"))
        val failure = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertTrue(failure is StaleAuthSessionException)
        assertEquals(0, chain.proceeded.size)
    }

    @Test
    fun `valid session-bound mutation strips provenance before wire`() {
        val expected = AuthTokenStore.snapshot()
        val provenance = requireNotNull(AuthTokenStore.requestProvenance(expected))
        val request = apiRequest("/api/user/notifications/mark-read").newBuilder()
            .header(AuthTokenStore.REQUEST_REVISION_HEADER, provenance.revision.toString())
            .header(AuthTokenStore.REQUEST_SESSION_ID_HEADER, provenance.sessionId)
            .build()
        val chain = ScriptedChain(request) { req, _ -> response(req, 200) }

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(1, chain.proceeded.size)
        assertEquals("Bearer old-token", chain.proceeded.single().header("Authorization"))
        assertNull(chain.proceeded.single().header(AuthTokenStore.REQUEST_REVISION_HEADER))
        assertNull(chain.proceeded.single().header(AuthTokenStore.REQUEST_SESSION_ID_HEADER))
    }

    @Test
    fun `fresh login after refresh result prevents stale account retry`() {
        interceptor = AuthInterceptor(beforeRetry = {
            AuthTokenStore.save("account-b-token")
        })
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            when {
                isRefresh(req) -> response(req, 200, """{"token":"account-a-rotated"}""")
                req.header("Authorization") == "Bearer account-a-rotated" -> response(req, 200)
                else -> response(req, 401)
            }
        }

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertEquals(2, chain.proceeded.size)
        assertEquals("Bearer old-token", chain.proceeded[1].header("Authorization"))
    }

    @Test
    fun `replacement save cancels active retry without blocking auth write`() {
        val saveStarted = CountDownLatch(1)
        val saveFinished = CountDownLatch(1)
        var replacement: Thread? = null
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            when {
                isRefresh(req) -> response(req, 200, """{"token":"account-a-rotated"}""")
                req.header("Authorization") == "Bearer account-a-rotated" -> {
                    replacement = thread {
                        saveStarted.countDown()
                        AuthTokenStore.save("account-b-token")
                        saveFinished.countDown()
                    }
                    assertTrue(saveStarted.await(1, TimeUnit.SECONDS))
                    assertTrue("replacement auth write must not wait on network", saveFinished.await(1, TimeUnit.SECONDS))
                    response(req, 200)
                }
                else -> response(req, 401)
            }
        }

        val result = interceptor.intercept(chain)
        replacement?.join(1_000)

        assertEquals(401, result.code)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertEquals(3, chain.proceeded.size)
        assertEquals(0, saveFinished.count)
        assertTrue(chain.isCanceled)
    }

    @Test
    fun `session-bound dispatch is canceled by replacement without blocking auth write`() {
        val expected = AuthTokenStore.snapshot()
        val provenance = requireNotNull(AuthTokenStore.requestProvenance(expected))
        val request = apiRequest("/api/user/notifications/mark-read").newBuilder()
            .header(AuthTokenStore.REQUEST_REVISION_HEADER, provenance.revision.toString())
            .header(AuthTokenStore.REQUEST_SESSION_ID_HEADER, provenance.sessionId)
            .build()
        val saveStarted = CountDownLatch(1)
        val saveFinished = CountDownLatch(1)
        lateinit var replacement: Thread
        val chain = ScriptedChain(request) { req, _ ->
            replacement = thread {
                saveStarted.countDown()
                AuthTokenStore.save("account-b-token")
                saveFinished.countDown()
            }
            assertTrue(saveStarted.await(1, TimeUnit.SECONDS))
            assertTrue("replacement auth write must not wait on bound network", saveFinished.await(1, TimeUnit.SECONDS))
            response(req, 200)
        }

        val failure = runCatching { interceptor.intercept(chain) }.exceptionOrNull()
        replacement.join(1_000)

        assertTrue(failure is StaleAuthSessionException)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertEquals(0, saveFinished.count)
        assertTrue(chain.isCanceled)
    }

    @Test
    fun `replacement at response completion boundary invalidates retry atomically`() {
        interceptor = AuthInterceptor(
            beforeRetry = {},
            beforeDispatchComplete = { request ->
                if (request.header("Authorization") == "Bearer account-a-rotated") {
                    AuthTokenStore.save("account-b-token")
                }
            },
        )
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            when {
                isRefresh(req) -> response(req, 200, """{"token":"account-a-rotated"}""")
                req.header("Authorization") == "Bearer account-a-rotated" -> response(req, 200)
                else -> response(req, 401)
            }
        }

        val result = interceptor.intercept(chain)
        val failure = runCatching { result.body?.string() }.exceptionOrNull()

        assertTrue("Expected completion-boundary stale failure, got $failure", failure is StaleAuthSessionException)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertEquals(3, chain.proceeded.size)
        assertTrue(chain.isCanceled)
    }

    @Test
    fun `replacement after headers cancels lazy body before account a data is consumed`() {
        val chain = ScriptedChain(sessionBoundRequest()) { req, _ ->
            response(req, 200, """{"account":"a"}""")
        }
        val result = interceptor.intercept(chain)

        AuthTokenStore.save("account-b-token")
        val failure = runCatching { result.body?.string() }.exceptionOrNull()

        assertTrue(failure is StaleAuthSessionException)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertTrue(chain.isCanceled)
    }

    @Test
    fun `replacement after exact length read is rejected when body closes`() {
        val payload = """{"account":"a"}"""
        val chain = ScriptedChain(sessionBoundRequest()) { req, _ -> response(req, 200, payload) }
        val result = interceptor.intercept(chain)
        val source = requireNotNull(result.body).source()

        assertEquals(payload, source.readByteArray(payload.toByteArray().size.toLong()).decodeToString())
        AuthTokenStore.save("account-b-token")
        val failure = runCatching { source.close() }.exceptionOrNull()

        assertTrue(failure is StaleAuthSessionException)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertTrue(chain.isCanceled)
    }

    @Test
    fun `refresh request uses expected bearer even when global session changes`() {
        val chain = ScriptedChain(apiRequest()) { req, _ ->
            if (isRefresh(req)) {
                AuthTokenStore.save("account-b-token")
                response(req, 200, """{"token":"account-a-rotated"}""")
            } else {
                response(req, 401)
            }
        }

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        assertEquals("account-b-token", AuthTokenStore.token)
        assertEquals(2, chain.proceeded.size)
        assertEquals("Bearer old-token", chain.proceeded[1].header("Authorization"))
    }
}
