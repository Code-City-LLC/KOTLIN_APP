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
}
