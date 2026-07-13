package com.ga.airdrop.feature.homedetails

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kemar directive 2026-07-12 (room #23392/#23407): the tier benefits scroll
 * dissolves into the tier gradient above the glass CTA. The mask must NEVER
 * permanently hide text — the scroll's bottom padding has to let the last
 * benefit row come to rest fully ABOVE the fade band. These proofs pin that
 * arithmetic so a future padding "cleanup" can't reintroduce hidden text.
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
    fun ctaLessPagesDoNotReserveOrRenderTheFadeLane() {
        // Current Swift applies its fixed viewport mask only while the CTA is
        // visible. CTA-less pages retain ordinary breathing room and no mask.
        assertEquals(32.dp, TierBottomPaddingNoCta)
        assertTrue(TierBottomPaddingNoCta < TierFadeHeight)
    }
}
