package com.ga.airdrop.feature.shop

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ShopRecentSearches.pushRecent] parity with Swift saveRecentSearch (§C.7):
 * newest-first, case-insensitive dedupe, capped at [ShopRecentSearches.MAX].
 */
class ShopRecentSearchesTest {

    @Test
    fun `inserts the new query at the front`() {
        assertEquals(
            listOf("laptop", "phone"),
            ShopRecentSearches.pushRecent(listOf("phone"), "laptop"),
        )
    }

    @Test
    fun `dedupes case-insensitively, keeping the new casing at the front`() {
        assertEquals(
            listOf("iPhone", "laptop"),
            ShopRecentSearches.pushRecent(listOf("laptop", "IPHONE"), "iPhone"),
        )
    }

    @Test
    fun `caps at MAX dropping the oldest`() {
        val full = listOf("a", "b", "c", "d", "e") // MAX = 5
        assertEquals(
            listOf("new", "a", "b", "c", "d"),
            ShopRecentSearches.pushRecent(full, "new"),
        )
    }

    @Test
    fun `blank query leaves the list unchanged`() {
        val list = listOf("phone", "laptop")
        assertEquals(list, ShopRecentSearches.pushRecent(list, "   "))
    }

    @Test
    fun `query is trimmed before storing`() {
        assertEquals(listOf("tv"), ShopRecentSearches.pushRecent(emptyList(), "  tv  "))
    }
}
