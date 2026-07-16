package com.ga.airdrop.feature.shop

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
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
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuctionProductDetailsFullVisualParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun auctionProductDetailsLocksSwiftVisualGeometryLight() {
        setDetailsContent(mode = ThemeController.Mode.LIGHT)
        waitForDetails()

        assertTopGeometry()
        saveRootScreenshot("auction_product_details_full_swift_light.png")
    }

    @Test
    fun auctionProductDetailsLocksSwiftVisualGeometryDark() {
        setDetailsContent(mode = ThemeController.Mode.DARK)
        waitForDetails()

        assertTopGeometry()
        saveRootScreenshot("auction_product_details_full_swift_dark.png")
    }

    @Test
    fun descriptionExpansionKeepsSwiftSeeAllRail() {
        setDetailsContent(mode = ThemeController.Mode.LIGHT)
        waitForDetails()

        compose.onNodeWithText("See all").performScrollTo().performClick()
        compose.onNodeWithText("See less").assertIsDisplayed()

        compose.onNodeWithText("See less").performScrollTo().performClick()
        compose.onNodeWithText("See all").assertIsDisplayed()
    }

    private fun setDetailsContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    val repo = remember {
                        FakeShopProductsRepository(product = Product, related = emptyList())
                    }
                    val viewModel = remember {
                        AuctionProductDetailsViewModel(
                            slug = Product.routeSlug,
                            featured = false,
                            products = repo,
                        )
                    }
                    AuctionProductDetailsScreen(
                        slug = Product.routeSlug,
                        featured = false,
                        onNavigate = {},
                        onBack = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    private fun assertTopGeometry() {
        compose.onNodeWithText("Sale").assertIsDisplayed()

        val hero = compose.onNodeWithTag("auction-details-hero-card").getUnclippedBoundsInRoot()
        assertClose(20f, hero.left.value, "Swift hero leading inset")
        assertClose(335f, boundsWidth(hero), "Swift hero width")
        assertClose(240f, boundsHeight(hero), "Swift hero height")

        val placeholder = compose.onNodeWithTag("auction-details-hero-placeholder").getUnclippedBoundsInRoot()
        assertClose(96f, boundsWidth(placeholder), "Swift placeholder width")
        assertClose(96f, boundsHeight(placeholder), "Swift placeholder height")

        compose.onNodeWithTag("auction-details-dots-row").assertIsDisplayed()
        repeat(3) { index ->
            val dot = compose.onNodeWithTag("auction-details-dot-$index").getUnclippedBoundsInRoot()
            assertClose(8f, boundsWidth(dot), "Swift dot $index width")
            assertClose(8f, boundsHeight(dot), "Swift dot $index height")
        }

        compose.onNodeWithTag("auction-details-stats-row").assertIsDisplayed()
        compose.onNodeWithText("0 Reviews").assertIsDisplayed()
        compose.onNodeWithText("50 Shares").assertIsDisplayed()
        compose.onNodeWithText(Product.title).assertIsDisplayed()
        compose.onNodeWithText("Model: ${Product.slug!!.uppercase()}").assertIsDisplayed()
        compose.onNodeWithText("$18.00").assertIsDisplayed()
        compose.onNodeWithText("$12.50").assertIsDisplayed()
        compose.onNodeWithText("Stock Quantity: ${Product.inventory}").assertIsDisplayed()

        val stepper = compose.onNodeWithTag("auction-details-quantity-stepper").getUnclippedBoundsInRoot()
        assertClose(132f, boundsWidth(stepper), "Swift quantity stepper width")
        assertClose(44f, boundsHeight(stepper), "Swift quantity stepper height")

        compose.onNodeWithText("Description").assertIsDisplayed()
        compose.onNodeWithText("See all").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Related Products").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View More").assertIsDisplayed()
        compose.onNodeWithTag("auction-related-skeleton-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auction-related-skeleton-1").assertIsDisplayed()
        val skeleton = compose.onNodeWithTag("auction-related-skeleton-0").getUnclippedBoundsInRoot()
        assertClose(220f, boundsHeight(skeleton), "Swift related skeleton height")

        compose.onNodeWithTag("auction-details-primary-cta").assertIsDisplayed()
        val cta = compose.onNodeWithTag("auction-details-primary-cta").getUnclippedBoundsInRoot()
        assertClose(335f, boundsWidth(cta), "Swift bottom CTA width")
        assertClose(52f, boundsHeight(cta), "Swift bottom CTA height")
    }

    private class FakeShopProductsRepository(
        private val product: ShopProduct,
        private val related: List<ShopProduct>,
    ) : ShopProductsRepository {
        override suspend fun auctionProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(related)

        override suspend fun featuredProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(emptyList())

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.success(product)
    }

    private fun waitForDetails() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/auction_product_details_full",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private companion object {
        val Product = ShopProduct(
            id = 7301,
            slug = "swift-full-visual-proof",
            title = "Swift Full Visual Proof",
            imageUrl = null,
            priceUsd = 12.5,
            regularPriceUsd = 18.0,
            inventory = 9,
            description = "Swift visual guard paragraph with enough words to keep the collapsed description rail active. " +
                "The copy verifies the See all and See less control without changing the product detail layout.",
            packageId = 8301,
            createdAt = "2026-07-06T00:00:00Z",
        )
    }
}
