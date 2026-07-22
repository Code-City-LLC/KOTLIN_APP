package com.ga.airdrop.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageDeepLinkReferenceTest {

    @Test
    fun `positive package id wins and is canonicalized`() {
        assertEquals(
            "42",
            PackageDeepLinkReference.select(
                packageIdCandidates = listOf("0", "0042"),
                aliasCandidates = listOf("TRACK-42"),
            ),
        )
    }

    @Test
    fun `invalid ids fall back to exact tracking alias`() {
        assertEquals(
            "TRACK-42",
            PackageDeepLinkReference.select(
                packageIdCandidates = listOf("0", "-7"),
                aliasCandidates = listOf("0", "TRACK-42"),
            ),
        )
    }

    @Test
    fun `zero and negative references are never routable package ids`() {
        assertNull(PackageDeepLinkReference.routeReference("0"))
        assertNull(PackageDeepLinkReference.routeReference("-7"))
        assertNull(PackageDeepLinkReference.routeReference("  "))
    }

    @Test
    fun `overflowing all digit reference fails closed instead of becoming an alias`() {
        val overflow = "999999999999999999999999"

        assertNull(PackageDeepLinkReference.routeReference(overflow))
        assertNull(PackageDeepLinkReference.positiveId(overflow))
        assertNull(
            PackageDeepLinkReference.select(
                packageIdCandidates = listOf(overflow),
                aliasCandidates = listOf(overflow),
            ),
        )
    }
}
