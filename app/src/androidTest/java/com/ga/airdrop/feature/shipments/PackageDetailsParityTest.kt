package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageDetailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var packagesRepo: FakePackagesRepository
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
    fun invoiceAndCartButtonsKeepSwiftRuntimeRails() {
        setPackageDetailsContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("package-details-invoice-view-101")
            .performScrollTo()
            .performClick()
        assertTrue(
            "Invoice view should navigate to the shared invoice viewer route",
            navigatedRoutes.lastOrNull().orEmpty().startsWith("invoiceViewer?url="),
        )

        compose.onNodeWithTag("package-details-invoice-delete-101")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Delete invoice").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { packagesRepo.deletedInvoiceIds == listOf(101) }

        compose.onNodeWithText("Add to Cart")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Success").assertIsDisplayed()
        assertEquals(1, CartStore.count)
    }

    private fun setPackageDetailsContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            CartStore.init(InstrumentationRegistry.getInstrumentation().targetContext)
            CartStore.clear()
        }
        navigatedRoutes.clear()
        packagesRepo = FakePackagesRepository(sampleDetail())
        val viewModel = PackageDetailsViewModel(
            packageId = "7",
            repo = packagesRepo,
            hubRepo = FakeHubRepository(),
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
                        viewModel = viewModel,
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
        val delete = compose.onNodeWithTag("package-details-invoice-delete-101")
            .getUnclippedBoundsInRoot()
        val view = compose.onNodeWithTag("package-details-invoice-view-101")
            .getUnclippedBoundsInRoot()
        assertClose(56f, boundsHeight(row), "Swift invoice row height")
        assertTrue(
            "Swift invoice actions are trash then eye",
            boundsLeft(delete) < boundsLeft(view),
        )

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

    private fun sampleDetail() = ShipmentPackageDetail(
        id = 7,
        status = "7",
        statusName = "Ready for Pickup",
        shippingMethod = "Standard",
        trackingCode = "AR000000043525",
        store = "Global HUB",
        shipper = "DHL / Airborne",
        courierNumber = "1Z83X5220392160325",
        description = "Plastic phone case",
        weightLbs = 4.5,
        numberOfPieces = 1,
        history = listOf(
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

        override suspend fun packages(page: Int, perPage: Int, status: Int?, search: String?) =
            Result.success(emptyList<ShipmentPackage>())

        override suspend fun packageDetails(packageId: String) = Result.success(detail)

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.success(Unit)

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit> {
            deletedInvoiceIds += invoiceId
            detail = detail.copy(invoices = detail.invoices.filterNot { it.id == invoiceId })
            return Result.success(Unit)
        }
    }

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
