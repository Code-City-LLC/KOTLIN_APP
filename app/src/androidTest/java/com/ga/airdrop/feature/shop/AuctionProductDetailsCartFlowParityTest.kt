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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuctionProductDetailsCartFlowParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun cleanCart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
        }
    }

    @Test
    fun addToCartCreatesOneSwiftCartLineAndViewCartRoutesToCart() {
        val navigations = mutableListOf<String>()

        setDetailsContent(navigations = navigations)
        waitForDetails()
        saveRootScreenshot("auction_details_cart_initial_light.png")

        compose.onNodeWithTag("auction-details-quantity-increase").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("auction-details-primary-cta").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Added to cart").fetchSemanticsNodes().isNotEmpty() &&
                CartStore.count == 1
        }

        compose.onNodeWithText("Added to cart").assertIsDisplayed()
        compose.onNodeWithText("${AuctionProduct.title} added. Tap View Cart to checkout.")
            .assertIsDisplayed()

        val line = CartStore.items.value.single()
        assertEquals("Swift product detail cart add is idempotent by product id", AuctionProduct.id, line.id)
        assertEquals("Swift hosted checkout cart line carries package id", AuctionProduct.packageId, line.packageId)
        assertEquals("Swift cart line uses product title", AuctionProduct.title, line.title)
        assertEquals("Swift product detail add stores a single checkout unit", 1, line.qty)
        assertEquals(AuctionProduct.priceUsd, line.priceUsd, 0.001)

        compose.onNodeWithText("View Cart").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { navigations == listOf(Routes.CART) }
    }

    @Test
    fun auctionCtaHidesWhenProductAlreadyInCart() {
        val navigations = mutableListOf<String>()

        setDetailsContent(
            seedCart = listOf(AuctionProduct.toCartLine(qty = 1)),
            navigations = navigations,
        )
        waitForDetails()

        // Swift syncBottomCTAWithCart hides the Add-to-Cart bar once the product
        // is in the cart, so there is no duplicate-add path from the detail CTA.
        compose.onNodeWithTag("auction-details-primary-cta").assertDoesNotExist()
        assertEquals("Seeded cart keeps its single line", 1, CartStore.count)
        assertEquals("Seeded cart line keeps its single unit", 1, CartStore.items.value.single().qty)
    }

    @Test
    fun headerCartIconRoutesToCartLikeSwiftHeader() {
        val navigations = mutableListOf<String>()

        setDetailsContent(
            seedCart = listOf(AuctionProduct.toCartLine(qty = 1)),
            navigations = navigations,
        )
        waitForDetails()

        compose.onNodeWithContentDescription("Cart").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { navigations == listOf(Routes.CART) }
    }

    private fun setDetailsContent(
        product: ShopProduct = AuctionProduct,
        related: List<ShopProduct> = emptyList(),
        seedCart: List<CartStore.CartLine> = emptyList(),
        navigations: MutableList<String>,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CartStore.clear()
            seedCart.forEach { CartStore.add(it) }
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
                    val viewModel = remember(product.routeSlug) {
                        AuctionProductDetailsViewModel(
                            slug = product.routeSlug,
                            featured = false,
                            products = repo,
                        )
                    }
                    AuctionProductDetailsScreen(
                        slug = product.routeSlug,
                        featured = false,
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
            "screenshots/auction_product_detail_cart",
        )
        dir.mkdirs()
        return dir
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

    private companion object {
        val AuctionProduct = ShopProduct(
            id = 9101,
            slug = "swift-auction-cart-proof",
            title = "Swift Auction Cart Proof",
            imageUrl = null,
            priceUsd = 9.2,
            regularPriceUsd = 12.0,
            inventory = 4,
            description = "Swift/Figma product detail cart proof.",
            packageId = 7101,
            createdAt = "2026-07-06T00:00:00Z",
        )
    }
}
