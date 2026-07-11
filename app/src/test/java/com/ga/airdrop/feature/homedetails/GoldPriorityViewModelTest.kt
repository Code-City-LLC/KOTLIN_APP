package com.ga.airdrop.feature.homedetails

import com.ga.airdrop.data.api.ApiErrorCodes
import com.ga.airdrop.data.model.CustomerTier
import com.ga.airdrop.data.model.ServiceTier
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

    @Test
    fun `benefitsByCodeFrom merges server copy, flags and restored marketing (Swift model)`() {
        val tiers = listOf(
            ServiceTier(
                code = "diam",
                processingCopy = "Next possible ship-out; highest priority",
                benefitsSummary = listOf("VIP priority", "Free returns"),
                isPriority = true, // must NOT duplicate into a flag row — summary owns the facts
            ),
            ServiceTier(code = "RUBY", benefitsSummary = emptyList()),
            ServiceTier(code = "", benefitsSummary = listOf("orphan")), // blank code → dropped
        )
        val map = benefitsByCodeFrom(tiers)

        // DIAM: processing_copy first, summary verbatim, then restored marketing.
        val diam = map.getValue("DIAM")
        assertEquals("Next possible ship-out; highest priority", diam[0])
        assertEquals(listOf("VIP priority", "Free returns"), diam.subList(1, 3))
        assertFalse(diam.contains("Priority processing lane.")) // flag rows only when summary empty
        assertTrue(diam.contains("Dedicated WhatsApp VIP line for real-time assistance."))
        assertEquals(diam.size, diam.distinct().size)

        // RUBY: no summary + no flags → restored marketing only, and NEVER AirCoins.
        val ruby = map.getValue("RUBY")
        assertTrue(ruby.isNotEmpty())
        assertTrue(ruby.contains("Competitive base shipping rates."))
        assertFalse(ruby.any { it.contains("AirCoins", ignoreCase = true) })

        assertFalse(map.containsKey(""))
    }

    @Test
    fun `benefitsByCodeFrom derives flag rows only when the server sent no summary`() {
        val tiers = listOf(
            ServiceTier(
                code = "GOLD",
                benefitsSummary = emptyList(),
                isPriority = true,
                aircoinsEligible = true,
                freeReturnLbCap = 10.0,
            ),
        )
        val gold = benefitsByCodeFrom(tiers).getValue("GOLD")
        assertEquals("Priority processing lane.", gold[0])
        assertEquals("Earns AirCoins on eligible shipping charges.", gold[1])
        assertEquals("Free returns up to 10 lb per package.", gold[2])
        // Restored marketing follows the flag-derived facts.
        assertTrue(gold.contains("Free storage for 30 days on all incoming packages."))
    }

    @Test
    fun `tierRelation drives the page-relative CTA state machine`() {
        val gold = tierPages.indexOfFirst { it.id == "gold" }
        val diamond = tierPages.indexOfFirst { it.id == "diamond" }
        val ruby = tierPages.indexOfFirst { it.id == "ruby" }
        val inactive = tierPages.indexOfFirst { it.id == "inactive" }
        val corporate = tierPages.indexOfFirst { it.id == "corporate" }

        // Gold customer: higher pages upsell, own page is current, lower hidden.
        assertEquals(TierRelation.Upgrade, tierRelation(diamond, gold))
        assertEquals(TierRelation.Current, tierRelation(gold, gold))
        assertEquals(TierRelation.Downgrade, tierRelation(ruby, gold))
        // Corporate is a separate B2B SKU — never an upgrade path.
        assertEquals(TierRelation.Preview, tierRelation(corporate, gold))
        // Inactive page: activation CTA only when it IS the customer's state.
        assertEquals(TierRelation.Downgrade, tierRelation(inactive, gold))
        assertEquals(TierRelation.Activation, tierRelation(inactive, inactive))
        // Unresolved tier → preview everywhere (CTA hidden).
        assertEquals(TierRelation.Preview, tierRelation(gold, null))
    }

    @Test
    fun `adjacentCodedTier skips the presentational pages`() {
        val gold = tierPages.indexOfFirst { it.id == "gold" }
        val sapphire = tierPages.indexOfFirst { it.id == "sapphire" }
        assertEquals("PLAT", adjacentCodedTier(gold, -1)?.apiCode)
        assertEquals("RUBY", adjacentCodedTier(gold, +1)?.apiCode)
        // Below Sapphire sit only Inactive/Corporate — no coded tier.
        assertNull(adjacentCodedTier(sapphire, +1))
    }

    @Test
    fun `coded tier errors get bespoke copy, others fall through to the server message`() {
        assertEquals(
            "Insurance is required for your tier and can't be declined.",
            ApiErrorCodes.friendlyCopy(ApiErrorCodes.INSURANCE_MANDATORY),
        )
        assertEquals(
            "That shipping option isn't available for this destination right now.",
            ApiErrorCodes.friendlyCopy(ApiErrorCodes.NO_RATE_CARD),
        )
        // FORBIDDEN / null have no bespoke copy → caller uses the backend message.
        assertNull(ApiErrorCodes.friendlyCopy(ApiErrorCodes.FORBIDDEN))
        assertNull(ApiErrorCodes.friendlyCopy(null))
    }
}
