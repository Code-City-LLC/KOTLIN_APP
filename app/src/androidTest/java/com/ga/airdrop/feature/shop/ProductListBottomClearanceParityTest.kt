package com.ga.airdrop.feature.shop

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductListBottomClearanceParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun auctionListLastRowClearsSwiftBottomChrome() {
        setAuctionContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("product-list-grid")
            .performScrollToNode(hasTestTag("product-list-card-${AuctionProducts.last().id}"))
        scrollGridToBottom()
        compose.waitForIdle()

        val shell = compose.onNodeWithTag("product-list-shell").getUnclippedBoundsInRoot()
        val lastCard = compose
            .onNodeWithTag("product-list-card-${AuctionProducts.last().id}")
            .getUnclippedBoundsInRoot()
        val clearance = (shell.bottom - lastCard.bottom).value

        assertTrue(
            "Swift AuctionView contentInset.bottom is 124; last row clearance was $clearance",
            clearance >= 110f,
        )
    }

    @Test
    fun featureProductsListLastRowClearsSwiftBottomChromeInDark() {
        setFeaturedContent(ThemeController.Mode.DARK)

        compose.onNodeWithTag("product-list-grid")
            .performScrollToNode(hasTestTag("product-list-card-${FeatureProducts.last().id}"))
        scrollGridToBottom()
        compose.waitForIdle()

        val shell = compose.onNodeWithTag("product-list-shell").getUnclippedBoundsInRoot()
        val lastCard = compose
            .onNodeWithTag("product-list-card-${FeatureProducts.last().id}")
            .getUnclippedBoundsInRoot()
        val clearance = (shell.bottom - lastCard.bottom).value

        assertTrue(
            "Swift FeatureProducts contentInset.bottom is 124; last row clearance was $clearance",
            clearance >= 110f,
        )
    }

    private fun setAuctionContent(mode: ThemeController.Mode) {
        val viewModel = AuctionViewModel(
            products = FakeShopProductsRepository(
                auctionProducts = AuctionProducts,
                featureProducts = emptyList(),
            ),
        )
        setContent(mode) {
            AuctionScreen(
                onNavigate = {},
                onBack = {},
                viewModel = viewModel,
            )
        }
    }

    private fun setFeaturedContent(mode: ThemeController.Mode) {
        val viewModel = FeaturedProductsViewModel(
            products = FakeShopProductsRepository(
                auctionProducts = emptyList(),
                featureProducts = FeatureProducts,
            ),
        )
        setContent(mode) {
            FeaturedProductsScreen(
                onNavigate = {},
                onBack = {},
                viewModel = viewModel,
            )
        }
    }

    private fun setContent(
        mode: ThemeController.Mode,
        content: @Composable () -> Unit,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .testTag("product-list-shell"),
                ) {
                    content()
                }
            }
        }
        waitForProducts()
    }

    private fun waitForProducts() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Swift Product 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("product-list-grid").assertIsDisplayed()
    }

    private fun scrollGridToBottom() {
        repeat(4) {
            compose.onNodeWithTag("product-list-grid").performTouchInput {
                swipeUp()
            }
            compose.waitForIdle()
        }
    }

    private class FakeShopProductsRepository(
        private val auctionProducts: List<ShopProduct>,
        private val featureProducts: List<ShopProduct>,
    ) : ShopProductsRepository {
        override suspend fun auctionProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(auctionProducts)

        override suspend fun featuredProducts(page: Int, perPage: Int, search: String?): Result<List<ShopProduct>> =
            Result.success(featureProducts)

        override suspend fun productBySlug(slug: String, featured: Boolean): Result<ShopProduct> =
            Result.success((auctionProducts + featureProducts).first { it.routeSlug == slug })
    }

    private companion object {
        val AuctionProducts = products(idStart = 9100, slugPrefix = "auction")
        val FeatureProducts = products(idStart = 9200, slugPrefix = "feature")

        fun products(idStart: Int, slugPrefix: String): List<ShopProduct> =
            List(9) { index ->
                ShopProduct(
                    id = idStart + index,
                    slug = "swift-$slugPrefix-bottom-clearance-$index",
                    title = "Swift Product ${index + 1}",
                    imageUrl = null,
                    priceUsd = 1550.0,
                    packageId = 7000 + idStart + index,
                    createdAt = "2026-07-06T00:00:00Z",
                )
            }
    }
}
