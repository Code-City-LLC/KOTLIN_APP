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
     * [failedToken] is the bearer that just 401'd; [performRefresh] runs the
     * actual network call and returns the new token (null on any failure —
     * Swift also rejects a body-less 200, so "no token" is "failed").
     * Returns true when the caller should retry with [AuthTokenStore.token].
     */
    fun refresh(failedToken: String, performRefresh: () -> String?): Boolean =
        synchronized(lock) {
            val current = AuthTokenStore.token
            // Rotated while we waited on the lock — Swift's "await the
            // existing task" arm. No second network round-trip.
            if (current != null && current != failedToken) return true
            val newToken = performRefresh()?.takeIf { it.isNotBlank() } ?: return false
            AuthTokenStore.save(newToken)
            true
        }

    /**
     * Foreground-refresh outcome mapping — Swift
     * SceneDelegate.refreshStoredSessionIfNeeded parity (SceneDelegate:429):
     * a 401 means the stored session is dead → clear it (AppRoot's reactive
     * logout then returns the user to the auth landing, mirroring Swift's
     * handleAPISessionInvalidated); a success with a token rotates the
     * bearer; anything else (network error, body-less response) leaves the
     * session untouched — Swift logs and skips.
     */
    fun applyForegroundRefresh(
        expectedSession: AuthTokenStore.Snapshot,
        httpCode: Int?,
        newToken: String?,
    ) {
        synchronized(lock) {
            // The request may finish after login or another refresh installs a
            // newer bearer. A stale outcome must neither clear nor overwrite it.
            if (AuthTokenStore.snapshot() != expectedSession) return
            when {
                httpCode == 401 -> AuthTokenStore.clear()
                !newToken.isNullOrBlank() -> AuthTokenStore.save(newToken)
                else -> Unit
            }
        }
    }
}
