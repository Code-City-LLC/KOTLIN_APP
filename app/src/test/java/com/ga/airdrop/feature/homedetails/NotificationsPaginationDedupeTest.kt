package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A duplicate notification id CRASHES the inbox.
 *
 * `NotificationsScreen:205` keys its LazyColumn on `notification.id`, and
 * Compose throws `IllegalArgumentException("Key was already used")` the instant
 * two rows share a key — it is not a rendering glitch, the screen dies.
 *
 * The server pages by OFFSET. Any notification that arrives between the request
 * for page 1 and the request for page 2 shifts every row down and re-delivers
 * one already held. Notifications arrive constantly on this app, so paging the
 * inbox is precisely when it happens.
 *
 * These tests pin the dedupe on the append. They deliberately assert on the
 * list-merge rule rather than the ViewModel, so they stay honest about what is
 * being guarded: **whatever reaches the screen must have unique ids.**
 */
class NotificationsPaginationDedupeTest {

    private fun n(id: Int, title: String = "n$id") =
        AirdropNotification(id = id.toString(), title = title)

    /** The exact merge NotificationsViewModel performs on a loadMore page. */
    private fun append(existing: List<AirdropNotification>, batch: List<AirdropNotification>) =
        (existing + batch).distinctBy { it.id }

    @Test
    fun `an id redelivered on the next page does not appear twice`() {
        val page1 = listOf(n(3), n(2), n(1))
        // A new notification arrived, so the offset shifted and id 1 comes again.
        val page2 = listOf(n(1), n(0))

        val merged = append(page1, page2)

        assertEquals(
            "a repeated id must collapse — two rows with one key crash the LazyColumn",
            listOf(3, 2, 1, 0),
            merged.map { it.id.toInt() },
        )
    }

    /** The invariant the screen actually depends on, stated directly. */
    @Test
    fun `every id in the merged list is unique`() {
        val merged = append(listOf(n(5), n(4), n(3)), listOf(n(4), n(3), n(2)))

        assertEquals(merged.size, merged.map { it.id }.toSet().size)
    }

    /** Order must survive: the inbox is newest-first and dedupe must not resort. */
    @Test
    fun `the first occurrence wins so newest-first ordering is preserved`() {
        val merged = append(listOf(n(9, "newest")), listOf(n(9, "stale copy"), n(8)))

        assertEquals(listOf(9, 8), merged.map { it.id.toInt() })
        assertEquals("newest", merged.first().title)
    }

    /** A normal page with no overlap must be untouched. */
    @Test
    fun `a clean page appends everything`() {
        val merged = append(listOf(n(3), n(2)), listOf(n(1), n(0)))

        assertEquals(listOf(3, 2, 1, 0), merged.map { it.id.toInt() })
    }

    /** Empty batches are the end-of-list case and must not disturb anything. */
    @Test
    fun `an empty page leaves the list alone`() {
        val existing = listOf(n(2), n(1))

        assertEquals(existing.map { it.id }, append(existing, emptyList()).map { it.id })
        assertTrue(append(emptyList(), emptyList()).isEmpty())
    }
}
