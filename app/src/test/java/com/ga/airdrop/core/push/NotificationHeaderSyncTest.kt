package com.ga.airdrop.core.push

import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.SessionStore
import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHeaderSyncTest {
    @Test
    fun `unread row always lights the header`() {
        assertTrue(nextUnreadHeaderState(current = false, pageHasUnread = true, endReached = false))
    }

    @Test
    fun `complete read inbox clears the header`() {
        assertFalse(nextUnreadHeaderState(current = true, pageHasUnread = false, endReached = true))
    }

    @Test
    fun `partial read page cannot falsely clear an unread header`() {
        assertTrue(nextUnreadHeaderState(current = true, pageHasUnread = false, endReached = false))
    }

    @Test
    fun `reading final loaded row clears header immediately`() {
        val owner = AuthenticatedSessionOwner("notification-test-session")
        SessionStore.initializeAuthenticatedSession(owner.sessionId)
        SessionStore.updateForSession(owner) { it.copy(hasUnreadNotifications = true) }
        NotificationHeaderSync.publishAfterRead(
            owner = owner,
            notifications = listOf(AirdropNotification(id = "read", isRead = true)),
            endReached = true,
        )
        assertFalse(SessionStore.header.value.hasUnreadNotifications)
        SessionStore.clear()
    }

    @Test
    fun `reading loaded rows cannot clear header before pagination ends`() {
        val owner = AuthenticatedSessionOwner("notification-partial-session")
        SessionStore.initializeAuthenticatedSession(owner.sessionId)
        SessionStore.updateForSession(owner) { it.copy(hasUnreadNotifications = true) }
        NotificationHeaderSync.publishAfterRead(
            owner = owner,
            notifications = listOf(AirdropNotification(id = "read", isRead = true)),
            endReached = false,
        )
        assertTrue(SessionStore.header.value.hasUnreadNotifications)
        SessionStore.clear()
    }
}
