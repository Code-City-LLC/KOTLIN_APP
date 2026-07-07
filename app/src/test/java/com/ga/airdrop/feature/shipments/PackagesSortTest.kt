package com.ga.airdrop.feature.shipments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [sortPackages] parity with Swift FigmaPackagesViewController.applySortedOrder
 * (§B.4): the server already returns creation_date DESC so Newest = server order
 * and Oldest = that reversed; case-insensitive status/tracking A-Z with a
 * newest-id tiebreak; [PackagesSortStore.sortFor] falls back to NEWEST_FIRST.
 */
class PackagesSortTest {

    private fun pkg(
        id: Int,
        createdAt: String? = null,
        statusName: String? = null,
        trackingCode: String? = null,
    ) = ShipmentPackage(id = id, createdAt = createdAt, statusName = statusName, trackingCode = trackingCode)

    private val a = pkg(1, createdAt = "2026-01-01", statusName = "Delivered", trackingCode = "ARD2")
    private val b = pkg(2, createdAt = "2026-03-01", statusName = "arrived", trackingCode = "ard1")
    private val c = pkg(3, createdAt = "2026-02-01", statusName = "Customs", trackingCode = "ARD3")

    @Test
    fun `newest first preserves the server's creation-date-desc order`() {
        // The server already returns newest-first; the client must not reorder
        // (re-sorting stringly-typed timestamps can only diverge).
        assertEquals(listOf(1, 2, 3), sortPackages(listOf(a, b, c), PackagesSort.NEWEST_FIRST).map { it.id })
    }

    @Test
    fun `oldest first is the server order reversed`() {
        assertEquals(listOf(3, 2, 1), sortPackages(listOf(a, b, c), PackagesSort.OLDEST_FIRST).map { it.id })
    }

    @Test
    fun `status A-Z is case-insensitive with status fallback`() {
        val noName = pkg(4, statusName = null).copy(status = "Beta")
        assertEquals(
            listOf(2, 4, 3, 1), // arrived, Beta, Customs, Delivered
            sortPackages(listOf(a, b, c, noName), PackagesSort.STATUS_AZ).map { it.id },
        )
    }

    @Test
    fun `tracking A-Z is case-insensitive`() {
        assertEquals(listOf(2, 1, 3), sortPackages(listOf(a, b, c), PackagesSort.TRACKING_AZ).map { it.id })
    }

    @Test
    fun `sortFor resolves names and falls back to NEWEST_FIRST`() {
        assertEquals(PackagesSort.STATUS_AZ, PackagesSortStore.sortFor("STATUS_AZ"))
        assertEquals(PackagesSort.NEWEST_FIRST, PackagesSortStore.sortFor(null))
        assertEquals(PackagesSort.NEWEST_FIRST, PackagesSortStore.sortFor("bogus"))
    }
}
