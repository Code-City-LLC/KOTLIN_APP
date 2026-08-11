package com.ga.airdrop.core.push

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.network.ForegroundRefreshCoordinator
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.SessionStore
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the header bell's unread badge in sync with Laravel's inbox.
 *
 * Laravel already publishes the tally in the notifications list meta
 * (`GET /user/notifications` → `meta.unread_count`, verified live), so this
 * asks for the cheapest possible page (one row) purely to read that number —
 * it never caches notification rows. Those remain solely owned by
 * NotificationsViewModel.
 *
 * Session hygiene mirrors the rest of the app: every publish is gated on the
 * [AuthenticatedSessionOwner] that started the fetch, and a generation counter
 * drops results that land after the session changed, so a slow response from a
 * previous account can never paint a badge for the next one.
 */
/**
 * The one network call this object makes, behind an interface so it can be
 * replaced in tests.
 *
 * ⚠️ WHY THIS SEAM EXISTS. `AuthTokenStore.save()` calls
 * [NotificationBadgeSync.onAuthenticatedSessionChanged], so **saving a token
 * issues a real authenticated HTTP request** — a data-layer write that reaches
 * out to the network. Any instrumentation test doing
 * `AuthTokenStore.save("fake-token", ...)` therefore fires a live
 * `GET /user/notifications` with a bogus bearer: it 401s, the interceptor
 * refreshes, the refresh 401s, and the session is torn down mid-test.
 *
 * The damage then lands somewhere that looks unrelated:
 * `NotificationAccountPreferences.currentMasterEnabled()` reads inside
 * `AuthTokenStore.runWhileCurrentSession(...)`, which now fails, returns null,
 * and `?.master == true` turns that null into **false** — so the screen renders
 * "notifications are off" and a test asserting the enabled copy fails with no
 * mention of the network anywhere in it.
 *
 * That presented as a CI-only flake in NotificationsScreenParityTest, a
 * different test each run, and cost most of a day across two agents.
 * BrightHarbor's ruling: inject the gateway. This is that seam.
 */
internal fun interface NotificationBadgeGateway {
    /** Unread count, or null when it could not be read. */
    suspend fun unreadCount(perPage: Int): Int?
}

internal object NotificationBadgeSync {

    /** One row is enough — we only read `meta.unread_count`. */
    private const val PROBE_PAGE_SIZE = 1

    /**
     * Swappable ONLY so tests can stop a fake token from reaching the network.
     * Production never reassigns this.
     */
    @Volatile
    internal var gateway: NotificationBadgeGateway = NotificationBadgeGateway { perPage ->
        ApiClient.service.notifications(page = 1, perPage = perPage).pagination?.unreadCount
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0)

    /** Auth transitions: clear the stale badge, then refetch for the new owner. */
    fun onAuthenticatedSessionChanged(owner: AuthenticatedSessionOwner?) {
        generation.incrementAndGet()
        if (owner == null) return
        SessionStore.updateForSession(owner) { it.copy(unreadNotifications = 0) }
        refresh(owner)
    }

    /** Foreground/resume + after the inbox marks rows read. */
    fun refresh() {
        DefaultAuthenticatedSessionBoundary.capture()?.let(::refresh)
    }

    /**
     * Publish a count we already know locally (e.g. the inbox just marked
     * everything read) without paying for a round trip.
     */
    fun publishKnownCount(count: Int) {
        val owner = DefaultAuthenticatedSessionBoundary.capture() ?: return
        SessionStore.updateForSession(owner) {
            it.copy(unreadNotifications = count.coerceAtLeast(0))
        }
    }

    private fun refresh(owner: AuthenticatedSessionOwner) {
        val started = generation.incrementAndGet()
        // ⚠️ CAPTURE THE GATEWAY HERE, NOT INSIDE THE COROUTINE.
        //
        // `gateway` is @Volatile and the launch below runs later, so reading it
        // inside the coroutine resolves whatever is installed at EXECUTION
        // time. A test that swaps in a fake and restores the real one in
        // tearDown would then have its queued refresh run against the REAL
        // gateway and hit the network anyway — the exact failure this seam
        // exists to prevent. Binding at launch time makes the substitution
        // deterministic. BrightHarbor #180 hold 1/3.
        val probe = gateway
        scope.launch {
            val unread = runCatching {
                ForegroundRefreshCoordinator.run {
                    probe.unreadCount(PROBE_PAGE_SIZE)
                }
            }.getOrNull() ?: return@launch // network/parse failure: keep the last good badge
            if (generation.get() != started) return@launch // a newer refresh/session won
            SessionStore.updateForSession(owner) {
                it.copy(unreadNotifications = unread.coerceAtLeast(0))
            }
        }
    }
}
