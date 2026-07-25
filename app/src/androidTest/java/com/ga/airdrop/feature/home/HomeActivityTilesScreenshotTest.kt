package com.ga.airdrop.feature.home

import androidx.activity.ComponentActivity
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.homedetails.homeDetailsGraph
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeActivityTilesScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureHomeActivityTilesLight() {
        captureHomeScreens(
            mode = ThemeController.Mode.LIGHT,
            topFilename = "home_top_light.png",
            activityTilesFilename = "home_activity_tiles_light.png",
        )
    }

    @Test
    fun captureHomeActivityTilesDark() {
        captureHomeScreens(
            mode = ThemeController.Mode.DARK,
            topFilename = "home_top_dark.png",
            activityTilesFilename = "home_activity_tiles_dark.png",
        )
    }

    @Test
    fun captureEveryWarehouseCardShadowLight() {
        captureWarehouseCardShadows(
            mode = ThemeController.Mode.LIGHT,
            themeName = "light",
        )
    }

    @Test
    fun captureEveryWarehouseCardShadowDark() {
        captureWarehouseCardShadows(
            mode = ThemeController.Mode.DARK,
            themeName = "dark",
        )
    }

    @Test
    fun homeLinksUseSwiftFigmaOrangeLight() {
        assertHomeLinkAccent(
            mode = ThemeController.Mode.LIGHT,
            targetColor = FIGMA_ORANGE_LIGHT,
            screenshot = "home_orange_links_light.png",
        )
    }

    @Test
    fun homeLinksUseFigmaDarkFunctionOrange() {
        assertHomeLinkAccent(
            mode = ThemeController.Mode.DARK,
            targetColor = FIGMA_DARK_FUNCTION_ORANGE,
            forbiddenColor = FIGMA_ORANGE_LIGHT,
            screenshot = "home_orange_links_dark.png",
        )
    }

    @Test
    fun homeActivityIconsKeepSwiftFigmaOrangeLight() {
        assertHomeActivityIconAccent(
            mode = ThemeController.Mode.LIGHT,
            targetColor = FIGMA_ORANGE_LIGHT,
            alternateColor = FIGMA_DARK_FUNCTION_ORANGE,
            screenshot = "home_activity_icons_light.png",
        )
    }

    @Test
    fun homeActivityIconsUseFigmaDarkFunctionOrange() {
        assertHomeActivityIconAccent(
            mode = ThemeController.Mode.DARK,
            targetColor = FIGMA_DARK_FUNCTION_ORANGE,
            alternateColor = FIGMA_ORANGE_LIGHT,
            screenshot = "home_activity_icons_dark.png",
        )
    }

    @Test
    fun warehouseCardsEmitSwiftRoutes() {
        val navigatedRoutes = mutableListOf<String>()
        setHomeContent { route -> navigatedRoutes += route }

        listOf(
            "standard" to "${Routes.WAREHOUSES}?type=standard",
            "seadrop" to "${Routes.WAREHOUSES}?type=seadrop",
            "express" to "${Routes.WAREHOUSES}?type=express",
        ).forEach { (type, route) ->
            compose.onNodeWithTag("home-warehouse-carousel")
                .performScrollToNode(hasTestTag("home-warehouse-$type"))
            compose.onNodeWithTag("home-warehouse-$type").performClick()
            compose.runOnIdle {
                assertEquals(route, navigatedRoutes.lastOrNull())
            }
        }
    }

    @Test
    fun warehouseCardsUseSwiftFigmaGeometry() {
        setHomeContent()

        listOf(
            WarehouseGeometryCase(type = "standard", imageDescription = "Standard"),
            WarehouseGeometryCase(type = "seadrop", imageDescription = "SeaDrop"),
            WarehouseGeometryCase(type = "express", imageDescription = "Express"),
        ).forEach { warehouseCase ->
            compose.onNodeWithTag("home-warehouse-carousel")
                .performScrollToNode(hasTestTag("home-warehouse-${warehouseCase.type}"))
            compose.waitForIdle()

            val card = compose.onNodeWithTag("home-warehouse-${warehouseCase.type}")
                .getUnclippedBoundsInRoot()
            val image = compose.onNodeWithContentDescription(
                warehouseCase.imageDescription,
                useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()

            assertClose(238f, boundsWidth(card), "Swift warehouse card width for ${warehouseCase.type}")
            assertClose(326f, boundsHeight(card), "Swift warehouse card height for ${warehouseCase.type}")
            assertClose(80f, boundsWidth(image), "Swift warehouse image width for ${warehouseCase.type}")
            assertClose(80f, boundsHeight(image), "Swift warehouse image height for ${warehouseCase.type}")
            assertClose(
                30f,
                boundsTop(image) - boundsTop(card),
                "Swift warehouse image top inset for ${warehouseCase.type}",
            )
        }
    }

    @Test
    fun homeActionCardsEmitSwiftRoutes() {
        val navigatedRoutes = mutableListOf<String>()
        setHomeContent { route -> navigatedRoutes += route }

        listOf(
            "home-activity-services" to Routes.SERVICES,
            "home-activity-ship-tax" to Routes.SALES_TAXES,
            "home-activity-calculator" to Routes.CALCULATOR,
            "home-activity-drop-alert" to Routes.DROP_ALERT,
        ).forEach { (tag, route) ->
            compose.onNodeWithTag(tag).performScrollTo()
            compose.waitForIdle()
            compose.onNodeWithTag(tag).performClick()
            compose.runOnIdle {
                assertEquals(route, navigatedRoutes.lastOrNull())
            }
        }

        compose.onNodeWithText("See More").performScrollTo()
        compose.onNodeWithText("See More").performClick()
        compose.runOnIdle {
            assertEquals(Routes.AUCTION, navigatedRoutes.lastOrNull())
        }

        compose.onNodeWithText("Refer a friend").performScrollTo()
        compose.onNodeWithText("Refer a friend").performClick()
        compose.runOnIdle {
            assertEquals(Routes.REFER_A_FRIEND, navigatedRoutes.lastOrNull())
        }
    }

    @Test
    fun homeHeaderButtonsEmitSwiftRoutes() {
        val navigatedRoutes = mutableListOf<String>()
        setHomeContent { route -> navigatedRoutes += route }

        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.runOnIdle {
            assertEquals(Routes.NOTIFICATIONS, navigatedRoutes.lastOrNull())
        }

        compose.onNodeWithContentDescription("Cart").performClick()
        compose.runOnIdle {
            assertEquals(Routes.CART, navigatedRoutes.lastOrNull())
        }
    }

    @Test
    fun activityTilesUseSwiftFigmaGeometry() {
        setHomeContent()

        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val expectedTileWidth = (boundsWidth(rootBounds) - 40f - 10f) / 2f
        listOf(
            "home-activity-services",
            "home-activity-ship-tax",
            "home-activity-calculator",
            "home-activity-drop-alert",
        ).forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertClose(expectedTileWidth, boundsWidth(bounds), "activity tile width for $tag")
            assertClose(108f, boundsHeight(bounds), "activity tile height for $tag")
        }
    }

    @Test
    fun auctionHighlightCardUsesSwiftFigmaGeometry() {
        compose.setContent {
            AirdropTheme {
                ProductHighlightCard(
                    product = AuctionProduct(
                        id = 1,
                        name = "Apple 2023 MacBook Pro Laptop M3 chip",
                        slug = "apple-2023-macbook-pro-laptop-m3-chip",
                        currentPrice = "1550.00",
                    ),
                    onClick = {},
                )
            }
        }

        val bounds = compose.onNodeWithTag("home-auction-card").getUnclippedBoundsInRoot()
        assertClose(160f, boundsWidth(bounds), "auction highlight card width")
        assertClose(245f, boundsHeight(bounds), "auction highlight card height")
    }

    @Test
    fun auctionHighlightCardAndCartToggleKeepSwiftFlowsSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val product = AuctionProduct(
            id = 42,
            packageId = 42,
            name = "Apple 2023 MacBook Pro Laptop M3 chip",
            slug = "apple-2023-macbook-pro-laptop-m3-chip",
            currentPrice = "1550.00",
        )
        val navigatedRoutes = mutableListOf<String>()

        instrumentation.runOnMainSync {
            CartStore.init(context)
            CartStore.clear()
        }

        try {
            compose.setContent {
                AirdropTheme {
                    ProductHighlightCard(
                        product = product,
                        onClick = {
                            navigatedRoutes += Routes.auctionProductDetails(product.slug.orEmpty())
                        },
                    )
                }
            }

            compose.onNodeWithContentDescription("Add to cart").assertExists()
            compose.onNodeWithTag("home-auction-cart-glyph", useUnmergedTree = true)
                .getUnclippedBoundsInRoot().let { glyph ->
                assertClose(22f, boundsWidth(glyph), "Swift add glyph width")
                assertClose(22f, boundsHeight(glyph), "Swift add glyph height")
            }
            compose.onNodeWithTag("home-auction-cart-toggle").performClick()
            compose.runOnIdle {
                assertEquals(emptyList<String>(), navigatedRoutes)
                assertEquals(1, CartStore.count)
                assertEquals(product.id, CartStore.items.value.single().id)
            }
            compose.onNodeWithContentDescription("Remove from cart").assertExists()
            compose.onNodeWithTag("home-auction-cart-glyph", useUnmergedTree = true)
                .getUnclippedBoundsInRoot().let { glyph ->
                assertClose(22f, boundsWidth(glyph), "Swift selected check-box width")
                assertClose(22f, boundsHeight(glyph), "Swift selected check-box height")
            }

            compose.onNodeWithTag("home-auction-card").performClick()
            compose.runOnIdle {
                assertEquals(
                    listOf(Routes.auctionProductDetails(product.slug.orEmpty())),
                    navigatedRoutes,
                )
            }

            compose.onNodeWithTag("home-auction-cart-toggle").performClick()
            compose.runOnIdle {
                assertEquals(0, CartStore.count)
                assertEquals(1, navigatedRoutes.size)
            }
        } finally {
            instrumentation.runOnMainSync {
                CartStore.clear()
            }
        }
    }

    @Test
    fun referFriendCompanionTileUsesThemeAwareDuotoneIconLight() {
        assertReferFriendIconPalette(
            mode = ThemeController.Mode.LIGHT,
            targetColor = SWIFT_TEXT_DARK_TITLE,
            screenshot = "home_refer_friend_swift_light.png",
        )
    }

    @Test
    fun referFriendCompanionTileUsesThemeAwareDuotoneIconDark() {
        assertReferFriendIconPalette(
            mode = ThemeController.Mode.DARK,
            targetColor = SWIFT_TEXT_DARK_TITLE_DARK,
            screenshot = "home_refer_friend_swift_dark.png",
        )
    }

    @Test
    fun standardWarehouseCardOpensWarehouseScreenFromHomeNavGraph() {
        assertWarehouseCardOpensFromHomeNavGraph(
            WarehouseNavGraphCase(
                type = "standard",
                expectedTitle = "AirDrop (Air Freight)",
                screenshot = "home_warehouse_standard_after_tap.png",
            )
        )
    }

    @Test
    fun seadropWarehouseCardOpensWarehouseScreenFromHomeNavGraph() {
        assertWarehouseCardOpensFromHomeNavGraph(
            WarehouseNavGraphCase(
                type = "seadrop",
                expectedTitle = "SeaDrop (Sea Freight)",
                screenshot = "home_warehouse_seadrop_after_tap.png",
            )
        )
    }

    @Test
    fun expressWarehouseCardOpensWarehouseScreenFromHomeNavGraph() {
        assertWarehouseCardOpensFromHomeNavGraph(
            WarehouseNavGraphCase(
                type = "express",
                expectedTitle = "Express (Air Express)",
                screenshot = "home_warehouse_express_after_tap.png",
            )
        )
    }

    private fun assertWarehouseCardOpensFromHomeNavGraph(warehouseCase: WarehouseNavGraphCase) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        compose.setContent {
            AirdropTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(onNavigate = { navController.navigate(it) })
                    }
                    homeDetailsGraph(navController)
                }
            }
        }
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithTag("home-warehouse-carousel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-warehouse-carousel")
            .performScrollToNode(hasTestTag("home-warehouse-${warehouseCase.type}"))
        compose.onNodeWithTag("home-warehouse-${warehouseCase.type}").performClick()
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithText(warehouseCase.expectedTitle).fetchSemanticsNodes().isNotEmpty()
        }
        val bitmap = captureBitmapWithRetry("${warehouseCase.type} warehouse destination") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
        val output = File(screenshotDir(), warehouseCase.screenshot)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun captureHomeScreens(
        mode: ThemeController.Mode,
        topFilename: String,
        activityTilesFilename: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }

        setHomeContent()
        saveRootScreenshot(topFilename)
        compose.onNodeWithText("Services").performScrollTo()
        compose.waitForIdle()
        saveRootScreenshot(activityTilesFilename)
    }

    private fun captureWarehouseCardShadows(
        mode: ThemeController.Mode,
        themeName: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }
        setHomeContent()

        listOf("standard", "seadrop", "express").forEach { type ->
            compose.onNodeWithTag("home-warehouse-carousel")
                .performScrollToNode(hasTestTag("home-warehouse-$type"))
            compose.waitForIdle()

            val shadow = compose.onNodeWithTag(
                "home-warehouse-shadow-$type",
                useUnmergedTree = true,
            )
            assertTrue("$type $themeName shadow must be visible", nodeHasVisibleBounds(shadow))

            val bitmap = captureBitmapWithRetry("$type $themeName warehouse card") {
                compose.onNodeWithTag("home-warehouse-$type")
                    .captureToImage()
                    .asAndroidBitmap()
            }
            val filename = "home_warehouse_${type}_shadow_$themeName.png"
            FileOutputStream(File(screenshotDir(), filename)).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Unable to encode $filename"
                }
            }
            saveProofScreenshot(bitmap, filename)
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = captureBitmapWithRetry(filename) {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveProofScreenshot(bitmap, filename)
    }

    @Suppress("InlinedApi")
    private fun saveProofScreenshot(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val relativePath = "$PROOF_SCREENSHOT_DIR/${context.packageName}/"
        resolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf(filename, relativePath),
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create proof screenshot $filename" }
        var published = false
        try {
            val output = requireNotNull(resolver.openOutputStream(uri)) {
                "Unable to open proof screenshot $filename"
            }
            output.use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    "Unable to encode proof screenshot $filename"
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) {
                "Unable to publish proof screenshot $filename"
            }
            published = true
        } finally {
            if (!published) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun setHomeContent(onNavigate: (String) -> Unit = {}) {
        compose.setContent {
            AirdropTheme {
                HomeScreen(onNavigate = onNavigate)
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Services").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertReferFriendIconPalette(
        mode: ThemeController.Mode,
        targetColor: Int,
        screenshot: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }

        setHomeContent()
        compose.onNodeWithText("Refer a friend").performScrollTo()
        compose.waitForIdle()

        val bitmap = captureVisibleNode(
            compose.onNodeWithTag("home-activity-refer-a-friend-icon", useUnmergedTree = true),
            "Home refer icon",
        )
        assertTrue("Theme-aware title stroke", bitmap.hasPixelNear(targetColor))
        assertTrue(
            "Refer companion tile must keep its AirDrop orange accent",
            bitmap.hasPixelNear(AIRDROP_ORANGE),
        )
        saveRootScreenshot(screenshot)
    }

    private fun assertHomeLinkAccent(
        mode: ThemeController.Mode,
        targetColor: Int,
        screenshot: String,
        forbiddenColor: Int? = null,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }

        setHomeContent()
        assertTextAccent("Read More", targetColor, forbiddenColor, mode)

        compose.onNodeWithText("See More").performScrollTo()
        compose.waitForIdle()
        assertTextAccent("See More", targetColor, forbiddenColor, mode)
        saveRootScreenshot(screenshot)
    }

    private fun assertHomeActivityIconAccent(
        mode: ThemeController.Mode,
        targetColor: Int,
        alternateColor: Int,
        screenshot: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }

        setHomeContent()
        compose.onNodeWithTag("home-activity-services").performScrollTo()
        compose.waitForIdle()

        listOf(
            "home-activity-services-icon",
            "home-activity-ship-tax-icon",
            "home-activity-calculator-icon",
            "home-activity-drop-alert-icon",
        ).forEach { tag ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
            node.performScrollTo()
            compose.waitForIdle()
            val bitmap = captureVisibleNode(node, "${mode.name} $tag")
            assertTrue("${mode.name} $tag accent", bitmap.hasPixelNear(targetColor))
            assertIconColorDominates(
                bitmap = bitmap,
                expected = targetColor,
                alternate = alternateColor,
                label = "${mode.name} $tag expected accent should dominate alternate",
            )
        }
        saveRootScreenshot(screenshot)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private fun boundsTop(bounds: androidx.compose.ui.unit.DpRect): Float = bounds.top.value

    private fun assertIconColorDominates(
        bitmap: Bitmap,
        expected: Int,
        alternate: Int,
        label: String,
    ) {
        val expectedCount = bitmap.pixelCountNear(expected)
        val alternateCount = bitmap.pixelCountNear(alternate)
        assertTrue(
            "$label: expectedCount=$expectedCount alternateCount=$alternateCount",
            expectedCount > alternateCount,
        )
    }

    private fun assertTextAccent(
        text: String,
        target: Int,
        forbidden: Int?,
        mode: ThemeController.Mode,
    ) {
        val bitmap = captureVisibleNode(
            compose.onAllNodesWithText(text, useUnmergedTree = true)[0],
            "${mode.name} $text",
        )
        assertTrue("${mode.name} $text accent", bitmap.hasPixelNear(target))
        forbidden?.let {
            assertTrue("${mode.name} $text stale accent", !bitmap.hasPixelNear(it))
        }
    }

    private fun captureVisibleNode(
        node: SemanticsNodeInteraction,
        label: String,
    ): Bitmap {
        compose.waitUntil(timeoutMillis = 5_000) { nodeHasVisibleBounds(node) }
        compose.waitForIdle()
        assertTrue("$label must have visible non-zero bounds", nodeHasVisibleBounds(node))
        return captureBitmapWithRetry(label) {
            node.captureToImage().asAndroidBitmap()
        }
    }

    private fun nodeHasVisibleBounds(node: SemanticsNodeInteraction): Boolean = runCatching {
        val bounds = node.getUnclippedBoundsInRoot()
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val visibleWidth = minOf(bounds.right, rootBounds.right) - maxOf(bounds.left, rootBounds.left)
        val visibleHeight = minOf(bounds.bottom, rootBounds.bottom) - maxOf(bounds.top, rootBounds.top)
        visibleWidth.value > 0f && visibleHeight.value > 0f
    }.getOrDefault(false)

    private fun captureBitmapWithRetry(label: String, capture: () -> Bitmap): Bitmap {
        repeat(CAPTURE_ATTEMPTS) { attempt ->
            compose.waitForIdle()
            try {
                val bitmap = capture()
                check(bitmap.width > 0 && bitmap.height > 0) { "$label produced an empty bitmap" }
                return bitmap
            } catch (failure: Throwable) {
                if (!failure.isTransientCaptureFailure() || attempt == CAPTURE_ATTEMPTS - 1) {
                    throw failure
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(CAPTURE_RETRY_DELAY_MS)
            }
        }
        error("$label capture exhausted all attempts")
    }

    private fun Throwable.isTransientCaptureFailure(): Boolean {
        val detail = message.orEmpty()
        return detail.contains("PixelCopy") ||
            detail.contains("width and height must be > 0") ||
            detail.contains("produced an empty bitmap")
    }

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        return pixelCountNear(target) > 0
    }

    private fun Bitmap.pixelCountNear(target: Int): Int {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        var count = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(blue - targetBlue) <= COLOR_TOLERANCE
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private data class WarehouseNavGraphCase(
        val type: String,
        val expectedTitle: String,
        val screenshot: String,
    )

    private data class WarehouseGeometryCase(
        val type: String,
        val imageDescription: String,
    )

    private companion object {
        private const val SWIFT_TEXT_DARK_TITLE = 0xFF292929.toInt()
        private const val SWIFT_TEXT_DARK_TITLE_DARK = 0xFFFFFFFF.toInt()
        private const val FIGMA_ORANGE_LIGHT = 0xFFF15114.toInt()
        private const val FIGMA_DARK_FUNCTION_ORANGE = 0xFFF46427.toInt()
        private const val AIRDROP_ORANGE = 0xFFF15114.toInt()
        private const val COLOR_TOLERANCE = 8
        private const val CAPTURE_ATTEMPTS = 3
        private const val CAPTURE_RETRY_DELAY_MS = 150L
        private const val PROOF_SCREENSHOT_DIR = "Pictures/kotlin_ui_proof/home_refer_icon"
    }
}
