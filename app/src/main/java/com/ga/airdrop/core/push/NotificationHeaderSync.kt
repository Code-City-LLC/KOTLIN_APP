package com.ga.airdrop.core.push

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.data.repo.MiscRepository
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Synchronizes the existing shared header with Laravel's existing inbox.
 * It stores only the unread bit in [SessionStore]; notification rows remain
 * solely owned by GET /user/notifications and NotificationsViewModel.
 */
internal object NotificationHeaderSync {
    // Laravel caps per_page at 100. Querying its unread-only rail prevents a
    // full page of newer read rows from hiding an older unread notification.
    private const val PAGE_SIZE = 100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshGeneration = AtomicLong(0)

    fun onAuthenticatedSessionChanged(owner: AuthenticatedSessionOwner?) {
        refreshGeneration.incrementAndGet()
        if (owner != null) refresh(owner)
    }

    fun refresh() {
        DefaultAuthenticatedSessionBoundary.capture()?.let(::refresh)
    }

    fun markUnread() {
        val owner = DefaultAuthenticatedSessionBoundary.capture() ?: return
        SessionStore.updateForSession(owner) { it.copy(hasUnreadNotifications = true) }
    }

    internal fun publishPage(
        owner: AuthenticatedSessionOwner,
        notifications: List<AirdropNotification>,
        endReached: Boolean,
    ) {
        val hasUnread = notifications.any { !it.isRead }
        SessionStore.updateForSession(owner) { current ->
            current.copy(
                hasUnreadNotifications = nextUnreadHeaderState(
                    current = current.hasUnreadNotifications,
                    pageHasUnread = hasUnread,
                    endReached = endReached,
                )
            )
        }
    }

    /** Clear only when every server page is represented by the loaded rows. */
    internal fun publishAfterRead(
        owner: AuthenticatedSessionOwner,
        notifications: List<AirdropNotification>,
        endReached: Boolean,
    ) {
        val hasUnread = notifications.any { !it.isRead }
        SessionStore.updateForSession(owner) { current ->
            current.copy(
                hasUnreadNotifications = nextUnreadHeaderState(
                    current = current.hasUnreadNotifications,
                    pageHasUnread = hasUnread,
                    endReached = endReached,
                )
            )
        }
    }

    private fun refresh(owner: AuthenticatedSessionOwner) {
        val generation = refreshGeneration.incrementAndGet()
        scope.launch {
            MiscRepository(ApiClient.service).notifications(
                page = 1,
                limit = PAGE_SIZE,
                unreadOnly = true,
            )
                .onSuccess { batch ->
                    if (refreshGeneration.get() != generation) return@onSuccess
                    if (!DefaultAuthenticatedSessionBoundary.isCurrent(owner)) return@onSuccess
                    val visible = batch.filter { it.isVisibleForInstalledApp() }
                    publishPage(
                        owner = owner,
                        notifications = visible,
                        endReached = batch.size < PAGE_SIZE,
                    )
                }
        }
    }
}

internal fun nextUnreadHeaderState(
    current: Boolean,
    pageHasUnread: Boolean,
    endReached: Boolean,
): Boolean = when {
    pageHasUnread -> true
    endReached -> false
    else -> current
}
