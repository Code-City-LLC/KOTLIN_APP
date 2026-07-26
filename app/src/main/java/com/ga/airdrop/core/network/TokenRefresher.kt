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
     * SceneDelegate.refreshStoredSessionIfNeeded parity (SceneDelegate:429):
     * a 401 means the stored session is dead → clear it (AppRoot's reactive
     * logout then returns the user to the auth landing, mirroring Swift's
     * handleAPISessionInvalidated); a success with a token rotates the
     * bearer; anything else — a network error, a body-less response, or even
     * a 401 — leaves the session untouched. The app does not sign the customer
     * out; only the customer does.
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
                // ⚠️ A 401 here used to clear the session. It no longer does.
                // Kemar 2026-07-26: the app never logs the customer out; only
                // the customer does. A foreground refresh that comes back 401
                // leaves the stored token alone — the next real request will
                // surface its own error, and a token that recovers (or a
                // server-side fix) resumes working without a re-login.
                !newToken.isNullOrBlank() -> AuthTokenStore.rotate(expectedSession, newToken)
                else -> Unit
            }
        }
    }
}
