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
    }

    @Test
    fun ctaHiddenOnDowngradeAndPreview_neverADowngradeSign() {
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

    // ── breakdown-sheet targets skip presentational pages ──

    @Test
    fun nearestApiTierSkipsInactiveAndCorporate() {
        // Up from Ruby → Gold; down from Ruby → Sapphire.
        assertEquals("gold", nearestApiTier(ruby, upward = true)?.id)
        assertEquals("sapphire", nearestApiTier(ruby, upward = false)?.id)
        // Down from Sapphire skips Inactive AND Corporate → nothing below.
        assertNull(nearestApiTier(sapphire, upward = false))
        // Up from Diamond → nothing above.
        assertNull(nearestApiTier(diamond, upward = true))
    }
}
