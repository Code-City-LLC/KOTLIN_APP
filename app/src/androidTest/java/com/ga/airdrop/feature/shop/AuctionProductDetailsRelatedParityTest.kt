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
import com.ga.airdrop.core.navigation.Routes
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuctionProductDetailsRelatedParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun auctionModeKeepsSwiftRelatedSkeletonsWhenRelatedDataIsEmpty() {
        val navigations = mutableListOf<String>()

        setDetailsContent(featured = false, related = emptyList(), navigations = navigations)

        waitForDetails()
        compose.onNodeWithText("Related Products").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auction-related-skeleton-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auction-related-skeleton-1").assertIsDisplayed()
        assertClose(
            expected = 220f,
            actual = boundsHeight(compose.onNodeWithTag("auction-related-skeleton-0").getUnclippedBoundsInRoot()),
            label = "Swift related skeleton height",
        )
        saveRootScreenshot("auction_related_empty_swift_light.png")

        compose.onNodeWithText("View More").performScrollTo().performClick()

        assertEquals(listOf(Routes.AUCTION), navigations)
    }

    @Test
    fun featuredModeStillOmitsRelatedProductsSection() {
        setDetailsContent(featured = true, related = emptyList(), navigations = mutableListOf())

        waitForDetails()

        assertTrue(
            "Featured product details should not render the auction-only related section",
            compose.onAllNodesWithText("Related Products").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun nullDescriptionUsesSwiftFallbackCopy() {
        setDetailsContent(
            featured = false,
            product = SampleProduct.copy(description = null),
            related = emptyList(),
            navigations = mutableListOf(),
        )

        waitForDetails()

        compose.onNodeWithText(SwiftDescriptionFallback).performScrollTo().assertIsDisplayed()
        saveRootScreenshot("auction_description_fallback_swift_light.png")
        assertTrue(
            "Product details should not use the old Android-only null-description copy",
            compose.onAllNodesWithText("No description available.").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun featuredInvalidPurchaseLinkShowsSwiftUnavailableAlert() {
        setDetailsContent(
            featured = true,
            product = SampleProduct.copy(
                slug = "swift-invalid-feature-link",
                amazonUrl = "not a valid url",
            ),
            related = emptyList(),
            navigations = mutableListOf(),
        )

        waitForDetails()
        compose.onNodeWithText("Purchase Product").performClick()

        compose.onNodeWithText("Product link unavailable").assertIsDisplayed()
        compose.onNodeWithText("The purchase link is not a valid URL.").assertIsDisplayed()
    }

    private fun setDetailsContent(
        featured: Boolean,
        product: ShopProduct = SampleProduct,
        related: List<ShopProduct>,
        navigations: MutableList<String>,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    val repo = remember { FakeShopProductsRepository(product = product, related = related) }
                    val viewModel = remember(featured) {
                        AuctionProductDetailsViewModel(
                            slug = product.routeSlug,
                            featured = featured,
                            products = repo,
                        )
                    }
                    AuctionProductDetailsScreen(
                        slug = product.routeSlug,
                        featured = featured,
                        onNavigate = { navigations.add(it) },
                        onBack = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    private fun waitForDetails() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty()
        }
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
            "screenshots/auction_related_empty",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private companion object {
        const val SwiftDescriptionFallback =
            "Detailed product description will be loaded from the /products/:id endpoint once authenticated. " +
                "Includes specifications, dimensions, condition, and seller notes."

        val SampleProduct = ShopProduct(
            id = 7001,
            slug = "swift-related-parity-bag",
            title = "Swift Related Parity Bag",
            imageUrl = null,
            priceUsd = 9.20,
            regularPriceUsd = 12.00,
            inventory = 4,
            description = "Detailed product description for the Swift related-products parity check.",
            amazonUrl = "https://www.amazon.com/sample",
            packageId = 8801,
        )
    }
}
