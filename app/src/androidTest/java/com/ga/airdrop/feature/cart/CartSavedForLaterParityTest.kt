package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartSavedForLaterParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun cleanStores() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
            SavedForLaterStore.clearAll()
        }
    }

    @Test
    fun longPressSavesLineAndViewerMovesItBackToCart() {
        setCartContent(
            listOf(
                CartStore.CartLine(id = 2001, packageId = 7001, title = "Alpha Radio", priceUsd = 5.0),
                CartStore.CartLine(id = 2002, packageId = 7002, title = "Beta Lamp", priceUsd = 7.0),
            )
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Basket (2 Items)").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("cart-line-2001").performTouchInput { longClick() }
        compose.onNodeWithTag("cart-action-save-for-later").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            CartStore.count == 1 && SavedForLaterStore.count == 1
        }
        assertEquals("Save for Later removes only the selected active cart line", 1, CartStore.count)
        assertEquals("Saved for Later is kept outside the checkout cart count", 1, SavedForLaterStore.count)
        compose.onNodeWithText("Basket (1 Item)").assertIsDisplayed()
        compose.onNodeWithTag("cart-saved-pill").assertIsDisplayed()

        compose.onNodeWithTag("cart-saved-pill").performClick()
        compose.onNodeWithTag("saved-for-later-screen").assertIsDisplayed()
        compose.onNodeWithText("Saved for Later").assertIsDisplayed()
        compose.onNodeWithText("Alpha Radio").assertIsDisplayed()
        compose.onNodeWithTag("saved-move-2001").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            CartStore.count == 2 && SavedForLaterStore.count == 0
        }
        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Basket (2 Items)").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Saved pill hides at zero, matching Swift applySavedCountToHeader",
            compose.onAllNodesWithTag("cart-saved-pill").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun savedViewerRemoveDeletesOnlySavedCopy() {
        setCartContent(
            cartLines = listOf(
                CartStore.CartLine(id = 3002, packageId = 8002, title = "Active Lamp", priceUsd = 7.0),
            ),
            savedLines = listOf(
                CartStore.CartLine(id = 3001, packageId = 8001, title = "Saved Radio", priceUsd = 5.0),
            ),
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("cart-saved-pill").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("cart-saved-pill").performClick()
        compose.onNodeWithTag("saved-remove-3001").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            CartStore.count == 1 && SavedForLaterStore.count == 0
        }
        assertEquals("Remove from saved must not remove active cart lines", 1, CartStore.count)
        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
    }

    private fun setCartContent(
        cartLines: List<CartStore.CartLine>,
        savedLines: List<CartStore.CartLine> = emptyList(),
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            SavedForLaterStore.init(context)
            CartStore.clear()
            SavedForLaterStore.clearAll()
            cartLines.forEach { CartStore.add(it) }
            savedLines.forEach { SavedForLaterStore.save(it) }
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    val viewModel = remember { CartViewModel(FakeCartCheckoutRepository()) }
                    CartScreen(
                        onBack = {},
                        onShopNow = {},
                        viewModel = viewModel,
                        openCheckoutUrl = {},
                    )
                }
            }
        }
    }

    private class FakeCartCheckoutRepository : ShopCheckoutRepository {
        val checkoutCalls = AtomicInteger()

        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
        ): Result<String> {
            checkoutCalls.incrementAndGet()
            return Result.success("https://checkout.airdropja.test/cart-session")
        }

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)

        override suspend fun billingProfile(): Result<ShopBillingProfile> =
            Result.success(ShopBillingProfile(firstName = "John"))
    }
}
