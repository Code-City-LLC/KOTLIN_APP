package com.ga.airdrop.feature.home

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.AppRoot
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AuctionProduct
import com.ga.airdrop.feature.cart.CartStore
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
    val compose = createComposeRule()

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
    fun homeActionCardsEmitSwiftRoutes() {
        val navigatedRoutes = mutableListOf<String>()
        setHomeContent { route -> navigatedRoutes += route }

        listOf(
            "home-activity-services" to Routes.SERVICES,
            "home-activity-ship-tax" to Routes.SALES_TAXES,
            "home-activity-calculator" to Routes.CALCULATOR,
            "home-activity-drop-alert" to Routes.DROP_ALERT,
        ).forEach { (tag, route) ->
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

            compose.onNodeWithTag("home-auction-cart-toggle").performClick()
            compose.runOnIdle {
                assertEquals(emptyList<String>(), navigatedRoutes)
                assertEquals(1, CartStore.count)
                assertEquals(product.id, CartStore.items.value.single().id)
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
    fun referFriendCardUsesSwiftSingleToneIconLight() {
        assertReferFriendIconPalette(
            mode = ThemeController.Mode.LIGHT,
            targetColor = SWIFT_TEXT_DARK_TITLE,
            screenshot = "home_refer_friend_swift_light.png",
        )
    }

    @Test
    fun referFriendCardUsesSwiftSingleToneIconDark() {
        assertReferFriendIconPalette(
            mode = ThemeController.Mode.DARK,
            targetColor = SWIFT_TEXT_DARK_TITLE_DARK,
            screenshot = "home_refer_friend_swift_dark.png",
        )
    }

    @Test
    fun standardWarehouseCardOpensWarehouseScreenFromAppRoot() {
        assertWarehouseCardOpensFromAppRoot(
            WarehouseAppRootCase(
                type = "standard",
                expectedTitle = "AirDrop (Air Freight)",
                screenshot = "home_warehouse_standard_after_tap.png",
            )
        )
    }

    @Test
    fun seadropWarehouseCardOpensWarehouseScreenFromAppRoot() {
        assertWarehouseCardOpensFromAppRoot(
            WarehouseAppRootCase(
                type = "seadrop",
                expectedTitle = "SeaDrop (Sea Freight)",
                screenshot = "home_warehouse_seadrop_after_tap.png",
            )
        )
    }

    @Test
    fun expressWarehouseCardOpensWarehouseScreenFromAppRoot() {
        assertWarehouseCardOpensFromAppRoot(
            WarehouseAppRootCase(
                type = "express",
                expectedTitle = "Express (Air Express)",
                screenshot = "home_warehouse_express_after_tap.png",
            )
        )
    }

    private fun assertWarehouseCardOpensFromAppRoot(warehouseCase: WarehouseAppRootCase) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            AuthTokenStore.save("ui-proof-token")
        }

        try {
            compose.setContent {
                AirdropTheme {
                    AppRoot()
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
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val output = File(screenshotDir(), warehouseCase.screenshot)
            FileOutputStream(output).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            instrumentation.runOnMainSync {
                AuthTokenStore.clear()
            }
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

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
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
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PROOF_SCREENSHOT_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create proof screenshot $filename" }

        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open proof screenshot $filename" }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
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

        assertIconContainsColor("home-refer-friend-icon", targetColor, "Swift textDarkTitle refer icon")
        assertIconDoesNotContainColor(
            tag = "home-refer-friend-icon",
            target = STALE_FIGMA_ORANGE,
            label = "Figma orange accent must not render on Swift-precedence Home refer icon",
        )
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

    private fun assertIconContainsColor(tag: String, target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertIconDoesNotContainColor(tag: String, target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(label, !bitmap.hasPixelNear(target))
    }

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
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
                    return true
                }
            }
        }
        return false
    }

    private data class WarehouseAppRootCase(
        val type: String,
        val expectedTitle: String,
        val screenshot: String,
    )

    private companion object {
        private const val SWIFT_TEXT_DARK_TITLE = 0xFF292929.toInt()
        private const val SWIFT_TEXT_DARK_TITLE_DARK = 0xFFFFFFFF.toInt()
        private const val STALE_FIGMA_ORANGE = 0xFFF15114.toInt()
        private const val COLOR_TOLERANCE = 8
        private const val PROOF_SCREENSHOT_DIR = "Pictures/kotlin_ui_proof/home_refer_icon"
    }
}
