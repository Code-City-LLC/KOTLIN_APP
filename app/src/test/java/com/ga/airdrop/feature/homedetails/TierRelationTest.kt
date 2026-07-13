package com.ga.airdrop.feature.homedetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure coverage for the tier CTA state machine + benefit-row resolution
 * (CoralCove #22431 blocker 3): relation matrix, CTA labels/visibility,
 * static Inactive/Corporate fallback (Kemar ruling #22424), and the
 * breakdown sheet's nearest-REAL-tier targets.
 *
 * Page order (top→bottom): diamond(0) platinum(1) gold(2) ruby(3)
 * sapphire(4) inactive(5) corporate(6).
 */
class TierRelationTest {

    private val diamond = tierPages.indexOfFirst { it.id == "diamond" }
    private val gold = tierPages.indexOfFirst { it.id == "gold" }
    private val ruby = tierPages.indexOfFirst { it.id == "ruby" }
    private val sapphire = tierPages.indexOfFirst { it.id == "sapphire" }
    private val inactive = tierPages.indexOfFirst { it.id == "inactive" }
    private val corporate = tierPages.indexOfFirst { it.id == "corporate" }

    // ── relation matrix (Swift relation(forNavigatingTo:)) ──

    @Test
    fun ownPageIsCurrent() {
        assertEquals(TierRelation.CURRENT, tierRelation(ruby, ruby))
    }

    @Test
    fun higherPageIsUpgrade_lowerPageIsDowngrade() {
        // Ruby customer browsing Gold (one above) / Sapphire (one below).
        assertEquals(TierRelation.UPGRADE, tierRelation(gold, ruby))
        assertEquals(TierRelation.DOWNGRADE, tierRelation(sapphire, ruby))
        // And the extremes.
        assertEquals(TierRelation.UPGRADE, tierRelation(diamond, ruby))
    }

    @Test
    fun unresolvedUserIsPreviewEverywhereExceptInactive() {
        assertEquals(TierRelation.PREVIEW, tierRelation(gold, null))
        assertEquals(TierRelation.PREVIEW, tierRelation(corporate, null))
        // Inactive page for an unresolved user is a lower page, never activation.
        assertEquals(TierRelation.DOWNGRADE, tierRelation(inactive, null))
    }

    @Test
    fun inactivePageActivatesOnlyForInactiveCustomers() {
        assertEquals(TierRelation.ACTIVATION, tierRelation(inactive, inactive))
        assertEquals(TierRelation.DOWNGRADE, tierRelation(inactive, ruby))
    }

    @Test
    fun corporateIsPreviewFromEitherSide() {
        // Browsing the corporate page as a normal customer…
        assertEquals(TierRelation.PREVIEW, tierRelation(corporate, ruby))
        // …and browsing normal pages AS a corporate customer.
        assertEquals(TierRelation.PREVIEW, tierRelation(gold, corporate))
    }

    // ── CTA labels / visibility ──

    @Test
    fun ctaLabelsMatchSwiftStates() {
        assertEquals("Your Tier", tierCtaLabel(TierRelation.CURRENT, "Ruby Starter"))
        assertEquals(
            "Upgrade to Gold Standard",
            tierCtaLabel(TierRelation.UPGRADE, "Gold Standard"),
        )
        assertEquals(
            "Ship a package now to activate your account",
            tierCtaLabel(TierRelation.ACTIVATION, "Inactive"),
        )
        assertEquals(
            "Loading your tier…",
            tierCtaLabel(
                TierRelation.PREVIEW,
                "Gold Standard",
                TierResolutionStatus.Loading,
            ),
        )
        assertEquals(
            "Retry tier details",
            tierCtaLabel(
                TierRelation.PREVIEW,
                "Gold Standard",
                TierResolutionStatus.Failed,
            ),
        )
    }

    @Test
    fun currentSwiftHidesDowngradeAndPreviewCtas() {
        assertNull(tierCtaLabel(TierRelation.DOWNGRADE, "Sapphire Saver"))
        assertNull(tierCtaLabel(TierRelation.PREVIEW, "Corporate"))
    }

    // ── benefit-row resolution (server copy + static legacy pages) ──

    @Test
    fun apiTiersUseServerCopyOnly() {
        val rows = mapOf("RUBY" to listOf("Server row"))
        assertEquals(
            listOf("Server row"),
            benefitRowsForPage(tierPages[ruby], rows),
        )
        // Missing server entry for an API tier → null (unavailable state),
        // NEVER a hardcoded fallback.
        assertNull(benefitRowsForPage(tierPages[gold], rows))
    }

    @Test
    fun inactiveAndCorporateShowStaticInfo() {
        // Kemar ruling #22424: these pages MUST display their benefit info.
        assertEquals(4, benefitRowsForPage(tierPages[inactive], emptyMap())?.size)
        assertEquals(7, benefitRowsForPage(tierPages[corporate], emptyMap())?.size)
    }

    // ── offer-driven change gating (CoralCove #22805) ──

    private fun offer(code: String, direction: String, rank: Int? = null, current: Boolean = false) =
        com.ga.airdrop.data.model.TierChangeOption(
            code = code, name = code, laneRank = rank, isCurrent = current, direction = direction,
        )

    @Test
    fun canChangeFalseYieldsNoTargetsAndNoLegalPatch() {
        val offers = listOf(offer("GOLD", "upgrade", 3))
        assertNull(offerTargetPage(offers, canChange = false, upward = true))
        assertNull(offerTargetPage(offers, canChange = false, upward = false))
        org.junit.Assert.assertFalse(isOfferedChange(offers, canChange = false, code = "GOLD"))
    }

    @Test
    fun partialOffersOnlyProduceTheOfferedDirection() {
        // Backend offers upgrades only — no downgrade button may appear.
        val offers = listOf(offer("GOLD", "upgrade", 3), offer("PLAT", "upgrade", 4))
        assertEquals("gold", offerTargetPage(offers, canChange = true, upward = true)?.id)
        assertNull(offerTargetPage(offers, canChange = true, upward = false))
    }

    @Test
    fun nearestTargetsPickedByLaneRankNotPageOrder() {
        // Nearest upgrade = LOWEST lane_rank among upgrades; nearest
        // downgrade = HIGHEST lane_rank among downgrades.
        val offers = listOf(
            offer("DIAM", "upgrade", 5),
            offer("GOLD", "upgrade", 3),
            offer("SAVR", "downgrade", 1),
        )
        assertEquals("gold", offerTargetPage(offers, canChange = true, upward = true)?.id)
        assertEquals("sapphire", offerTargetPage(offers, canChange = true, upward = false)?.id)
    }

    @Test
    fun offerDirectionIsAuthoritativeOverPageOrder() {
        // Backend can declare a code as a DOWNGRADE even if page order would
        // call it an upgrade — the offer's direction wins.
        val offers = listOf(offer("PLAT", "downgrade", 4))
        assertNull(offerTargetPage(offers, canChange = true, upward = true))
        assertEquals("platinum", offerTargetPage(offers, canChange = true, upward = false)?.id)
    }

    @Test
    fun patchGateRefusesUnofferedAndCurrentCodes() {
        val offers = listOf(offer("GOLD", "upgrade", 3), offer("RUBY", "same", 2, current = true))
        // Offered, non-current: legal.
        org.junit.Assert.assertTrue(isOfferedChange(offers, canChange = true, code = "gold"))
        // Not in the offer list: refused (page order never authorizes).
        org.junit.Assert.assertFalse(isOfferedChange(offers, canChange = true, code = "DIAM"))
        // Current tier: refused.
        org.junit.Assert.assertFalse(isOfferedChange(offers, canChange = true, code = "RUBY"))
        // Null code (legacy pages): refused.
        org.junit.Assert.assertFalse(isOfferedChange(offers, canChange = true, code = null))
        // Non-current offer with direction "same"/malformed: refused
        // (#22867-6) — only explicit upgrade/downgrade offers authorize.
        val weird = listOf(offer("PLAT", "same", 4), offer("DIAM", "sideways", 5))
        org.junit.Assert.assertFalse(isOfferedChange(weird, canChange = true, code = "PLAT"))
        org.junit.Assert.assertFalse(isOfferedChange(weird, canChange = true, code = "DIAM"))
        // And such offers never label a sheet either (#22867-4).
        assertNull(offerDirectionIsUpgrade(weird, "PLAT"))
    }
}
