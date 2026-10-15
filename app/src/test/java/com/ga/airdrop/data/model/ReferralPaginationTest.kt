package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferralPaginationTest {
    @Test fun referralAliasesPreserveTheActualLastPage() {
        val page = AirdropJson.decodeFromString<Paginated<ReferredFriend>>(
            """{"data":[{"id":21}],"pagination":{"current_page":2,"per_page":20,"total_records":41,"total_pages":3,"has_next_page":true}}""",
        )
        assertEquals(21, page.items.single().id)
        assertEquals(41, page.pagination?.total)
        assertEquals(3, page.pagination?.lastPage)
        assertEquals(2, page.pagination?.currentPage)
    }

    @Test fun standardPaginationAndUnreadCountsRemainCanonical() {
        val page = AirdropJson.decodeFromString<Paginated<ReferredFriend>>(
            """{"data":[{"id":1}],"meta":{"current_page":1,"per_page":20,"total":5,"last_page":1,"unread_count":4}}""",
        )
        assertEquals(Pagination(currentPage=1, perPage=20, total=5, lastPage=1, unreadCount=4), page.pagination)
    }

    @Test fun standardKeysWinWhenBothAliasesArePresent() {
        val page = AirdropJson.decodeFromString<Paginated<ReferredFriend>>(
            """{"data":[],"pagination":{"total":5,"total_records":900,"last_page":1,"total_pages":45}}""",
        )
        assertEquals(5, page.pagination?.total)
        assertEquals(1, page.pagination?.lastPage)
    }

    @Test fun metadataWithoutPagingStillUsesTheExistingFallback() {
        val page = AirdropJson.decodeFromString<Paginated<ReferredFriend>>(
            """{"data":[{"id":1}],"meta":{"timestamp":"2026-10-15"}}""",
        )
        assertNull(page.pagination)
    }
}
