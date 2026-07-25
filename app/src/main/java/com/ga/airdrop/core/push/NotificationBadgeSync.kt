package com.ga.airdrop.core.push

import com.ga.airdrop.core.network.ApiClient
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
internal object NotificationBadgeSync {

    /** One row is enough — we only read `meta.unread_count`. */
    private const val PROBE_PAGE_SIZE = 1

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
        scope.launch {
            val unread = runCatching {
                ApiClient.service
                    .notifications(page = 1, perPage = PROBE_PAGE_SIZE)
                    .pagination
                    ?.unreadCount
            }.getOrNull() ?: return@launch // network/parse failure: keep the last good badge
            if (generation.get() != started) return@launch // a newer refresh/session won
            SessionStore.updateForSession(owner) {
                it.copy(unreadNotifications = unread.coerceAtLeast(0))
            }
        }
    }
}
