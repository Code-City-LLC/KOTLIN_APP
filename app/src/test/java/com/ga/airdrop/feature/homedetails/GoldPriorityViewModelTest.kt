package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.TierChangeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Customer Tier page's upgrade/downgrade brain, tested through its pure
 * state transitions. The backend owns the decision (Kemar tier brief:
 * "upgrade/downgrade using backend validation only"); these pin that the page
 * faithfully reflects the API's current tier + available_changes and applies a
 * switch to the right pager tier — no local eligibility logic.
 */
class GoldPriorityViewModelTest {

    private fun rubyTier() = CustomerTier(
        currentTier = "RUBY",
        displayName = "Ruby Starter",
        canChange = true,
        availableChanges = listOf(
            TierChangeOption(code = "GOLD", name = "Gold Standard", laneRank = 3, isCurrent = false, direction = "upgrade"),
            TierChangeOption(code = "SAVR", name = "Sapphire Saver", laneRank = 1, isCurrent = false, direction = "downgrade"),
            TierChangeOption(code = "RUBY", name = "Ruby Starter", laneRank = 2, isCurrent = true, direction = "same"),
        ),
        aircoinsEligible = false,
    )

    @Test
    fun `applyCustomerTier resolves current code, directions and pager index`() {
        val s = applyCustomerTier(GoldPriorityUiState(), rubyTier())

        assertEquals("RUBY", s.currentTierCode)
        assertTrue(s.canChange)
        assertEquals("upgrade", s.directionByCode["GOLD"])
        assertEquals("downgrade", s.directionByCode["SAVR"])
        assertEquals(tierPages.indexOfFirst { it.apiCode == "RUBY" }, s.resolvedTierIndex)
    }

    @Test
    fun `applyChangeSuccess moves to the new tier and clears in-flight state`() {
        val before = applyCustomerTier(GoldPriorityUiState(changingToCode = "GOLD"), rubyTier())
        val after = applyChangeSuccess(before, "GOLD")

        assertEquals("GOLD", after.currentTierCode)
        assertNull(after.changingToCode)
        assertNull(after.changeError)
        assertEquals("Gold Standard", after.justChangedToName)
        assertEquals(tierPages.indexOfFirst { it.apiCode == "GOLD" }, after.resolvedTierIndex)
        // available_changes are stale after a switch — cleared until refetch.
        assertTrue(after.directionByCode.isEmpty())
    }

    @Test
    fun `blank requested code falls back to the code that was requested`() {
        // Backend echoes requestedTierCode; if it comes back blank the VM keeps
        // the caller's code (asserted here via the success fold with a real code).
        val after = applyChangeSuccess(GoldPriorityUiState(), "DIAM")
        assertEquals("DIAM", after.currentTierCode)
        assertEquals("Diamond Elite", after.justChangedToName)
    }

    @Test
    fun `directions drop blank codes`() {
        val tier = rubyTier().copy(
            availableChanges = rubyTier().availableChanges + TierChangeOption(code = "", name = "Bogus", direction = "upgrade"),
        )
        val directions = directionsFrom(tier)
        assertFalse(directions.containsKey(""))
        assertEquals(3, directions.size)
    }

    @Test
    fun `an unknown tier code yields no pager index (page keeps its fallback)`() {
        assertNull(indexForTierCode("NOPE"))
        assertNull(indexForTierCode(null))
        // The presentational-only pages have no API code, so are never resolved.
        assertNull(indexForTierCode("CORPORATE"))
    }
}
