package com.ga.airdrop.feature.shipments

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
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
    fun hubRefreshesLiveDataOnResumeLikeSwiftViewDidAppear() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repo = FakeHubRepository()
        val viewModel = ShipmentsViewModel(repo)

        compose.waitUntil(timeoutMillis = 5_000) {
            repo.exchangeRateCalls.get() == 1 &&
                repo.summaryCalls.get() == 1 &&
                repo.packagesCalls.get() == 1 &&
                repo.paymentsCalls.get() == 1 &&
                repo.ordersCalls.get() == 1 &&
                !viewModel.state.value.loading
        }

        val lifecycleOwner = TestLifecycleOwner()
        instrumentation.runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
        }

        navigatedRoutes.clear()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
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
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(repo.readyText).fetchSemanticsNodes().isNotEmpty()
        }

        instrumentation.runOnMainSync {
            lifecycleOwner.handle(Lifecycle.Event.ON_START)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repo.exchangeRateCalls.get() >= 2 &&
                repo.summaryCalls.get() >= 2 &&
                repo.packagesCalls.get() >= 2 &&
                repo.paymentsCalls.get() >= 2 &&
                repo.ordersCalls.get() >= 2
        }
    }

    @Test
    fun hubDoesNotDoubleRefreshWhenFirstComposedIntoAlreadyResumedLifecycle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        val repo = FakeHubRepository()
        val viewModel = ShipmentsViewModel(repo)

        compose.waitUntil(timeoutMillis = 5_000) {
            repo.ordersCalls.get() == 1 && !viewModel.state.value.loading
        }

        navigatedRoutes.clear()
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

        compose.runOnIdle {
            assertEquals(
                "Swift viewDidAppear should be represented by one first-entry hub load, not VM init plus an immediate resumed-state duplicate",
                1,
                repo.exchangeRateCalls.get(),
            )
            assertEquals(1, repo.summaryCalls.get())
            assertEquals(1, repo.packagesCalls.get())
            assertEquals(1, repo.paymentsCalls.get())
            assertEquals(1, repo.ordersCalls.get())
        }
    }

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

            compose.onNodeWithTag("shipments-summary-track-shipment").performClick()
            compose.onNodeWithTag("shipments-quick-track-sheet").assertIsDisplayed()
            compose.onNodeWithTag("shipments-quick-track-scan").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue("Track Shipment opens Swift quick-track sheet, not Packages", navigatedRoutes.isEmpty())
            }
            compose.onNodeWithTag("shipments-quick-track-field").performTextInput("ARD000000101")
            compose.onNodeWithTag("shipments-quick-track-submit").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                navigatedRoutes.lastOrNull() == Routes.packageDetails("101")
            }

            listOf(
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
    fun quickTrackScanButtonOpensScannerAndReturnsToSheet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.CAMERA,
            )
        }
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        setShipmentsContent(FakeHubRepository())

        compose.onNodeWithTag("shipments-summary-track-shipment").performClick()
        compose.onNodeWithTag("shipments-quick-track-sheet").assertIsDisplayed()
        compose.onNodeWithTag("shipments-quick-track-scan").performClick()
        compose.onNodeWithTag("shipments-quick-track-scanner").assertIsDisplayed()

        val closeNodes = compose.onAllNodesWithTag(
            "shipments-quick-track-scanner-close",
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        if (closeNodes.isNotEmpty()) {
            compose.onNodeWithTag("shipments-quick-track-scanner-close", useUnmergedTree = true).performClick()
        } else {
            compose.onNodeWithTag("shipments-quick-track-camera-not-now", useUnmergedTree = true).performClick()
        }

        compose.onNodeWithTag("shipments-quick-track-scanner").assertDoesNotExist()
        compose.onNodeWithTag("shipments-quick-track-sheet").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("Scanner dismissal must not navigate away from QuickTrack", navigatedRoutes.isEmpty())
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
    fun packagePreviewCardKeepsSwiftFigmaVisibleGeometry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CartStore.clear()
        }

        setShipmentsContent(FakeHubRepository())

        val cardTag = "shipments-package-card-101"
        compose.onNodeWithTag(cardTag).performScrollTo()
        compose.onNodeWithTag("$cardTag-method-strip", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("$cardTag-method-icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("$cardTag-status-row", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("$cardTag-status-value", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("shipments-package-cart-toggle-101", useUnmergedTree = true).assertExists()

        val card = bounds(cardTag)
        val methodStrip = bounds("$cardTag-method-strip")
        val methodIcon = bounds("$cardTag-method-icon")
        val statusRow = bounds("$cardTag-status-row")
        val statusValue = bounds("$cardTag-status-value")
        val cartToggle = bounds("shipments-package-cart-toggle-101")

        assertClose(280f, width(card), "Swift package card width")
        assertClose(54f, height(methodStrip), "Swift package method strip height")
        assertClose(24f, width(methodIcon), "Shipping method icon width")
        assertClose(24f, height(methodIcon), "Shipping method icon height")
        assertClose(24f, width(cartToggle), "Cart toggle width")
        assertClose(24f, height(cartToggle), "Cart toggle height")

        assertInside(card, methodStrip, "method strip")
        assertInside(card, statusRow, "status row")
        assertInside(card, statusValue, "status value")
        assertInside(card, cartToggle, "cart toggle")
        assertTrue(
            "Status row must remain below method strip",
            statusRow.top.value > methodStrip.bottom.value,
        )
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

    private fun setShipmentsContent(
        repo: FakeHubRepository,
        packagesRepo: ShipmentsPackagesRepository = FakePackagesRepository(),
    ) {
        navigatedRoutes.clear()
        val viewModel = ShipmentsViewModel(repo, packagesRepo)
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

    private fun assertInside(
        parent: androidx.compose.ui.unit.DpRect,
        child: androidx.compose.ui.unit.DpRect,
        label: String,
    ) {
        assertTrue("$label left clipped", child.left.value >= parent.left.value - 0.75f)
        assertTrue("$label top clipped", child.top.value >= parent.top.value - 0.75f)
        assertTrue("$label right clipped", child.right.value <= parent.right.value + 0.75f)
        assertTrue("$label bottom clipped", child.bottom.value <= parent.bottom.value + 0.75f)
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
        val relativePath = "Pictures/kotlin_ui_proof/shipments_hub_visual/"
        runCatching {
            context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                arrayOf(filename, relativePath),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        runCatching {
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
    }

    private class FakeHubRepository(
        private val packages: List<ShipmentPackage> = listOf(samplePackage()),
        private val payments: List<ShipmentPayment> = samplePayments(),
        private val orders: List<ShipmentOrder> = listOf(sampleOrder()),
        val readyText: String = "Ready for Pick-Up",
    ) : ShipmentsHubRepository {
        val exchangeRateCalls = AtomicInteger()
        val summaryCalls = AtomicInteger()
        val packagesCalls = AtomicInteger()
        val paymentsCalls = AtomicInteger()
        val ordersCalls = AtomicInteger()

        override suspend fun exchangeRate(): Result<Double> {
            exchangeRateCalls.incrementAndGet()
            return Result.success(161.0)
        }

        override suspend fun summary(): Result<ShipmentsSummary> {
            summaryCalls.incrementAndGet()
            return Result.success(
                ShipmentsSummary(
                    totalShipments = 7,
                    totalPackages = 34,
                    totalPayments = 234,
                    totalOrders = 3,
                )
            )
        }

        override suspend fun packagesShortlist(): Result<List<ShipmentPackage>> {
            packagesCalls.incrementAndGet()
            return Result.success(packages)
        }

        override suspend fun paymentsShortlist(): Result<List<ShipmentPayment>> {
            paymentsCalls.incrementAndGet()
            return Result.success(payments)
        }

        override suspend fun ordersShortlist(): Result<List<ShipmentOrder>> {
            ordersCalls.incrementAndGet()
            return Result.success(orders)
        }

        companion object {
            fun samplePackage() =
                ShipmentPackage(
                    id = 101,
                    description = "scrubber/earpod",
                    weightLbs = 1.3,
                    status = "7",
                    statusName = "Ready for Pick-Up",
                    shippingMethod = "Standard",
                    trackingCode = "ARD000000101",
                    courierNumber = "COUR101",
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

    private class FakePackagesRepository(
        private val packages: List<ShipmentPackage> = listOf(FakeHubRepository.samplePackage()),
    ) : ShipmentsPackagesRepository {

        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
            shippingMethod: String?,
        ): Result<List<ShipmentPackage>> {
            val query = search.orEmpty().trim().uppercase(Locale.US)
            val filtered = if (query.isEmpty()) {
                packages
            } else {
                packages.filter {
                    it.trackingCode.matches(query) ||
                        it.courierNumber.matches(query) ||
                        it.id.toString() == query
                }
            }
            return Result.success(filtered)
        }

        override suspend fun packageDetails(packageId: String): Result<ShipmentPackageDetail> =
            Result.failure(IllegalStateException("Package detail not used by quick-track test"))

        override suspend fun packageStatuses(): Result<List<PackageStatusInfo>> =
            Result.success(emptyList())

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int): Result<Unit> =
            Result.success(Unit)

        private fun String?.matches(query: String): Boolean =
            this?.trim()?.uppercase(Locale.US) == query
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
