package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.feature.cart.CartStore
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageDetailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var packagesRepo: FakePackagesRepository
    private lateinit var packageDetailsViewModel: PackageDetailsViewModel
    private val navigatedRoutes = mutableListOf<String>()

    @Test
    fun packageDetailsUsesSwiftWinningVisualsLight() {
        setPackageDetailsContent(ThemeController.Mode.LIGHT)

        saveRootScreenshot("package_details_swift_top_light.png")
        assertSwiftVisualParity()
        saveRootScreenshot("package_details_swift_charges_light.png")
    }

    @Test
    fun packageDetailsUsesSwiftWinningVisualsDark() {
        setPackageDetailsContent(ThemeController.Mode.DARK)

        saveRootScreenshot("package_details_swift_top_dark.png")
        assertSwiftVisualParity()
        saveRootScreenshot("package_details_swift_charges_dark.png")
    }

    @Test
    fun invoiceViewAndCartButtonsKeepSwiftRuntimeRailsAtReadyForPickup() {
        setPackageDetailsContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("package-details-invoice-view-101")
            .performScrollTo()
            .performClick()
        assertTrue(
            "Invoice view should navigate to the shared invoice viewer route",
            navigatedRoutes.lastOrNull().orEmpty().startsWith("invoiceViewer?url="),
        )
        assertEquals(
            "Swift locks invoice delete at Ready for Pickup while leaving upload/view available",
            0,
            compose.onAllNodesWithTag("package-details-invoice-delete-101").fetchSemanticsNodes().size,
        )

        compose.onNodeWithText("Add to Cart")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Success").assertIsDisplayed()
        assertEquals(1, CartStore.count)
    }

    @Test
    fun loadingFailureRetryAndAuthoritativeActionGateAreVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(instrumentation.targetContext)
            CartStore.clear()
        }
        navigatedRoutes.clear()
        packagesRepo = FakePackagesRepository(sampleDetail()).apply {
            packageDetailsDelayMs = 1_000
            detailResults.addLast(Result.failure(IllegalStateException("Package service unavailable")))
            detailResults.addLast(Result.success(sampleDetail()))
        }
        packageDetailsViewModel = PackageDetailsViewModel(
            packageId = "7",
            repo = packagesRepo,
            hubRepo = FakeHubRepository(),
            cartServer = ShipmentsTestCartServer,
            sessionBoundary = ShipmentsTestSessionBoundary(),
        )

        compose.setContent {
            AirdropThemeProvider {
                PackageDetailsScreen(
                    packageId = "7",
                    onBack = {},
                    onNavigate = navigatedRoutes::add,
                    viewModel = packageDetailsViewModel,
                )
            }
        }

        compose.onNodeWithTag("package-details-loading").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("package-details-retry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Package service unavailable").assertIsDisplayed()
        compose.onNodeWithText("Summary").assertDoesNotExist()
        compose.onNodeWithTag("package-details-upload-invoice-zone").assertDoesNotExist()
        compose.onNodeWithTag("package-details-cif-row").assertDoesNotExist()
        compose.onNodeWithText("Add to Cart").assertDoesNotExist()

        compose.onNodeWithTag("package-details-retry").performClick()
        compose.onNodeWithTag("package-details-loading").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Summary").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("package-details-upload-invoice-zone")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("package-details-cif-row")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Add to Cart")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(listOf("7", "7"), packagesRepo.detailRequests)
    }

    @Test
    fun invoiceUploadUsesSwiftSourceSheetAndCorrectFormatCopy() {
        setPackageDetailsContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithText("PDF and image files (JPG, PNG, GIF, BMP, WEBP) are allowed")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            "Swift origin/main removed the stale doc/docx/html promise from the upload helper copy",
            0,
            compose.onAllNodesWithText(
                "You're allowed to upload a maximum of 3 files each with a size below 10 MB. " +
                    "Only the following formats are allowed: pdf, jpg, bmp, png, doc, docx html.",
            ).fetchSemanticsNodes().size,
        )

        compose.onNodeWithTag("package-details-upload-invoice-zone")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("upload-source-sheet").assertIsDisplayed()
        compose.onNodeWithTag("upload-source-file").assertIsDisplayed()
        compose.onNodeWithTag("upload-source-photo").assertIsDisplayed()
        compose.onNodeWithTag("upload-source-camera").assertIsDisplayed()
        compose.onNodeWithTag("upload-source-cancel").assertIsDisplayed()
    }

    @Test
    fun invoiceDeleteRemainsAvailableBeforeReadyForPickup() {
        setPackageDetailsContent(
            mode = ThemeController.Mode.LIGHT,
            detail = sampleDetail(status = "6", statusName = "Processing at our Warehouse"),
        )

        val row = compose.onNodeWithTag("package-details-invoice-row-101")
            .performScrollTo()
            .getUnclippedBoundsInRoot()
        val delete = compose.onNodeWithTag("package-details-invoice-delete-101")
            .getUnclippedBoundsInRoot()
        val view = compose.onNodeWithTag("package-details-invoice-view-101")
            .getUnclippedBoundsInRoot()
        assertClose(56f, boundsHeight(row), "Swift invoice row height before ready")
        assertClose(28f, boundsWidth(delete), "Swift invoice delete control width")
        assertClose(28f, boundsHeight(delete), "Swift invoice delete control height")
        assertClose(28f, boundsWidth(view), "Swift invoice view control width")
        assertClose(28f, boundsHeight(view), "Swift invoice view control height")
        assertTrue("Swift orders invoice actions trash before view", delete.right <= view.left)
        assertTrue("Swift keeps invoice actions inside row trailing edge", view.right < row.right)

        compose.onNodeWithTag("package-details-invoice-delete-101")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Delete invoice").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { packagesRepo.deletedInvoiceIds == listOf(101) }
    }

    @Test
    fun invoiceUploadSilentlyRefreshesAndIgnoresReentry() {
        setPackageDetailsContent(
            mode = ThemeController.Mode.LIGHT,
            detail = sampleDetail(status = "6", statusName = "Processing at our Warehouse"),
        )
        packagesRepo.uploadDelayMs = 350

        val upload = InvoiceUploadFile(
            fileName = "new-invoice.pdf",
            mimeType = "application/pdf",
            bytes = byteArrayOf(1, 2, 3),
        )
        compose.runOnUiThread {
            packageDetailsViewModel.uploadInvoices(listOf(upload))
            packageDetailsViewModel.uploadInvoices(listOf(upload))
        }

        SystemClock.sleep(500)
        assertEquals("Upload re-entry should be ignored while the first upload is in flight", 1, packagesRepo.uploadCalls)
        assertFalse(packageDetailsViewModel.state.value.uploading)
        assertFalse(
            "Invoice mutation refresh should keep existing package content visible instead of full-page loading",
            packageDetailsViewModel.state.value.loading,
        )
        assertTrue(packageDetailsViewModel.state.value.detail?.invoices?.any { it.id == 202 } == true)
    }

    @Test
    fun invoiceDeleteShowsRowSpinnerAndRefreshesSilently() {
        setPackageDetailsContent(
            mode = ThemeController.Mode.LIGHT,
            detail = sampleDetail(status = "6", statusName = "Processing at our Warehouse"),
        )
        packagesRepo.deleteDelayMs = 650

        compose.onNodeWithTag("package-details-invoice-delete-101")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Delete").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            packageDetailsViewModel.state.value.deletingInvoiceId == 101
        }
        compose.onNodeWithTag("package-details-invoice-deleting-101")
            .performScrollTo()
            .assertIsDisplayed()
        val deleting = compose.onNodeWithTag("package-details-invoice-deleting-101")
            .getUnclippedBoundsInRoot()
        assertClose(28f, boundsWidth(deleting), "Delete progress control width")
        assertClose(28f, boundsHeight(deleting), "Delete progress control height")
        assertFalse(
            "Delete mutation should not replace visible package details with a full-page loading state",
            packageDetailsViewModel.state.value.loading,
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            packagesRepo.deletedInvoiceIds == listOf(101) &&
                packageDetailsViewModel.state.value.deletingInvoiceId == null
        }
        assertFalse(packageDetailsViewModel.state.value.loading)
    }

    @Test
    fun invoiceDeleteGuardUsesDeleteTitle() {
        setPackageDetailsContent(ThemeController.Mode.LIGHT)

        compose.runOnUiThread {
            packageDetailsViewModel.requestDeleteInvoice(101)
        }

        compose.onNodeWithText("Delete invoice").assertIsDisplayed()
        compose.onNodeWithText("Invoices can still be uploaded, but they cannot be deleted once a package is ready for pickup.")
            .assertIsDisplayed()
    }

    @Test
    fun timelineUsesSwiftVisibleProgressionWhenHistoryIsSparseAtReadyForPickup() {
        setPackageDetailsContent(
            mode = ThemeController.Mode.LIGHT,
            detail = sampleDetail(
                status = "7",
                statusName = "Ready for Pickup",
                history = listOf(
                    PackageHistoryItem(
                        status = 7,
                        statusName = "Ready for Pickup",
                        comment = "Ready at counter",
                        changedDate = "2024-01-14T12:30:00Z",
                    )
                ),
            ),
        )

        listOf(
            "Drop Alerted",
            "Shipment Received",
            "Port of Departure MIA",
            "Arrived at Port JAM",
            "Ready for Pickup",
        ).forEach { text ->
            compose.onNodeWithText(text)
                .performScrollTo()
                .assertIsDisplayed()
        }
        compose.onNodeWithText("Ready at counter")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            "Swift hides backend-only status 6 from the compact package timeline",
            0,
            compose.onAllNodesWithText("Processing at our Warehouse").fetchSemanticsNodes().size,
        )
        assertEquals(
            "Swift stops the compact package timeline at Ready for Pickup for status 7",
            0,
            compose.onAllNodesWithText("Paid and Ready for Pick Up").fetchSemanticsNodes().size,
        )

        listOf(1, 2, 3, 4, 7).forEach { statusId ->
            val icon = compose.onNodeWithTag("package-details-timeline-icon-$statusId")
                .performScrollTo()
                .getUnclippedBoundsInRoot()
            assertClose(24f, boundsWidth(icon), "Swift timeline icon $statusId width")
            assertClose(24f, boundsHeight(icon), "Swift timeline icon $statusId height")
        }
        assertEquals(1, compose.onAllNodesWithTag("package-details-timeline-connector-1").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("package-details-timeline-connector-4").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("package-details-timeline-connector-7").fetchSemanticsNodes().size)
        assertNodeContainsColor(
            tag = "package-details-timeline-icon-7",
            target = 0xFF39A634.toInt(),
            label = "Ready for Pickup timeline icon should be Swift completed green",
        )
    }

    @Test
    fun reportDamageCtaAppearsOnlyForDeliveredAndSubmitsSwiftPayload() {
        setPackageDetailsContent(
            mode = ThemeController.Mode.LIGHT,
            detail = sampleDetail(
                status = "8",
                statusName = "Delivered",
                history = listOf(
                    PackageHistoryItem(
                        status = 8,
                        statusName = "Delivered",
                        comment = "Delivered to customer",
                        changedDate = "2024-01-20T16:30:00Z",
                    )
                ),
            ),
        )

        assertEquals(0, compose.onAllNodesWithText("Add to Cart").fetchSemanticsNodes().size)
        compose.onNodeWithTag("package-details-report-damage")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("package-details-report-damage-sheet").assertIsDisplayed()
        compose.onNodeWithText("Add photos of the damage. We'll review and reach out to you within 24 hours.")
            .assertIsDisplayed()
        compose.onNodeWithTag("package-details-report-damage-description")
            .performTextInput("  Cracked corner  ")
        compose.onNodeWithTag("package-details-report-damage-submit")
            .performClick()

        compose.waitUntil(timeoutMillis = 5_000) { packagesRepo.damageReports.size == 1 }
        assertEquals(
            DamageReportCall(packageId = "7", description = "Cracked corner", photoCount = 0),
            packagesRepo.damageReports.single(),
        )
        compose.onNodeWithText("Report received").assertIsDisplayed()
        compose.onNodeWithText("Thanks — we'll review the damage photos and reach out within 24 hours.")
            .assertIsDisplayed()
    }

    @Test
    fun reportDamageCtaHiddenBeforeDelivered() {
        setPackageDetailsContent(
            mode = ThemeController.Mode.LIGHT,
            detail = sampleDetail(status = "7", statusName = "Ready for Pickup"),
        )

        assertEquals(
            "Swift only shows Report damage for Delivered status 8 packages",
            0,
            compose.onAllNodesWithTag("package-details-report-damage").fetchSemanticsNodes().size,
        )
    }

    private fun setPackageDetailsContent(
        mode: ThemeController.Mode,
        detail: ShipmentPackageDetail = sampleDetail(),
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            CartStore.init(InstrumentationRegistry.getInstrumentation().targetContext)
            CartStore.clear()
        }
        navigatedRoutes.clear()
        packagesRepo = FakePackagesRepository(detail)
        packageDetailsViewModel = PackageDetailsViewModel(
            packageId = "7",
            repo = packagesRepo,
            hubRepo = FakeHubRepository(),
            cartServer = ShipmentsTestCartServer,
            sessionBoundary = ShipmentsTestSessionBoundary(),
        )
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    PackageDetailsScreen(
                        packageId = "7",
                        onBack = {},
                        onNavigate = navigatedRoutes::add,
                        viewModel = packageDetailsViewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Summary").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSwiftVisualParity() {
        compose.onNodeWithTag("package-details-sheet").assertIsDisplayed()
        compose.onNodeWithText("AirDrop").assertIsDisplayed()
        assertNodeContainsColor("package-details-hero-icon", 0xFF10BBE9.toInt(), "Standard hero glyph is AirDrop blue")
        assertEquals(0, compose.onAllNodesWithText("AirDrop Standard").fetchSemanticsNodes().size)

        compose.onNodeWithTag("package-details-section-summary")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Merchant/Shipper")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Global HUB")
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithTag("package-details-section-timeline")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Drop Alerted")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Received at warehouse")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            "Swift omits missing timeline dates instead of showing Figma's static N/A",
            0,
            compose.onAllNodesWithText("N/A").fetchSemanticsNodes().size,
        )

        val row = compose.onNodeWithTag("package-details-invoice-row-101")
            .performScrollTo()
            .getUnclippedBoundsInRoot()
        val view = compose.onNodeWithTag("package-details-invoice-view-101")
            .getUnclippedBoundsInRoot()
        assertClose(56f, boundsHeight(row), "Swift invoice row height")
        assertEquals(
            "Swift hides invoice delete at status 7 Ready for Pickup",
            0,
            compose.onAllNodesWithTag("package-details-invoice-delete-101").fetchSemanticsNodes().size,
        )
        assertTrue("Swift keeps invoice view action visible", boundsLeft(view) > boundsLeft(row))

        compose.onNodeWithTag("package-details-cif-row")
            .performScrollTo()
            .assertIsDisplayed()
        assertClose(
            48f,
            boundsHeight(compose.onNodeWithTag("package-details-cif-row").getUnclippedBoundsInRoot()),
            "Swift CIF row height",
        )

        compose.onNodeWithTag("package-details-section-charges")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Exchange Rate")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("1 USD = 161.00 JMD").fetchSemanticsNodes().size)
        compose.onNodeWithTag("package-details-total-value")
            .performScrollTo()
            .assertIsDisplayed()
        assertTextExists("USD 0.00 / JMD 0.00")
        assertEquals(0, compose.onAllNodesWithText("-").fetchSemanticsNodes().size)
    }

    private fun sampleDetail(
        status: String = "7",
        statusName: String = "Ready for Pickup",
        history: List<PackageHistoryItem> = listOf(
            PackageHistoryItem(
                status = 1,
                statusName = "Drop Alerted",
                comment = "Received at warehouse",
                changedDate = "2024-01-12T15:14:00Z",
            ),
            PackageHistoryItem(
                status = 2,
                statusName = "Shipment Received",
                changedDate = null,
            ),
        ),
    ) = ShipmentPackageDetail(
        id = 7,
        status = status,
        statusName = statusName,
        shippingMethod = "Standard",
        trackingCode = "AR000000043525",
        store = "Global HUB",
        shipper = "DHL / Airborne",
        courierNumber = "1Z83X5220392160325",
        description = "Plastic phone case",
        weightLbs = 4.5,
        numberOfPieces = 1,
        history = history,
        invoices = listOf(
            PackageInvoiceDoc(
                id = 101,
                fileName = "invoice.pdf",
                fullUrl = "https://example.test/invoice.pdf",
            ),
        ),
        additionalCharges = emptyMap(),
        additionalChargesTotal = null,
        exchangeRate = 161.0,
    )

    private class FakePackagesRepository(
        private var detail: ShipmentPackageDetail,
    ) : ShipmentsPackagesRepository {
        val deletedInvoiceIds = mutableListOf<Int>()
        val damageReports = mutableListOf<DamageReportCall>()
        val detailRequests = mutableListOf<String>()
        val detailResults = ArrayDeque<Result<ShipmentPackageDetail>>()
        var uploadCalls = 0
        var uploadDelayMs = 0L
        var deleteDelayMs = 0L
        var packageDetailsDelayMs = 0L

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ) = Result.success(Paged(emptyList<ShipmentPackage>()))

        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> {
            detailRequests += packageId
            if (packageDetailsDelayMs > 0) delay(packageDetailsDelayMs)
            return detailResults.removeFirstOrNull() ?: Result.success(detail)
        }

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>): Result<Unit> {
            uploadCalls += 1
            if (uploadDelayMs > 0) delay(uploadDelayMs)
            detail = detail.copy(
                invoices = detail.invoices + PackageInvoiceDoc(
                    id = 202,
                    fileName = files.firstOrNull()?.fileName ?: "new-invoice.pdf",
                    fullUrl = "https://example.test/new-invoice.pdf",
                ),
            )
            return Result.success(Unit)
        }

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit> {
            if (deleteDelayMs > 0) delay(deleteDelayMs)
            deletedInvoiceIds += invoiceId
            detail = detail.copy(invoices = detail.invoices.filterNot { it.id == invoiceId })
            return Result.success(Unit)
        }

        override suspend fun reportDamage(
            packageId: String,
            description: String,
            photos: List<DamageReportUploadFile>,
        ): Result<Unit> {
            damageReports += DamageReportCall(packageId, description, photos.size)
            return Result.success(Unit)
        }
    }

    private data class DamageReportCall(
        val packageId: String,
        val description: String,
        val photoCount: Int,
    )

    private class FakeHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)

        override suspend fun summary() = Result.success(ShipmentsSummary())

        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())

        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())

        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/package_details",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsLeft(bounds: DpRect): Float = bounds.left.value

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected at least one visible node with text $text",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private fun assertNodeContainsColor(tag: String, target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun Bitmap.hasPixelNear(target: Int, tolerance: Int = 10): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                val alpha = pixel ushr 24
                if (alpha < 200) continue
                val dr = kotlin.math.abs(((pixel shr 16) and 0xFF) - ((target shr 16) and 0xFF))
                val dg = kotlin.math.abs(((pixel shr 8) and 0xFF) - ((target shr 8) and 0xFF))
                val db = kotlin.math.abs((pixel and 0xFF) - (target and 0xFF))
                if (dr <= tolerance && dg <= tolerance && db <= tolerance) return true
            }
        }
        return false
    }
}
