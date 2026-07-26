package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.PackageTimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The temporary ordering guard Kemar asked for while the projection's ORDER BY
 * is unconfirmed. Its job is to be a no-op on every real payload and to fire
 * only on the one documented defect — see [TrackJourney.applyOrderGuard].
 */
class TrackJourneyOrderGuardTest {

    private fun e(label: String, iso: String?, state: String = "done") = PackageTimelineEntry(
        key = label, status = null, label = label, icon = null,
        at = null, atIso = iso, state = state, source = "status",
    )

    /** The real pre-staging payload for 153907 — already ordered, must not move. */
    @Test
    fun `a correctly ordered live payload is returned untouched`() {
        val live = listOf(
            e("Shipment Received", "2026-07-07T20:10:07-04:00"),
            e("Port of Departure -MIA", "2026-07-11T20:10:07-04:00"),
            e("Arrived at Port -JAM", "2026-07-16T20:10:07-04:00"),
            e("Released From Customs", "2026-07-17T20:10:07-04:00"),
            e("Processing at our Warehouse", "2026-07-18T20:10:07-04:00"),
            e("Ready for Pickup", "2026-07-20T20:10:07-04:00"),
            e("Preparing for Dispatch", "2026-07-22T09:28:54-04:00"),
            e("Out for Delivery", "2026-07-23T09:28:54-04:00"),
            e("Delivered", "2026-07-23T20:10:07-04:00"),
        )
        assertEquals(live, TrackJourney.applyOrderGuard(live))
    }

    /**
     * The defect it exists for: a legacy `YYYY/MM/DD` row string-sorts after
     * every `YYYY-MM-DD` row, so the server can send the FIRST event last.
     * at_iso still says when it really happened.
     */
    @Test
    fun `a legacy row the varchar sorted last is put back where it happened`() {
        val misordered = listOf(
            e("Port of Departure -MIA", "2026-07-11T20:10:07-04:00"),
            e("Shipment Received", "2024-03-02T08:00:00-05:00"), // stored as 2024/03/02
        )
        assertEquals(
            listOf("Shipment Received", "Port of Departure -MIA"),
            TrackJourney.applyOrderGuard(misordered).map { it.label },
        )
    }

    /** Trailing pending steps carry no timestamp and stay at the end. */
    @Test
    fun `an undated pending step keeps its place at the end`() {
        val withPending = listOf(
            e("Out for Delivery", "2026-07-23T09:28:54-04:00"),
            e("Shipment Received", "2024-03-02T08:00:00-05:00"),
            e("Delivered", null, state = "pending"),
        )
        assertEquals(
            listOf("Shipment Received", "Out for Delivery", "Delivered"),
            TrackJourney.applyOrderGuard(withPending).map { it.label },
        )
    }

    /** Shapes it cannot read are passed through, never rearranged on a guess. */
    @Test
    fun `an unreadable shape is passed through rather than guessed at`() {
        val undatedInTheMiddle = listOf(
            e("B", "2026-07-11T20:10:07-04:00"),
            e("gap", null),
            e("A", "2024-03-02T08:00:00-05:00"),
        )
        assertEquals(undatedInTheMiddle, TrackJourney.applyOrderGuard(undatedInTheMiddle))

        val unparseable = listOf(e("B", "11/07/2026"), e("A", "2024-03-02T08:00:00-05:00"))
        assertEquals(unparseable, TrackJourney.applyOrderGuard(unparseable))

        val noneDated = listOf(e("B", null), e("A", null))
        assertEquals(noneDated, TrackJourney.applyOrderGuard(noneDated))
    }
}
