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

    private fun setDetailsContent(
        featured: Boolean,
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
                    val repo = remember { FakeShopProductsRepository(related = related) }
                    val viewModel = remember(featured) {
                        AuctionProductDetailsViewModel(
                            slug = SampleProduct.routeSlug,
                            featured = featured,
                            products = repo,
                        )
                    }
                    AuctionProductDetailsScreen(
                        slug = SampleProduct.routeSlug,
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
        private val related: List<ShopProduct>,
    ) : ShopProductsRepository {
        override suspend fun auctionProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(related)

        override suspend fun featuredProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(emptyList())

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.success(SampleProduct)
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
        val SampleProduct = ShopProduct(
            id = 7001,
            slug = "swift-related-parity-bag",
            title = "Swift Related Parity Bag",
            imageUrl = null,
            priceUsd = 9.20,
            regularPriceUsd = 12.00,
            inventory = 4,
            description = "Detailed product description for the Swift related-products parity check.",
            packageId = 8801,
        )
    }
}
