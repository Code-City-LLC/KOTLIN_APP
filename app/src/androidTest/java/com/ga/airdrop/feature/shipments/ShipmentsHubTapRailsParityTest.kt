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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShipmentsHubTapRailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()

    @Test
    fun hubSummaryCardsViewMoreCardsAndCartToggleKeepSwiftRails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CartStore.clear()
        }

        try {
            setShipmentsContent(FakeHubRepository())

            listOf(
                "shipments-summary-track-shipment" to Routes.PACKAGES,
                "shipments-summary-packages" to Routes.PACKAGES,
                "shipments-summary-payments" to Routes.PAYMENTS,
                "shipments-summary-orders" to Routes.ORDERS,
            ).forEach { (tag, route) ->
                compose.onNodeWithTag(tag).performClick()
                compose.runOnIdle {
                    assertEquals(route, navigatedRoutes.lastOrNull())
                }
            }

            val routeCountBeforeCart = navigatedRoutes.size
            compose.onNodeWithTag("shipments-package-cart-toggle-101")
                .performClick()
            compose.runOnIdle {
                assertEquals(routeCountBeforeCart, navigatedRoutes.size)
                assertEquals(1, CartStore.count)
                assertEquals(101, CartStore.items.value.single().id)
            }

            compose.onNodeWithTag("shipments-package-card-101").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.packageDetails("101"), navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-packages-view-more").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.PACKAGES, navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-orders-view-more").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.ORDERS, navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-payments-view-more").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.PAYMENTS, navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-payment-card-201").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.paymentPackageDetails("201"), navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-payment-card-202").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.productPaymentDetails("202"), navigatedRoutes.lastOrNull())
            }
        } finally {
            instrumentation.runOnMainSync {
                CartStore.clear()
            }
        }
    }

    @Test
    fun orderCardOpensSwiftDetailRoute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        setShipmentsContent(
            FakeHubRepository(
                packages = emptyList(),
                payments = emptyList(),
                orders = listOf(FakeHubRepository.sampleOrder()),
                readyText = "Apple 2023 MacBook Pro Laptop M3 chip",
            )
        )

        compose.onNodeWithTag("shipments-order-card-301").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Routes.orderDetails("301"), navigatedRoutes.lastOrNull())
        }
    }

    @Test
    fun hubSummaryKeepsSwiftFigmaGeometryAndIconPaletteLight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        setShipmentsContent(FakeHubRepository())

        assertSummarySwiftFigmaGeometry()
        assertSummaryIconPalette("track-shipment", dark = false)
        assertSummaryIconPalette("packages", dark = false)
        assertSummaryIconPalette("payments", dark = false)
        assertSummaryIconPalette("orders", dark = false)
        saveRootScreenshot("shipments_hub_swift_light.png")
    }

    @Test
    fun hubSummaryKeepsSwiftFigmaGeometryAndIconPaletteDark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.DARK)
        }

        setShipmentsContent(FakeHubRepository())

        assertSummarySwiftFigmaGeometry()
        assertSummaryIconPalette("track-shipment", dark = true)
        assertSummaryIconPalette("packages", dark = true)
        assertSummaryIconPalette("payments", dark = true)
        assertSummaryIconPalette("orders", dark = true)
        saveRootScreenshot("shipments_hub_swift_dark.png")
    }

    private fun setShipmentsContent(repo: FakeHubRepository) {
        navigatedRoutes.clear()
        val viewModel = ShipmentsViewModel(repo)
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    ShipmentsScreen(
                        onNavigate = navigatedRoutes::add,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(repo.readyText).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertSummarySwiftFigmaGeometry() {
        val track = bounds("shipments-summary-track-shipment")
        val packages = bounds("shipments-summary-packages")
        val payments = bounds("shipments-summary-payments")

        assertClose(162.5f, width(track), "Summary tile width")
        assertClose(93f, height(track), "Summary tile height")
        assertClose(10f, packages.left.value - track.right.value, "Summary column gap")
        assertClose(10f, payments.top.value - track.bottom.value, "Summary row gap")
    }

    private fun assertSummaryIconPalette(tag: String, dark: Boolean) {
        val bitmap = compose.onNodeWithTag(
            "shipments-summary-icon-$tag",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        val orangePixels = countPixels(bitmap) { a, r, g, b ->
            a > 80 && r > 200 && g in 55..120 && b < 60
        }
        val secondaryPixels = if (dark) {
            countPixels(bitmap) { a, r, g, b -> a > 80 && r > 210 && g > 210 && b > 210 }
        } else {
            countPixels(bitmap) { a, r, g, b -> a > 80 && r < 80 && g < 80 && b < 80 }
        }

        assertTrue("$tag should keep Swift/Figma orange accent", orangePixels > 4)
        assertTrue(
            "$tag should use ThemeController-aware ${if (dark) "white" else "dark"} secondary strokes",
            secondaryPixels > 4,
        )
    }

    private fun countPixels(bitmap: Bitmap, predicate: (Int, Int, Int, Int) -> Boolean): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24 and 0xFF
                val red = pixel ushr 16 and 0xFF
                val green = pixel ushr 8 and 0xFF
                val blue = pixel and 0xFF
                if (predicate(alpha, red, green, blue)) count += 1
            }
        }
        return count
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(
        tag,
        useUnmergedTree = true,
    ).getUnclippedBoundsInRoot()

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun width(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun height(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/shipments_hub_visual").also { it.mkdirs() }
    }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/shipments_hub_visual")
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

    private class FakeHubRepository(
        private val packages: List<ShipmentPackage> = listOf(samplePackage()),
        private val payments: List<ShipmentPayment> = samplePayments(),
        private val orders: List<ShipmentOrder> = listOf(sampleOrder()),
        val readyText: String = "Ready for Pick-Up",
    ) : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)

        override suspend fun summary() = Result.success(
            ShipmentsSummary(
                totalShipments = 7,
                totalPackages = 34,
                totalPayments = 234,
                totalOrders = 3,
            )
        )

        override suspend fun packagesShortlist() = Result.success(packages)

        override suspend fun paymentsShortlist() = Result.success(payments)

        override suspend fun ordersShortlist() = Result.success(orders)

        companion object {
            fun samplePackage() =
                ShipmentPackage(
                    id = 101,
                    description = "scrubber/earpod",
                    weightLbs = 1.3,
                    statusName = "Ready for Pick-Up",
                    shippingMethod = "Standard",
                    additionalChargesTotal = 50.0,
                )

            fun samplePayments() = listOf(
                ShipmentPayment(
                    id = 201,
                    invoiceId = "INV-201",
                    paymentType = "package",
                    totalAmount = 50.0,
                    trackingCode = "ARD000000201",
                    paymentDate = "2024-01-12T15:14:00Z",
                    packageId = 101,
                    packageDescription = "Package payment",
                ),
                ShipmentPayment(
                    id = 202,
                    invoiceId = "INV-202",
                    paymentType = "product",
                    totalAmount = 1550.0,
                    trackingCode = "ARD000000202",
                    paymentDate = "2024-01-13T15:14:00Z",
                    orderId = 301,
                    packageDescription = "Product payment",
                ),
            )

            fun sampleOrder() =
                ShipmentOrder(
                    id = 301,
                    orderNumber = "ORD-301",
                    title = "Apple 2023 MacBook Pro Laptop M3 chip",
                    status = "pending",
                    orderStatus = "order placed",
                    invoiceAmountUsd = 1550.0,
                )
        }
    }
}
