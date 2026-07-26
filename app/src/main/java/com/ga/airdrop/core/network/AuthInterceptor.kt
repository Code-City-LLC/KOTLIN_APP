package com.ga.airdrop.core.network

import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.LoginResponse
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Adds the Sanctum bearer token to every request; on 401 it attempts ONE
 * token refresh + retry before force-logging-out — mirror of Swift
 * AirdropAPI.makeRequestWithResponse:347 (refresh-then-retry) and
 * refreshToken() at :678 (single-flight, reject body-less 200).
 */
class AuthInterceptor internal constructor(
    private val beforeRetry: () -> Unit,
    private val beforeDispatch: (Request) -> Unit = {},
) : Interceptor {

    constructor() : this(beforeRetry = {})

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isNoAuth = original.header(NO_AUTH_HEADER)?.equals("true", ignoreCase = true) == true
        // Pre-auth endpoints MUST be sent unauthenticated — a leftover bearer
        // from a prior session makes the backend reject the sign-in/registration
        // request (the reported "login shows 400"). Swift's AirdropAPI omits the
        // token for these too. `auth/refresh` and `auth/logout` still need it.
        // NB: match `auth/register`, not the unrelated `device-tokens/register`.
        val isPreAuth = path.endsWith("auth/login") ||
            path.endsWith("auth/register") ||
            path.endsWith("auth/forgot-password") ||
            path.endsWith("auth/reset-password")
        // Swift `isRefreshRequest` recursion guard: a 401 from the refresh
        // endpoint itself must never trigger another refresh attempt.
        val isRefresh = path.endsWith("auth/refresh")
        val boundRevision = original.header(AuthTokenStore.REQUEST_REVISION_HEADER)
        val boundSessionId = original.header(AuthTokenStore.REQUEST_SESSION_ID_HEADER)
        val isSessionBound = boundRevision != null || boundSessionId != null
        val currentSnapshot = AuthTokenStore.snapshot()
        if (isSessionBound) {
            val expectedRevision = boundRevision?.toLongOrNull()
            val currentProvenance = AuthTokenStore.requestProvenance(currentSnapshot)
            if (
                expectedRevision == null ||
                boundSessionId == null ||
                currentProvenance?.revision != expectedRevision ||
                currentProvenance?.sessionId != boundSessionId
            ) {
                throw StaleAuthSessionException()
            }
        }
        val attachedToken = if (isNoAuth || isPreAuth) null else currentSnapshot.token
        val builder = original.newBuilder()
            .removeHeader(NO_AUTH_HEADER)
            .removeHeader(AuthTokenStore.REQUEST_REVISION_HEADER)
            .removeHeader(AuthTokenStore.REQUEST_SESSION_ID_HEADER)
            .header("Accept", "application/json")
        attachedToken?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()

        val response = if (isSessionBound) {
            proceedForSession(chain, request, currentSnapshot)
        } else {
            chain.proceed(request)
        }

        if (
            response.code != 401 || attachedToken == null || isPreAuth || isRefresh ||
            isSessionBound
        ) {
            return response
        }

        val original401 = response.newBuilder()
            .body(response.peekBody(MAX_ERROR_BODY_BYTES))
            .build()
        response.close()

        // Swift :347 — try a single refresh + retry before tearing down the
        // session. TokenRefresher coalesces concurrent 401s onto one network
        // refresh; callers queued behind it receive the exact rotated snapshot.
        // Per-call, not per-interceptor. See performRefresh's `rejected` param.
        val refreshRejected = java.util.concurrent.atomic.AtomicBoolean(false)
        val refreshedSnapshot = TokenRefresher.refresh(currentSnapshot) { expectedToken ->
            performRefresh(chain, currentSnapshot, expectedToken, refreshRejected)
        }
        val retryToken = refreshedSnapshot?.token
        if (retryToken != null) {
            beforeRetry()
            val retry = request.newBuilder()
                .header("Authorization", "Bearer $retryToken")
                .build()
            return try {
                proceedForSession(chain, retry, refreshedSnapshot)
            } catch (_: StaleAuthSessionException) {
                original401
            }
        }

        // ⚠️ EXACTLY ONE TEARDOWN PATH, AND THIS IS IT.
        //
        // Kemar ruled (2026-07-26, reversing his earlier call) that Android
        // adopts SwiftHawk's rule so both platforms behave identically:
        //
        //     401 on any normal request        -> stay signed in
        //     refresh throws / 5xx / body-less -> stay signed in
        //     refresh answers 401              -> LOG OUT
        //
        // The distinction matters because performRefresh ends in
        // `runCatching { ... }.getOrNull()`, so a null return means ANY failure
        // — a dropped connection, a timeout, a tunnel. Clearing on null was the
        // original bug: losing signal logged the customer out. Only an explicit
        // HTTP 401 from /auth/refresh clears now.
        //
        // ⚠️ KNOWN RISK, raised with Kemar before he chose and still open:
        // AuthController::refresh DELETES the current token before returning
        // the replacement, so a lost response leaves the device presenting a
        // token the server already dropped. Refresh then answers 401 for a
        // perfectly healthy account and this path logs that customer out. It is
        // safe only once Laravel ships response-loss-safe rotation.
        if (refreshRejected.get()) {
            // Kemar 2026-07-26: "Android should sweep too." A rejected bearer
            // now runs the same canonical local sweep iOS has always run on
            // this path (AirdropAPI.swift:769-770). Previously only the token
            // was cleared, so on a shared handset the next person to sign in
            // inherited the previous customer's AI-disclosure acceptance,
            // biometric app-lock, quiet hours, calculator history and shop
            // search history.
            //
            // ⚠️ GATED ON THE GUARDED CLEAR RETURNING TRUE. clear(expected) is
            // generation-guarded and answers false when a newer session already
            // owns the store. clearLocalUserSession internally calls the
            // UNGUARDED clear(), so sweeping after a superseded 401 would wipe
            // the REPLACEMENT session's data. Only sweep when this session was
            // genuinely the one torn down.
            if (AuthTokenStore.clear(currentSnapshot)) {
                com.ga.airdrop.core.session.ForcedSignOutSweep.run()
            }
        }
        return original401
    }

    /**
     * POST /auth/refresh through the remainder of the chain (does not
     * re-enter this interceptor, so no recursion). Sends the expected request
     * generation's bearer because Laravel Sanctum's refresh requires it (Swift refreshToken()
     * doc), and rejects a 2xx without a token exactly like Swift's
     * "body-less success" hardening. Returns the new token or null.
     */
    private fun performRefresh(
        chain: Interceptor.Chain,
        expectedSession: AuthTokenStore.Snapshot,
        expectedToken: String,
        /**
         * Set to true ONLY when /auth/refresh itself answers 401. Passed in per
         * call rather than held on the interceptor: this class is a singleton
         * on the OkHttp client, so an instance field would race across
         * concurrent requests and one caller's dead session could tear down
         * another's.
         */
        rejected: java.util.concurrent.atomic.AtomicBoolean,
    ): String? = runCatching {
        val refreshRequest = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/refresh")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $expectedToken")
            .build()
        proceedForSession(chain, refreshRequest, expectedSession).use { refreshResponse ->
            // A 401 here is the ONE outcome that means the principal is gone.
            // Recorded separately so the caller can tell it apart from every
            // other failure — see the teardown block below.
            if (refreshResponse.code == 401) rejected.set(true)
            if (!refreshResponse.isSuccessful) return null
            val body = refreshResponse.body?.string().orEmpty()
            if (body.isBlank()) return null
            AirdropJson.decodeFromString(LoginResponse.serializer(), body)
                .token
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun proceedForSession(
        chain: Interceptor.Chain,
        request: Request,
        expectedSession: AuthTokenStore.Snapshot,
    ): Response {
        val dispatch = AuthTokenStore.acquireRequest(expectedSession) { chain.call().cancel() }
            ?: throw StaleAuthSessionException()
        var finished = false
        return try {
            beforeDispatch(request)
            if (!dispatch.isValid) throw StaleAuthSessionException()
            val response = chain.proceed(request)
            if (!AuthTokenStore.finishRequest(dispatch)) {
                response.close()
                throw StaleAuthSessionException()
            }
            finished = true
            response
        } finally {
            if (!finished) AuthTokenStore.abandonRequest(dispatch)
        }
    }

    companion object {
        const val NO_AUTH_HEADER = "X-Airdrop-No-Auth"
        private const val MAX_ERROR_BODY_BYTES = 1024L * 1024L
    }
}

class StaleAuthSessionException : IOException("Authenticated session changed before request dispatch")
