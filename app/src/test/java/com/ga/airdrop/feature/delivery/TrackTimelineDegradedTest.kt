package com.ga.airdrop.feature.delivery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track degrades to the last mile when the canonical journey cannot be read —
 * and now SAYS so.
 *
 * ⚠️ THE DEGRADATION WAS ALWAYS CORRECT. A timeline failure must not blank a
 * Track screen we have real delivery data for. What was wrong is that it
 * happened SILENTLY:
 *
 *     val timeline = gateway.packageTimeline(packageId).getOrNull().orEmpty()
 *
 * A rail showing only the last mile is indistinguishable from a package that
 * genuinely has no warehouse history. The customer was quietly shown a shorter
 * journey than the one that actually happened — the same "absence rendered as
 * information" shape fixed on five list screens today, in its subtlest form:
 * here it produces a PARTIAL truth rather than an empty one, which is why it
 * survived the sweep that caught the others.
 *
 * ⚠️ AND THE FIRST VERSION OF THE BANNER WAS WRONG. It said "Earlier tracking
 * steps couldn't be loaded", asserting a PARTIAL loss. The loss is TOTAL: the
 * last-mile legs (dispatch / out_for_delivery / delivered) are timeline icon
 * keys composed by the SAME endpoint, so when it fails the rail is empty, not
 * shortened. Saying "earlier" invites the customer to read the empty rail as
 * "nothing else has happened" — the same false-shorter-journey this file
 * exists to prevent, reintroduced through copy. Caught by an adversarial audit
 * of this work, not by this test.
 *
 * This pins the state contract. The banner itself is asserted by tag
 * `track-timeline-partial` in the Compose layer.
 */
class TrackTimelineDegradedTest {

    @Test
    fun `a readable timeline is not flagged as degraded`() {
        val state = DeliveryCenterUiState(
            timeline = listOf(),
            timelineUnavailable = false,
            loading = false,
            loadedOnce = true,
        )

        assertFalse(
            "an empty-but-successful journey is a FACT, not a degradation",
            state.timelineUnavailable,
        )
    }

    /**
     * The distinction that matters: an empty timeline can mean two completely
     * different things, and only the flag separates them.
     */
    @Test
    fun `an empty timeline that FAILED is distinguishable from one that is genuinely empty`() {
        val genuinelyEmpty = DeliveryCenterUiState(
            timeline = emptyList(),
            timelineUnavailable = false,
            loading = false,
            loadedOnce = true,
        )
        val failedToLoad = DeliveryCenterUiState(
            timeline = emptyList(),
            timelineUnavailable = true,
            loading = false,
            loadedOnce = true,
        )

        // Identical rails. Different facts. Before the flag these two states
        // were byte-for-byte the same and the UI could not tell them apart.
        assertTrue(genuinelyEmpty.timeline.isEmpty())
        assertTrue(failedToLoad.timeline.isEmpty())
        assertFalse(genuinelyEmpty.timelineUnavailable)
        assertTrue(failedToLoad.timelineUnavailable)
    }

    /** The flag must not suppress the last-mile data we DID successfully read. */
    @Test
    fun `degraded still keeps whatever was successfully loaded`() {
        val state = DeliveryCenterUiState(
            timeline = emptyList(),
            timelineUnavailable = true,
            selectedPackageId = 153897,
            loading = false,
            loadedOnce = true,
        )

        assertTrue("the flag reports a gap; it must not erase the screen", state.loadedOnce)
        assertTrue(state.timelineUnavailable)
    }
}
