package com.ga.airdrop.feature.shipments

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageReferenceResolverTest {

    @Test
    fun `canonical numeric package id is a fallback after reference search`() = runTest {
        val repo = FakePackagesRepository()
        var shortlistCalls = 0

        val resolved = resolvePackageReference(
            reference = " 42 ",
            packagesRepo = repo,
            shortlist = {
                shortlistCalls += 1
                Result.success(emptyList())
            },
            numericIdIsCanonical = true,
        ).getOrThrow()

        assertEquals(42, resolved.packageId)
        assertNull(resolved.matchedPackage)
        assertEquals(listOf("42"), repo.searches)
        assertEquals(1, shortlistCalls)
    }

    @Test
    fun `numeric courier match outranks the same digits as a canonical package id`() = runTest {
        val packageWithCollidingId = ShipmentPackage(id = 42, trackingCode = "ADX-OTHER")
        val packageWithNumericCourier = ShipmentPackage(id = 7, courierNumber = "42")
        val repo = FakePackagesRepository(
            items = listOf(packageWithCollidingId, packageWithNumericCourier),
        )

        val resolved = resolvePackageReference(
            reference = "42",
            packagesRepo = repo,
            shortlist = { Result.success(emptyList()) },
            numericIdIsCanonical = true,
        ).getOrThrow()

        assertEquals(7, resolved.packageId)
        assertEquals(packageWithNumericCourier, resolved.matchedPackage)
        assertEquals(listOf("42"), repo.searches)
    }

    @Test
    fun `tracking and courier references require an exact match`() = runTest {
        val exactTracking = ShipmentPackage(id = 7, trackingCode = "ADX-240524")
        val exactCourier = ShipmentPackage(id = 8, courierNumber = "1Z999")
        val repo = FakePackagesRepository(
            items = listOf(
                ShipmentPackage(id = 6, trackingCode = "ADX-240524-OTHER"),
                exactTracking,
                exactCourier,
            ),
        )

        val tracking = resolvePackageReference(
            "adx-240524",
            repo,
            shortlist = { Result.success(emptyList()) },
            numericIdIsCanonical = true,
        ).getOrThrow()
        val courier = resolvePackageReference(
            "1z999",
            repo,
            shortlist = { Result.success(emptyList()) },
            numericIdIsCanonical = true,
        ).getOrThrow()

        assertEquals(7, tracking.packageId)
        assertEquals(exactTracking, tracking.matchedPackage)
        assertEquals(8, courier.packageId)
        assertEquals(exactCourier, courier.matchedPackage)
        assertEquals(listOf("ADX-240524", "1Z999"), repo.searches)
    }

    @Test
    fun `shortlist fallback keeps the same exact-match rule`() = runTest {
        val repo = FakePackagesRepository(items = emptyList())
        val fallback = ShipmentPackage(id = 9, trackingCode = "PKG-9")

        val resolved = resolvePackageReference(
            "pkg-9",
            repo,
            shortlist = { Result.success(listOf(fallback)) },
            numericIdIsCanonical = true,
        ).getOrThrow()

        assertEquals(9, resolved.packageId)
        assertEquals(fallback, resolved.matchedPackage)
    }

    @Test
    fun `broad search result without exact identity fails closed`() = runTest {
        val repo = FakePackagesRepository(
            items = listOf(ShipmentPackage(id = 10, trackingCode = "ADX-240524-OTHER")),
        )

        val result = resolvePackageReference(
            "ADX-240524",
            repo,
            shortlist = { Result.success(emptyList()) },
            numericIdIsCanonical = true,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("No package found"))
    }

    private class FakePackagesRepository(
        private val items: List<ShipmentPackage> = emptyList(),
    ) : ShipmentsPackagesRepository {
        val searches = mutableListOf<String>()

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<Paged<ShipmentPackage>> {
            search?.let(searches::add)
            return Result.success(Paged(items))
        }

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException("not used"))

        override suspend fun packageStatuses() = Result.success(emptyList<PackageStatusInfo>())

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException("not used"))

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException("not used"))
    }
}
