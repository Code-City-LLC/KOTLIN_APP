package com.ga.airdrop.feature.homedetails

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accepted Swift contract: one screen-level fade is visible on every active
 * tier page. CTA pages include the 74dp control lane; CTA-less pages retain
 * the 64dp dissolve instead of removing it.
 */
class TierFadeContractTest {

    @Test
    fun lastRowClearsTheFadeBandOnCtaPages() {
        // Content bottom offset (padding) must exceed the transparent CTA
        // strip + the dissolve band. Nav-bar insets add equally to both
        // sides of the inequality at runtime, so they cancel here.
        assertTrue(
            "bottom padding must clear CTA strip + fade band",
            TierBottomPaddingWithCta >= TierCtaClearance + TierFadeHeight,
        )
    }

    @Test
    fun ctaLessPagesStillReserveTheScreenLevelFade() {
        assertEquals(64.dp, TierBottomPaddingNoCta)
        assertTrue(TierBottomPaddingNoCta >= TierFadeHeight)
    }
}
