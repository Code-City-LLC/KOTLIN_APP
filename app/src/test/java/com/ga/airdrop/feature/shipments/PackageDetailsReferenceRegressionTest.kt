package com.ga.airdrop.feature.shipments

import com.ga.airdrop.data.repo.DeliveryTrackingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PackageDetailsReferenceRegressionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `numeric courier is resolved before every package detail read and write`() = runTest(dispatcher) {
        val packages = AmbiguousPackagesRepository()
        val tracking = RecordingTrackingGateway()
        val viewModel = PackageDetailsViewModel(
            packageId = "42",
            repo = packages,
            hubRepo = EmptyHubRepository(),
            tracking = tracking,
        )
        advanceUntilIdle()

        assertEquals(listOf("42"), packages.searches)
        assertEquals(listOf("7"), packages.detailRequests)
        assertEquals(7, viewModel.state.value.detail?.id)

        viewModel.uploadInvoices(
            listOf(InvoiceUploadFile("invoice.pdf", "application/pdf", ByteArray(8))),
        )
        advanceUntilIdle()
        viewModel.requestDeleteInvoice(99)
        viewModel.confirmDeleteInvoice()
        advanceUntilIdle()
        viewModel.submitDamageReport()
        advanceUntilIdle()

        assertEquals(listOf("7"), packages.uploadPackageIds)
        assertEquals(listOf("7"), packages.deletePackageIds)
        assertEquals(listOf("7"), packages.damagePackageIds)
        assertTrue(tracking.timelinePackageIds.isNotEmpty())
        assertTrue(tracking.timelinePackageIds.all { it == 7 })
    }

    @Test
    fun `damage description preserves all text accepted by Laravel unicode limit`() = runTest(dispatcher) {
        val descriptions = listOf(
            "a".repeat(5_000),
            "a".repeat(4_999) + "😀",
            "😀".repeat(5_000),
            "e\u0301".repeat(2_500),
            "  " + "😀".repeat(5_000) + "  ",
        )
        for (description in descriptions) {
            val packages = AmbiguousPackagesRepository()
            val viewModel = PackageDetailsViewModel("42", packages, EmptyHubRepository(), tracking = RecordingTrackingGateway())
            advanceUntilIdle()
            viewModel.onDamageReportDescription(description)
            assertEquals(description, viewModel.state.value.damageReportDescription)
            viewModel.submitDamageReport()
            advanceUntilIdle()
            assertEquals(listOf(description.trim()), packages.damageDescriptions)
            assertTrue(viewModel.state.value.showDamageReportSubmitted)
        }
    }

    @Test
    fun `oversized damage draft is retained and blocked before upload then corrected retry succeeds`() = runTest(dispatcher) {
        for (description in listOf("a".repeat(5_001), "😀".repeat(5_001), "e\u0301".repeat(2_500) + "x")) {
            val packages = AmbiguousPackagesRepository()
            val viewModel = PackageDetailsViewModel("42", packages, EmptyHubRepository(), tracking = RecordingTrackingGateway())
            advanceUntilIdle()
            viewModel.showReportDamageSheet(true)
            viewModel.onDamageReportDescription(description)
            viewModel.submitDamageReport()
            advanceUntilIdle()
            assertEquals(description, viewModel.state.value.damageReportDescription)
            assertTrue(packages.damageDescriptions.isEmpty())
            assertTrue(viewModel.state.value.showReportDamageSheet)
            assertTrue(!viewModel.state.value.submittingDamageReport)
            assertEquals("Description cannot exceed 5,000 characters.", viewModel.state.value.damageReportError)
            viewModel.onDamageReportDescription("Corrected damage description")
            viewModel.submitDamageReport()
            advanceUntilIdle()
            assertEquals(listOf("Corrected damage description"), packages.damageDescriptions)
            assertTrue(viewModel.state.value.showDamageReportSubmitted)
        }
    }

    private class AmbiguousPackagesRepository : ShipmentsPackagesRepository {
        val searches = mutableListOf<String>()
        val detailRequests = mutableListOf<String>()
        val uploadPackageIds = mutableListOf<String>()
        val deletePackageIds = mutableListOf<String>()
        val damagePackageIds = mutableListOf<String>()
        val damageDescriptions = mutableListOf<String>()

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<Paged<ShipmentPackage>> {
            search?.let(searches::add)
            return Result.success(
                Paged(
                    listOf(
                        ShipmentPackage(id = 42, trackingCode = "ADX-OTHER"),
                        ShipmentPackage(id = 7, courierNumber = "42"),
                    ),
                ),
            )
        }

        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> {
            detailRequests += packageId
            return Result.success(
                ShipmentPackageDetail(
                    id = packageId.toInt(),
                    status = "6",
                    statusName = "Processing at our Warehouse",
                    trackingCode = "ADX-$packageId",
                    invoices = listOf(PackageInvoiceDoc(id = 99, fileName = "existing.pdf")),
                ),
            )
        }

        override suspend fun packageStatuses() = Result.success(emptyList<PackageStatusInfo>())

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>): Result<Unit> {
            uploadPackageIds += packageId
            return Result.success(Unit)
        }

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit> {
            deletePackageIds += packageId
            return Result.success(Unit)
        }

        override suspend fun reportDamage(
            packageId: String,
            description: String,
            photos: List<DamageReportUploadFile>,
        ): Result<Unit> {
            damagePackageIds += packageId
            damageDescriptions += description
            return Result.success(Unit)
        }
    }

    private class EmptyHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private class RecordingTrackingGateway : DeliveryTrackingGateway {
        val timelinePackageIds = mutableListOf<Int>()

        override suspend fun packageJourneys(page: Int, perPage: Int) = error("not used")
        override suspend fun activeDeliveries(page: Int, perPage: Int) = error("not used")
        override suspend fun deliveryTracking(packageId: Int) = error("not used")
        override suspend fun packageTimeline(packageId: Int) =
            Result.success(emptyList<com.ga.airdrop.data.model.PackageTimelineEntry>()).also {
                timelinePackageIds += packageId
            }
    }
}
