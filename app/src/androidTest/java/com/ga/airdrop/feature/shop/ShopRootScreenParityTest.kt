package com.ga.airdrop.feature.shop

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShopRootScreenParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun cleanCart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
        }
    }

    @Test
    fun shopRootMatchesSwiftFigmaGeometryAndFeaturedCardsInLight() {
        assertShopRootGeometryAndFeaturedCards(ThemeController.Mode.LIGHT)
    }

    @Test
    fun shopRootMatchesSwiftFigmaGeometryAndFeaturedCardsInDark() {
        assertShopRootGeometryAndFeaturedCards(ThemeController.Mode.DARK)
    }

    @Test
    fun shopRootAuctionCardAndCartRailsUseSwiftRuntimeSplit() {
        val navigations = mutableListOf<String>()

        setShopRootContent(mode = ThemeController.Mode.LIGHT, navigations = navigations)
        waitForShopData()

        compose.onAllNodesWithContentDescription("Add to cart")[0].performClick()
        compose.waitUntil(timeoutMillis = 5_000) { CartStore.count == 1 }
        assertEquals("Auction plus toggles CartStore without opening details", emptyList<String>(), navigations)
        assertEquals("Swift root auction plus stores one checkout unit", 1, CartStore.items.value.single().qty)

        compose.onNodeWithTag("shop-root-auction-card-${AuctionProducts.first().id}").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            navigations == listOf(Routes.auctionProductDetails(AuctionProducts.first().routeSlug, false))
        }
    }

    @Test
    fun shopRootFeaturedCardRoutesToFeaturedDetailsWithoutCartToggle() {
        val navigations = mutableListOf<String>()

        setShopRootContent(mode = ThemeController.Mode.LIGHT, navigations = navigations)
        waitForShopData()

        assertEquals(
            "Swift Shop root omits plus buttons on the horizontal Feature Products cards",
            AuctionProducts.size,
            compose.onAllNodesWithContentDescription("Add to cart").fetchSemanticsNodes().size,
        )

        compose.onNodeWithTag("shop-root-featured-card-${FeaturedProducts.first().id}").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            navigations == listOf(Routes.auctionProductDetails(FeaturedProducts.first().routeSlug, true))
        }
    }

    private fun assertShopRootGeometryAndFeaturedCards(mode: ThemeController.Mode) {
        setShopRootContent(mode = mode, navigations = mutableListOf())
        waitForShopData()

        compose.onNodeWithTag("shop-root").assertIsDisplayed()
        compose.onNodeWithTag("shop-root-search").assertIsDisplayed()
        compose.onNodeWithTag("shop-root-auction-card-${AuctionProducts.first().id}").assertIsDisplayed()
        compose.onNodeWithTag("shop-root-featured-card-${FeaturedProducts.first().id}").assertIsDisplayed()

        val search = compose.onNodeWithTag("shop-root-search").getUnclippedBoundsInRoot()
        assertClose(20f, search.left.value, "Swift/Figma Shop search left")
        assertClose(126f, search.top.value, "Swift/Figma Shop search top")
        assertClose(335f, boundsWidth(search), "Swift/Figma Shop search width")
        assertClose(50f, boundsHeight(search), "Swift/Figma Shop search height")

        val auctionCard = compose
            .onNodeWithTag("shop-root-auction-card-${AuctionProducts.first().id}")
            .getUnclippedBoundsInRoot()
        assertClose(245f, boundsHeight(auctionCard), "Swift Shop auction card height")
        assertClose(162.5f, boundsWidth(auctionCard), "Swift Shop auction card width", tolerance = 2f)

        val featuredCard = compose
            .onNodeWithTag("shop-root-featured-card-${FeaturedProducts.first().id}")
            .getUnclippedBoundsInRoot()
        assertClose(160f, boundsWidth(featuredCard), "Figma/Swift featured root card width")
        assertClose(245f, boundsHeight(featuredCard), "Figma/Swift featured root card height")

        assertShopRootFrostedHeader(mode)

        val filename = if (mode == ThemeController.Mode.DARK) {
            "shop_root_swift_figma_dark.png"
        } else {
            "shop_root_swift_figma_light.png"
        }
        saveRootScreenshot(filename)
    }

    private fun assertShopRootFrostedHeader(mode: ThemeController.Mode) {
        compose.onNodeWithTag("shop-root-header").assertIsDisplayed()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val header = compose.onNodeWithTag("shop-root-header").getUnclippedBoundsInRoot()
        val expectedSurface = if (mode == ThemeController.Mode.DARK) darkGray200Rgb else lightGray200Rgb
        val expectedDivider = if (mode == ThemeController.Mode.DARK) darkIconShapeRgb else lightIconShapeRgb

        listOf(
            PixelSample(
                point = samplePoint(header, xFraction = 0.04f, yInsetDp = 6f),
                expectedRgb = expectedSurface,
                label = "Swift Shop frosted header top surface",
            ),
            PixelSample(
                point = samplePoint(header, xFraction = 0.04f, yInsetDp = header.heightDp() - 6f),
                expectedRgb = expectedSurface,
                label = "Swift Shop frosted header bottom surface",
            ),
            PixelSample(
                point = samplePoint(header, xFraction = 0.5f, yInsetDp = header.heightDp() - 0.5f),
                expectedRgb = expectedDivider,
                label = "Swift Shop frosted header 1dp divider",
            ),
        ).forEach { sample ->
            assertPixelNear(bitmap, sample.point, sample.expectedRgb, sample.label)
        }
    }

    private fun setShopRootContent(
        mode: ThemeController.Mode,
        navigations: MutableList<String>,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            CartStore.init(context)
            CartStore.clear()
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    val repo = remember { FakeShopProductsRepository() }
                    val viewModel = remember { ShopViewModel(products = repo) }
                    ShopScreen(
                        onNavigate = { navigations.add(it) },
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun waitForShopData() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(AuctionProducts.first().title).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText(FeaturedProducts.first().title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/shop_root_swift_figma",
        )
        dir.mkdirs()
        return dir
    }

    private fun saveScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/shop_root_swift_figma/"
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

    private class FakeShopProductsRepository : ShopProductsRepository {
        override suspend fun auctionProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(AuctionProducts)

        override suspend fun featuredProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(FeaturedProducts)

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.success((AuctionProducts + FeaturedProducts).first { it.routeSlug == slug })
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun samplePoint(bounds: DpRect, xFraction: Float, yInsetDp: Float): Pair<Int, Int> {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
            .density
        val x = ((bounds.left.value + (bounds.widthDp() * xFraction)) * density).toInt()
        val y = ((bounds.top.value + yInsetDp) * density).toInt()
        return x to y
    }

    private fun assertPixelNear(
        bitmap: Bitmap,
        point: Pair<Int, Int>,
        expectedRgb: IntArray,
        label: String,
    ) {
        val color = bitmap.getPixel(
            point.first.coerceIn(0, bitmap.width - 1),
            point.second.coerceIn(0, bitmap.height - 1),
        )
        val actual = intArrayOf(
            AndroidColor.red(color),
            AndroidColor.green(color),
            AndroidColor.blue(color),
        )
        val close = actual.indices.all { abs(actual[it] - expectedRgb[it]) <= 2 }
        assertTrue(
            "$label expected=${expectedRgb.joinToString()} actual=${actual.joinToString()}",
            close,
        )
    }

    private data class PixelSample(
        val point: Pair<Int, Int>,
        val expectedRgb: IntArray,
        val label: String,
    )

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private fun DpRect.widthDp(): Float = (right - left).value

    private fun DpRect.heightDp(): Float = (bottom - top).value

    private companion object {
        val lightGray200Rgb = intArrayOf(245, 245, 245)
        val darkGray200Rgb = intArrayOf(51, 51, 51)
        val lightIconShapeRgb = intArrayOf(229, 229, 229)
        val darkIconShapeRgb = intArrayOf(77, 77, 77)

        val AuctionProducts = listOf(
            sampleProduct(1001, "swift-root-auction-one", "Auction One"),
            sampleProduct(1002, "swift-root-auction-two", "Auction Two"),
            sampleProduct(1003, "swift-root-auction-three", "Auction Three"),
            sampleProduct(1004, "swift-root-auction-four", "Auction Four"),
        )

        val FeaturedProducts = listOf(
            sampleProduct(2001, "swift-root-featured-one", "Featured One"),
            sampleProduct(2002, "swift-root-featured-two", "Featured Two"),
            sampleProduct(2003, "swift-root-featured-three", "Featured Three"),
            sampleProduct(2004, "swift-root-featured-four", "Featured Four"),
        )

        fun sampleProduct(id: Int, slug: String, title: String): ShopProduct = ShopProduct(
            id = id,
            slug = slug,
            title = title,
            imageUrl = null,
            priceUsd = 1550.0,
            packageId = 7000 + id,
            createdAt = "2026-07-06T00:00:00Z",
        )
    }
}
