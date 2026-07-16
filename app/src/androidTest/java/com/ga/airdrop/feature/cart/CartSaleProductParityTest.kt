package com.ga.airdrop.feature.cart

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartSaleProductParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun cleanCart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
            SavedForLaterStore.clearAll()
        }
    }

    @Test
    fun saleProductUsesSwiftCardGeometryTypographyAndFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val line = CartStore.CartLine(
            id = 812,
            packageId = 812,
            title = "Swift Sale Product",
            priceUsd = 15.0,
            qty = 2,
            imageUrl = null,
            isAuction = true,
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CartStore.clear()
            SavedForLaterStore.init(context)
            SavedForLaterStore.clearAll()
            CartStore.add(line)
        }

        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                        .testTag("cart-sale-proof-root")
                ) {
                    val viewModel = remember { CartViewModel(FakeCheckoutRepository()) }
                    CartScreen(
                        onBack = {},
                        onShopNow = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("cart-sale-line-812").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("cart-sale-title-812", useUnmergedTree = true)
            .assertTextEquals("Swift Sale Product")
            .assertIsDisplayed()
        compose.onNodeWithTag("cart-sale-price-812", useUnmergedTree = true)
            .assertTextEquals("USD 30.00")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Product image unavailable",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithTag("cart-sale-remove-812", useUnmergedTree = true).assertIsDisplayed()

        val density = context.resources.displayMetrics.density
        val card = compose.onNodeWithTag("cart-sale-line-812").fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithTag("cart-sale-image-812", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val remove = compose.onNodeWithTag("cart-sale-remove-812", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertEquals(114f * density, card.height, 2f * density)
        assertEquals(84f * density, image.width, 1f * density)
        assertEquals(84f * density, image.height, 1f * density)
        assertEquals(15f * density, remove.top - card.top, 1f * density)
        assertEquals(20f * density, card.right - remove.right, 1f * density)

        val screenshotDir = File(context.getExternalFilesDir(null), "screenshots/cart_sale")
            .also { it.mkdirs() }
        val output = File(screenshotDir, "swift_sale_product_card.png")
        val bitmap = compose.onNodeWithTag("cart-sale-proof-root")
            .captureToImage()
            .asAndroidBitmap()
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class FakeCheckoutRepository : ShopCheckoutRepository {
        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
        ): Result<String> = Result.failure(IllegalStateException("Not used by visual proof"))

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)

        override suspend fun billingProfile(): Result<ShopBillingProfile> =
            Result.success(ShopBillingProfile(firstName = "Swift"))
    }
}
