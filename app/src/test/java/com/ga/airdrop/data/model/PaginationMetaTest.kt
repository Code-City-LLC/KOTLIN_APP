package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Laravel answers list endpoints with EITHER a nested `pagination` object or a
 * top-level `meta`. The notifications inbox uses `meta` — and that is where it
 * publishes `unread_count`, which drives the header bell badge. Reading only
 * `pagination` silently produced a null count and no badge.
 */
class PaginationMetaTest {

    private fun decode(json: String) =
        AirdropJson.decodeFromString(PaginatedSerializer(String.serializer()), json)

    @Test
    fun unreadCountIsReadFromLaravelMeta() {
        // The real shape returned by GET /user/notifications.
        val page = decode(
            """
            {"success":true,"data":["a"],
             "meta":{"current_page":1,"per_page":1,"total":23,"last_page":23,"unread_count":13}}
            """.trimIndent()
        )
        assertEquals(1, page.items.size)
        assertEquals(13, page.pagination?.unreadCount)
        assertEquals(23, page.pagination?.total)
    }

    @Test
    fun nestedPaginationStillWins() {
        val page = decode(
            """
            {"data":{"items":["a"],"pagination":{"current_page":2,"total":9}}}
            """.trimIndent()
        )
        assertEquals(2, page.pagination?.currentPage)
        assertEquals(9, page.pagination?.total)
    }

    @Test
    fun envelopeMetaWithoutPaginationFieldsIsNotMistakenForPagination() {
        // The delivery envelope's top-level meta is {timestamp, version} — it
        // must NOT masquerade as pagination.
        val page = decode(
            """
            {"data":["a"],"meta":{"timestamp":"2026-07-25T09:29:03-04:00","version":"1.0"}}
            """.trimIndent()
        )
        assertEquals(1, page.items.size)
        assertNull(page.pagination)
    }
}
