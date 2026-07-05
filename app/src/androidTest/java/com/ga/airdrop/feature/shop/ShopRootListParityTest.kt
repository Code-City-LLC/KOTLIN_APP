package com.ga.airdrop.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShopRootListParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shopRootProductCardUsesSwiftOneLineTitleAndFixedHeight() {
        setCardContent(
            tag = "shop-root-card",
            titleLines = 1,
            rootInsets = true,
        )

        compose.onNodeWithTag("shop-root-card").assertIsDisplayed()
        assertClose(
            expected = 245f,
            actual = boundsHeight(compose.onNodeWithTag("shop-root-card").getUnclippedBoundsInRoot()),
            label = "Swift shop-root product card height",
        )
        assertTrue(
            "Swift shop-root card should render the product title as one visible line",
            boundsHeight(compose.onNodeWithText(LongTitle, useUnmergedTree = true).getUnclippedBoundsInRoot()) < 32f,
        )
    }

    @Test
    fun productListCardUsesSwiftTwoLineTitleAndFixedHeight() {
        setCardContent(
            tag = "product-list-card",
            titleLines = 2,
            rootInsets = false,
        )

        compose.onNodeWithTag("product-list-card").assertIsDisplayed()
        assertClose(
            expected = 245f,
            actual = boundsHeight(compose.onNodeWithTag("product-list-card").getUnclippedBoundsInRoot()),
            label = "Swift product-list card height",
        )
        val titleHeight = boundsHeight(
            compose.onNodeWithText(LongTitle, useUnmergedTree = true).getUnclippedBoundsInRoot(),
        )
        assertTrue(
            "Swift product-list card should reserve two visible title lines",
            titleHeight in 44f..56f,
        )
    }

    @Test
    fun featuredEmptyCardKeepsSwiftFixedWidth() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    ShopEmptyCard(
                        text = "No featured products",
                        modifier = Modifier
                            .width(240.dp)
                            .testTag("featured-empty-card"),
                    )
                }
            }
        }

        compose.onNodeWithTag("featured-empty-card").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("featured-empty-card").getUnclippedBoundsInRoot()
        assertClose(240f, boundsWidth(bounds), "Swift featured empty-card width")
        assertClose(200f, boundsHeight(bounds), "Swift featured empty-card height")
    }

    @Test
    fun fullListSearchBackspaceReloadsUnfilteredLikeSwift() {
        val repo = RecordingShopProductsRepository()
        lateinit var viewModel: ProductListViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ProductListViewModel(featured = false, products = repo)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repo.auctionSearches.size >= 1
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.onQueryChange("abc")
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repo.auctionSearches.contains("abc")
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.onQueryChange("ab")
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            repo.auctionSearches.size >= 3 && repo.auctionSearches.last() == null
        }

        assertEquals(
            "Swift list VCs reload after backspacing below three chars and omit the search param",
            listOf(null, "abc", null),
            repo.auctionSearches.take(3),
        )
    }

    private fun setCardContent(
        tag: String,
        titleLines: Int,
        rootInsets: Boolean,
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
                        .background(AirdropTheme.colors.gray200),
                ) {
                    ShopProductCard(
                        product = SampleProduct,
                        inCart = false,
                        onClick = {},
                        onToggleCart = {},
                        modifier = Modifier
                            .width(160.dp)
                            .testTag(tag),
                        titleLines = titleLines,
                        rootInsets = rootInsets,
                    )
                }
            }
        }
    }

    private class RecordingShopProductsRepository : ShopProductsRepository {
        val auctionSearches = Collections.synchronizedList(mutableListOf<String?>())

        override suspend fun auctionProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> {
            auctionSearches += search
            return Result.success(List(perPage) { index -> SampleProduct.copy(id = page * 100 + index) })
        }

        override suspend fun featuredProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> = Result.success(emptyList())

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.success(SampleProduct)
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private companion object {
        const val LongTitle =
            "Apple 2023 MacBook Pro Laptop M3 chip with a very long title that should wrap only when Swift allows it"

        val SampleProduct = ShopProduct(
            id = 9101,
            slug = "swift-shop-card-parity",
            title = LongTitle,
            imageUrl = null,
            priceUsd = 1550.0,
            packageId = 9101,
            createdAt = "2026-01-01T00:00:00Z",
        )
    }
}
