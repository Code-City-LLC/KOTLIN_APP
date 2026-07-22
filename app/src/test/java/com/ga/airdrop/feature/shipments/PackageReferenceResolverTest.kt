package com.ga.airdrop.feature.shipments

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageReferenceResolverTest {

    private val packages = listOf(
        ShipmentPackage(
            id = 42,
            trackingCode = "ARD00000000042",
            courierNumber = "1Z-EXACT-42",
        ),
        ShipmentPackage(
            id = 77,
            trackingCode = "TRACK-77",
            courierNumber = "COURIER-77",
        ),
    )

    @Test
    fun `positive id and exact aliases resolve the same positive package`() {
        assertEquals(42, exactPackageReferenceMatch(packages, "42")?.id)
        assertEquals(42, exactPackageReferenceMatch(packages, "ard00000000042")?.id)
        assertEquals(42, exactPackageReferenceMatch(packages, "1z-exact-42")?.id)
    }

    @Test
    fun `arbitrary alias digits never fabricate a package id`() {
        assertNull(exactPackageReferenceMatch(packages, "UPS-42"))
    }

    @Test
    fun `ambiguous exact alias fails closed`() {
        val ambiguous = packages + ShipmentPackage(id = 88, trackingCode = "TRACK-77")

        assertNull(exactPackageReferenceMatch(ambiguous, "TRACK-77"))
    }

    @Test
    fun `non-positive result rows are never resolved`() {
        assertNull(
            exactPackageReferenceMatch(
                listOf(ShipmentPackage(id = 0, trackingCode = "TRACK-ZERO")),
                "TRACK-ZERO",
            ),
        )
    }

    @Test
    fun `pagination metadata reaches an exact alias on page two after row twenty`() = runTest {
        val alias = "TRACK-PAGE-TWO"
        val pageOne = List(20) { index ->
            ShipmentPackage(id = 1_000 + index, trackingCode = "OTHER-$index")
        }
        val repository = FakePagedPackagesRepository { page ->
            when (page) {
                1 -> Paged(pageOne, isLastPage = false)
                2 -> Paged(
                    listOf(ShipmentPackage(id = 42, trackingCode = alias)),
                    isLastPage = true,
                )
                else -> error("Unexpected page $page")
            }
        }

        val rows = packageReferenceSearchRows(repository, alias).getOrThrow()

        assertEquals(listOf(1, 2), repository.pageRequests)
        assertEquals(listOf(50, 50), repository.perPageRequests)
        assertEquals(42, exactPackageReferenceMatch(rows, alias)?.id)
    }

    @Test
    fun `exact aliases split across pages remain ambiguous and fail closed`() = runTest {
        val alias = "TRACK-AMBIGUOUS"
        val repository = FakePagedPackagesRepository { page ->
            when (page) {
                1 -> Paged(
                    listOf(ShipmentPackage(id = 42, trackingCode = alias)) +
                        List(19) { index ->
                            ShipmentPackage(id = 2_000 + index, trackingCode = "OTHER-$index")
                        },
                    isLastPage = false,
                )
                2 -> Paged(
                    listOf(ShipmentPackage(id = 43, courierNumber = alias)),
                    isLastPage = true,
                )
                else -> error("Unexpected page $page")
            }
        }

        val rows = packageReferenceSearchRows(repository, alias).getOrThrow()

        assertEquals(listOf(1, 2), repository.pageRequests)
        assertNull(exactPackageReferenceMatch(rows, alias))
    }

    @Test
    fun `alias lookup is bounded to ten pages even when metadata never closes`() = runTest {
        val repository = FakePagedPackagesRepository {
            Paged(emptyList(), isLastPage = false)
        }

        packageReferenceSearchRows(repository, "TRACK-MISSING").getOrThrow()

        assertEquals((1..10).toList(), repository.pageRequests)
    }

    private class FakePagedPackagesRepository(
        private val pageProvider: (Int) -> Paged<ShipmentPackage>,
    ) : ShipmentsPackagesRepository {
        val pageRequests = mutableListOf<Int>()
        val perPageRequests = mutableListOf<Int>()

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<Paged<ShipmentPackage>> {
            pageRequests += page
            perPageRequests += perPage
            return Result.success(pageProvider(page))
        }

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(IllegalStateException("Not used"))

        override suspend fun packageStatuses() = Result.success(emptyList<PackageStatusInfo>())

        override suspend fun uploadInvoices(
            packageId: String,
            files: List<InvoiceUploadFile>,
        ) = Result.success(Unit)

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.success(Unit)
    }
}
