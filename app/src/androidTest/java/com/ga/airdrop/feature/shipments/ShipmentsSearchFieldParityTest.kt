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
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShipmentsSearchFieldParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun packagesScreenKeepsSwiftLeadingSearchIcon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        val viewModel = PackagesViewModel(
            repo = FakePackagesRepository(),
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
                    PackagesScreen(
                        onBack = {},
                        onNavigate = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Ready for Pickup").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Search by Airdrop Tracking # or Courier #").assertIsDisplayed()
        assertLeadingSearchIcon("packages-search-field", "packages-search-icon")
        saveRootScreenshot("packages_search_swift_light.png")
    }

    private fun assertLeadingSearchIcon(fieldTag: String, iconTag: String) {
        val field = compose.onNodeWithTag(fieldTag).getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithTag(iconTag, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertClose(22f, boundsWidth(icon), "$fieldTag Swift leading icon width")
        assertClose(22f, boundsHeight(icon), "$fieldTag Swift leading icon height")
        assertClose(20f, boundsLeft(icon) - boundsLeft(field), "$fieldTag Swift leading icon inset")
        assertTrue(
            "$fieldTag icon should be on the leading half",
            boundsRight(icon) < boundsLeft(field) + (boundsWidth(field) / 2f),
        )
    }

    private class FakePackagesRepository : ShipmentsPackagesRepository {
        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ) = Result.success(
                Paged(
                listOf(
                    ShipmentPackage(
                        id = 401,
                        description = "Scrubber/Earpod",
                        weightLbs = 1.3,
                        statusName = "Ready for Pickup",
                        shippingMethod = "Standard",
                        additionalChargesTotal = 403.35,
                    )
                )
                )
            )

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException())

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

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

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun boundsLeft(bounds: DpRect): Float = bounds.left.value

    private fun boundsRight(bounds: DpRect): Float = bounds.right.value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
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
            "screenshots/shipments_search_field",
        )
        dir.mkdirs()
        return dir
    }
}
