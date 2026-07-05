package com.ga.airdrop.feature.home

import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
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
    fun standardWarehouseCardOpensWarehouseScreenFromAppRoot() {
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
                compose.onAllNodesWithTag("home-warehouse-standard").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("home-warehouse-standard").performClick()
            compose.waitUntil(timeoutMillis = 8_000) {
                compose.onAllNodesWithText("AirDrop (Air Freight)").fetchSemanticsNodes().isNotEmpty()
            }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val output = File(screenshotDir(), "home_warehouse_standard_after_tap.png")
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
}
