package com.ga.airdrop.feature.shipments

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackagesFilterFlowParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun packagesFilterHeaderOpensAndAppliesSwiftRailsLight() {
        runFilterFlow(
            mode = ThemeController.Mode.LIGHT,
            sheetScreenshot = "packages_filter_flow_swift_light.png",
            filteredScreenshot = "packages_filter_flow_filtered_light.png",
        )
    }

    @Test
    fun packagesFilterHeaderOpensAndAppliesSwiftRailsDark() {
        runFilterFlow(
            mode = ThemeController.Mode.DARK,
            sheetScreenshot = "packages_filter_flow_swift_dark.png",
            filteredScreenshot = "packages_filter_flow_filtered_dark.png",
        )
    }

    private fun runFilterFlow(
        mode: ThemeController.Mode,
        sheetScreenshot: String,
        filteredScreenshot: String,
    ) {
        val packagesRepo = RecordingPackagesRepository()
        setPackagesScreen(mode, packagesRepo)

        compose.onNodeWithContentDescription("Filter").assertIsDisplayed()
        compose.onNodeWithTag("packages-filter-button").performClick()
        compose.onNodeWithText("Sorting by").assertIsDisplayed()
        compose.onNodeWithText("Shipment Method").assertIsDisplayed()
        compose.onNodeWithText("Status of Shipment").assertIsDisplayed()
        saveNodeScreenshot("packages-filter-root", sheetScreenshot)

        compose.onNodeWithTag("packages-filter-status-row-7").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            packagesRepo.calls.any { it.status == 7 }
        }

        compose.onNodeWithTag("packages-filter-method-row-express").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            packagesRepo.calls.count { it.status == 7 } >= 2
        }
        compose.onNodeWithTag("packages-filter-close").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Express visible").assertIsDisplayed()
        assertNoText("Standard visible")
        assertNoText("SeaDrop visible")
        saveRootScreenshot(filteredScreenshot)

        val calls = packagesRepo.calls
        assertEquals(PackagesViewModel.PER_PAGE, calls.first().perPage)
        assertEquals(null, calls.first().status)
        assertTrue(
            "Swift status filter is server-backed, so a selected Ready status must be sent",
            calls.any { it.status == 7 },
        )
        assertEquals(
            "Swift keeps shipment method client-side; repo status remains Ready after method tap",
            7,
            calls.last().status,
        )
        assertEquals(null, calls.last().search)
    }

    private fun setPackagesScreen(
        mode: ThemeController.Mode,
        packagesRepo: RecordingPackagesRepository,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    PackagesScreen(
                        onBack = {},
                        onNavigate = {},
                        viewModel = PackagesViewModel(
                            repo = packagesRepo,
                            hubRepo = FakeHubRepository(),
                        ),
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            packagesRepo.calls.isNotEmpty() &&
                compose.onAllNodesWithText("Standard visible").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should be hidden by the Swift client-side shipment method filter",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        saveBitmap(bitmap, filename)
    }

    private fun saveNodeScreenshot(tag: String, filename: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        saveBitmap(bitmap, filename)
    }

    private fun saveBitmap(bitmap: Bitmap, filename: String) {
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/packages_filter_flow",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(filename, "$mediaStoreRelativePath/"),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, mediaStoreRelativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        outputStream.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private data class PackageCall(
        val page: Int,
        val perPage: Int,
        val status: Int?,
        val search: String?,
    )

    private class RecordingPackagesRepository : ShipmentsPackagesRepository {
        private val recordedCalls = Collections.synchronizedList(mutableListOf<PackageCall>())

        val calls: List<PackageCall>
            get() = synchronized(recordedCalls) { recordedCalls.toList() }

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
        ): Result<Paged<ShipmentPackage>> {
            recordedCalls += PackageCall(page, perPage, status, search)
            return Result.success(Paged(samplePackages))
        }

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException())

        override suspend fun packageStatuses() = Result.success(sampleStatuses)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException())

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException())
    }

    private class FakeHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)

        override suspend fun summary() = Result.success(ShipmentsSummary())

        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())

        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())

        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private companion object {
        val sampleStatuses = listOf(
            PackageStatusInfo(7, "Ready for Pickup", "#0f03fc", 10),
            PackageStatusInfo(18, "Paid and Ready for Pick Up", "#19d144", 16),
        )

        val samplePackages = listOf(
            ShipmentPackage(
                id = 701,
                description = "standard visible",
                weightLbs = 1.3,
                statusName = "Ready for Pickup",
                shippingMethod = "Standard",
                additionalChargesTotal = 50.0,
            ),
            ShipmentPackage(
                id = 702,
                description = "express visible",
                weightLbs = 2.0,
                statusName = "Ready for Pickup",
                shippingMethod = "Express",
                additionalChargesTotal = 75.0,
            ),
            ShipmentPackage(
                id = 703,
                description = "seaDrop visible",
                weightLbs = 3.0,
                statusName = "Ready for Pickup",
                shippingMethod = "SeaDrop",
                additionalChargesTotal = 100.0,
            ),
        )

        private val mediaStoreRelativePath =
            "Pictures/kotlin_ui_proof/packages_filter_flow/run_${System.currentTimeMillis()}"
    }
}
