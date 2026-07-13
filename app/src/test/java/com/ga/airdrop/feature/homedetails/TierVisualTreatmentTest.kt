package com.ga.airdrop.feature.homedetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierVisualTreatmentTest {

    @Test
    fun unresolvedAndNonCurrentPagesHaveNoFadeAndNoCtaReserve() {
        tierPages.indices.forEach { pageIndex ->
            assertNoCtaTreatment(tierPageVisualTreatment(pageIndex, resolvedTierIndex = null))
        }

        val currentPage = tierPages.indexOfFirst { it.id == "gold" }
        tierPages.indices
            .filterNot { it == currentPage }
            .forEach { pageIndex ->
                assertNoCtaTreatment(
                    tierPageVisualTreatment(pageIndex, resolvedTierIndex = currentPage)
                )
            }
    }

    @Test
    fun currentTierPredicateOwnsCtaFadeAndReservedClearance() {
        tierPages.indices.forEach { pageIndex ->
            val treatment = tierPageVisualTreatment(pageIndex, resolvedTierIndex = pageIndex)

            assertTrue(treatment.pageHasCta)
            assertTrue(treatment.appliesBottomFade)
            assertTrue(treatment.reservesCtaSpace)
            assertEquals(TierBottomPaddingWithCta, treatment.contentBottomPadding)
            assertEquals(
                TierNormalBottomPadding + TierCtaContentClearance,
                treatment.contentBottomPadding,
            )
            assertEquals(
                TierCtaHeight + TierCtaBottomGap + TierCtaViewportGap,
                TierCtaViewportInset,
            )
        }
    }

    @Test
    fun sevenPagesKeepDistinctTierGlyphs() {
        assertEquals(7, tierPages.size)
        assertEquals(7, tierPages.map { it.id }.distinct().size)
        assertEquals(7, tierPages.map { it.glyphRes }.distinct().size)
    }

    private fun assertNoCtaTreatment(treatment: TierPageVisualTreatment) {
        assertFalse(treatment.pageHasCta)
        assertFalse(treatment.appliesBottomFade)
        assertFalse(treatment.reservesCtaSpace)
        assertEquals(TierNormalBottomPadding, treatment.contentBottomPadding)
        assertEquals(24f, treatment.contentBottomPadding.value, 0f)
    }
}
