package com.ga.airdrop.feature.more2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The category page's tab bar and search (Kemar 2026-10-17: "the bar ... where
 * we can swipe from one item to the next instead of having to go back. And we
 * should also have the search bar."), from RN RestrictedItemsScreenView.
 */
class RestrictedCategoryTabsTest {

    @Test
    fun `tabs run in RN order with RN labels while the list keeps its names`() {
        assertEquals(
            listOf(
                "Permitted",
                "License-Required",
                "Restricted Commodities",
                "Prohibited Articles",
                "Conditional Commodities",
            ),
            RestrictedCategory.entries.map { it.tabLabel },
        )
        assertEquals("License Required", RestrictedCategory.LICENSE_REQUIRED.displayName)
        assertEquals("Prohibited", RestrictedCategory.PROHIBITED.displayName)
    }

    @Test
    fun `a swipe moves one category along and stops at either end`() {
        assertEquals(
            RestrictedCategory.LICENSE_REQUIRED,
            RestrictedCategory.PERMITTED.adjacent(1),
        )
        assertEquals(
            RestrictedCategory.PROHIBITED,
            RestrictedCategory.CONDITIONAL_COMMODITIES.adjacent(-1),
        )
        assertNull(RestrictedCategory.PERMITTED.adjacent(-1))
        assertNull(RestrictedCategory.CONDITIONAL_COMMODITIES.adjacent(1))
    }

    @Test
    fun `search matches an item's title or detail, ignoring case`() {
        val restricted = RestrictedCategory.RESTRICTED_COMMODITIES

        assertEquals(
            listOf("Lithium batteries and magnetised materials"),
            restricted.itemsMatching("LITHIUM").map { it.title },
        )
        // Found by its detail line alone.
        assertEquals(
            listOf("Lithium batteries and magnetised materials"),
            restricted.itemsMatching("power banks").map { it.title },
        )
        assertTrue(restricted.itemsMatching("zzqx").isEmpty())
    }

    @Test
    fun `a blank search keeps every item and only searches the open category`() {
        val permitted = RestrictedCategory.PERMITTED

        assertEquals(permitted.items, permitted.itemsMatching(""))
        assertEquals(permitted.items, permitted.itemsMatching("   "))
        // "Lithium" belongs to Restricted Commodities, not Permitted.
        assertTrue(permitted.itemsMatching("lithium").isEmpty())
        assertEquals(1, permitted.itemsMatching("  footwear ").size)
    }
}
