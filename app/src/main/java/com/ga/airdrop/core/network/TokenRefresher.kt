package com.ga.airdrop.core.network

import com.ga.airdrop.core.auth.AuthTokenStore

/**
 * Single-flight token refresh — Android counterpart of Swift AirdropAPI's
 * `inFlightRefresh` coalescing (AirdropAPI.swift:181-187 / :678).
 *
 * Swift's documented failure mode without coalescing: several requests 401
 * together after the app returns from background; each refreshes on its own;
 * the first rotation invalidates the others' refresh attempts, and those
 * failures tear down a session that is actually valid. Here the lock plays
 * the role of the actor: the first caller performs the network refresh, and
 * every caller that was queued behind it observes the rotated bearer and
 * returns success without a second round-trip.
 */
object TokenRefresher {

    private val lock = Any()

    /**
     * [expectedSession] is the exact generation that just 401'd;
     * [performRefresh] receives that generation's bearer and runs the actual
     * network call, returning the new token (null on any failure —
     * Swift also rejects a body-less 200, so "no token" is "failed").
     * Returns the exact same-session snapshot the caller may safely retry with.
     */
    fun refresh(
        expectedSession: AuthTokenStore.Snapshot,
        performRefresh: (expectedToken: String) -> String?,
    ): AuthTokenStore.Snapshot? =
        synchronized(lock) {
            val current = AuthTokenStore.snapshot()
            // Rotated while we waited on the lock — Swift's "await the
            // existing task" arm. No second network round-trip.
            if (current != expectedSession) {
                return current.takeIf { AuthTokenStore.isSameSession(it, expectedSession) }
            }
            val expectedToken = expectedSession.token ?: return null
            val newToken = performRefresh(expectedToken)?.takeIf { it.isNotBlank() } ?: return null
            AuthTokenStore.rotate(expectedSession, newToken)
        }

    /**
     * Foreground-refresh outcome mapping — Swift
     * SceneDelegate.refreshStoredSessionIfNeeded parity (SceneDelegate:429).
     *
     * A success carrying a token rotates the bearer. A **401** is the one
     * confirmed-dead signal and clears the session (Kemar 2026-07-26, adopting
     * SwiftHawk's rule; AppRoot then returns the user to the auth landing,
     * mirroring Swift's handleAPISessionInvalidated). Every other outcome — a
     * network error, a 5xx, a body-less response — leaves the stored bearer
     * untouched. The app never signs a customer out for losing signal.
     *
     * This must agree with AuthInterceptor's teardown exactly. Two code paths
     * that disagree about what a dead session is would sign customers out on
     * whichever one happened to run first.
     */
    fun applyForegroundRefresh(
        expectedSession: AuthTokenStore.Snapshot,
        httpCode: Int?,
        newToken: String?,
        beforeApply: () -> Unit = {},
    ) {
        synchronized(lock) {
            beforeApply()
            when {
                // Both operations compare and mutate under AuthTokenStore's
                // own lock, closing the check-then-clear/rotate race.
                //
                // Order matters: 401 is tested before the rotate arm so a
                // response that somehow carries both cannot rotate onto a
                // session the server has already rejected.
                httpCode == 401 -> AuthTokenStore.clear(expectedSession)
                !newToken.isNullOrBlank() -> AuthTokenStore.rotate(expectedSession, newToken)
                else -> Unit
            }
        }
    }
}
