package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.repo.TrackedDeliveryStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Laravel emits operations-facing stage labels (e.g. `"Driver Assigned"`).
 * A customer must never see them — not on the canonical path, and not on the
 * fallback path for an unrecognised server projection.
 */
class DeliveryStageLabelSafetyTest {

    private fun stage(key: String, label: String) =
        TrackedDeliveryStage(key = key, label = label, state = "done", at = null)

    @Test
    fun laravelDriverAssignedLabelIsNeverShownToCustomers() {
        // The exact payload Laravel ships today.
        assertEquals("Preparing for Dispatch", customerSafeStageLabelForTest(stage("assigned", "Driver Assigned")))
    }

    @Test
    fun knownKeysAlwaysUseApprovedCopyRegardlessOfServerLabel() {
        assertEquals("Out for Delivery", customerSafeStageLabelForTest(stage("out_for_delivery", "En route w/ driver")))
        assertEquals("Delivered", customerSafeStageLabelForTest(stage("delivered", "Dropped by courier")))
        assertEquals("Order Confirmed", customerSafeStageLabelForTest(stage("order_confirmed", "whatever")))
    }

    @Test
    fun unknownKeysWithDriverWordingAreScrubbed() {
        val out = customerSafeStageLabelForTest(stage("some_new_stage", "Driver en route"))
        assertFalse(out.lowercase().contains("driver"))
        assertFalse(out.lowercase().contains("courier"))
    }

    @Test
    fun benignUnknownLabelsPassThrough() {
        assertEquals("At Sorting Facility", customerSafeStageLabelForTest(stage("sorting", "At Sorting Facility")))
    }
}
