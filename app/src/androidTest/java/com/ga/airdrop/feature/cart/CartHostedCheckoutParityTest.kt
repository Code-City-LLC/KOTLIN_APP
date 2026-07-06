package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartHostedCheckoutParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun cleanCart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
        }
    }

    @Test
    fun makePaymentSendsSwiftCheckoutPayloadOpensUrlAndPreservesCartUntilVerifiedReturn() {
        val repo = FakeCartCheckoutRepository()
        val openedUrl = AtomicReference<String?>()
        val cartCountDuringOpen = AtomicInteger(-1)

        setCartContent(
            repo = repo,
            lines = listOf(
                CartStore.CartLine(id = 2002, packageId = 7002, title = "Beta Lamp", priceUsd = 7.0),
                CartStore.CartLine(id = 2001, packageId = 7001, title = "Alpha Radio", priceUsd = 5.0),
            ),
            openCheckoutUrl = { url ->
                openedUrl.set(url)
                cartCountDuringOpen.set(CartStore.count)
            },
        )

        waitForCart()
        compose.onNodeWithText("Make Payment").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            openedUrl.get() == CheckoutUrl
        }

        assertEquals("Swift Cart sends sorted package IDs from FigmaCartStore lines", listOf(7001, 7002), repo.lastPackageIds)
        assertEquals("Swift Cart checkout always pays in USD", "USD", repo.lastCurrency)
        assertEquals("Swift Cart checkout sends is_auction=true", true, repo.lastIsAuction)
        assertEquals("Cart must still exist while the hosted checkout open callback fires", 2, cartCountDuringOpen.get())
        assertEquals("Stripe hosted checkout URL should be opened once", CheckoutUrl, openedUrl.get())
        assertEquals(
            "Cart stays until the verified checkout-return flow confirms payment",
            2,
            CartStore.count,
        )
    }

    @Test
    fun yourNoteRowOpensFigmaPopupAndSavesPreviewDark() {
        setCartContent(
            repo = FakeCartCheckoutRepository(),
            mode = ThemeController.Mode.DARK,
            lines = listOf(
                CartStore.CartLine(id = 5001, packageId = 9001, title = "Swift Note Package", priceUsd = 11.0),
            ),
            openCheckoutUrl = {},
        )

        waitForCart()
        compose.onNodeWithTag("cart-note-row").performScrollTo().assertIsDisplayed()
        assertClose(
            59f,
            boundsHeight(compose.onNodeWithTag("cart-note-row").getUnclippedBoundsInRoot()),
            "Figma Your Note row height",
        )
        compose.onNodeWithTag("cart-note-chat-icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("cart-note-chevron", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag("cart-note-row").performClick()
        compose.onNodeWithTag("cart-note-dialog").assertIsDisplayed()
        assertClose(
            337f,
            boundsWidth(compose.onNodeWithTag("cart-note-dialog").getUnclippedBoundsInRoot()),
            "Figma Your Note popup width",
        )
        assertClose(
            566f,
            boundsHeight(compose.onNodeWithTag("cart-note-dialog").getUnclippedBoundsInRoot()),
            "Figma Your Note popup height",
        )
        assertClose(
            332f,
            boundsHeight(compose.onNodeWithTag("cart-note-input-card").getUnclippedBoundsInRoot()),
            "Figma Your Note text area height",
        )
        compose.onNodeWithTag("cart-note-dialog-title").assertIsDisplayed()
        compose.onNodeWithText("Enter Your text").assertIsDisplayed()

        compose.onNodeWithTag("cart-note-input").performTextInput("Leave at front desk")
        compose.onNodeWithTag("cart-note-save").performClick()

        assertTrue(compose.onAllNodesWithTag("cart-note-dialog").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Your Note — Leave at front desk").assertIsDisplayed()
    }

    @Test
    fun unauthenticatedCheckoutUsesSwiftSignInRequiredAlert() {
        val repo = FakeCartCheckoutRepository(
            checkoutFailure = IllegalStateException("Unauthenticated"),
        )
        val openedUrl = AtomicReference<String?>()

        setCartContent(
            repo = repo,
            lines = listOf(
                CartStore.CartLine(id = 3001, packageId = 8001, title = "Swift Cart Line", priceUsd = 12.0),
            ),
            openCheckoutUrl = { openedUrl.set(it) },
        )

        waitForCart()
        compose.onNodeWithText("Make Payment").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Sign in required").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Sign in required").assertIsDisplayed()
        compose.onNodeWithText("Log in to your Airdropja account before checking out.").assertIsDisplayed()
        assertTrue(
            "Swift unauthenticated Cart checkout should not surface the generic failure title",
            compose.onAllNodesWithText("Checkout failed").fetchSemanticsNodes().isEmpty(),
        )
        assertNull("Unauthenticated checkout must not open a hosted checkout URL", openedUrl.get())
        assertEquals("Failed checkout must not clear the cart", 1, CartStore.count)
    }

    @Test
    fun missingPackageIdBlocksCheckoutWithoutClearingCart() {
        val repo = FakeCartCheckoutRepository()

        setCartContent(
            repo = repo,
            lines = listOf(
                CartStore.CartLine(id = 4001, packageId = null, title = "Missing Package", priceUsd = 9.0),
            ),
            openCheckoutUrl = {},
        )

        waitForCart()
        compose.onNodeWithText("Make Payment").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Checkout unavailable").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Checkout unavailable").assertIsDisplayed()
        compose.onNodeWithText("One or more products are missing the package ID required for auction checkout.")
            .assertIsDisplayed()
        assertEquals("Missing package id must block the API call", 0, repo.checkoutCalls.get())
        assertEquals("Missing package id must not clear the cart", 1, CartStore.count)
    }

    private fun setCartContent(
        repo: FakeCartCheckoutRepository,
        mode: ThemeController.Mode = ThemeController.Mode.LIGHT,
        lines: List<CartStore.CartLine>,
        openCheckoutUrl: (String) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            CartStore.init(context)
            CartStore.clear()
            lines.forEach { CartStore.add(it) }
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    val viewModel = remember(repo) { CartViewModel(repo) }
                    CartScreen(
                        onBack = {},
                        onShopNow = {},
                        viewModel = viewModel,
                        openCheckoutUrl = openCheckoutUrl,
                    )
                }
            }
        }
    }

    private fun waitForCart() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Make Payment").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private class FakeCartCheckoutRepository(
        private val checkoutFailure: Throwable? = null,
    ) : ShopCheckoutRepository {
        val checkoutCalls = AtomicInteger()
        var lastPackageIds: List<Int>? = null
        var lastCurrency: String? = null
        var lastIsAuction: Boolean? = null

        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
        ): Result<String> {
            checkoutCalls.incrementAndGet()
            lastPackageIds = packageIds
            lastCurrency = currency
            lastIsAuction = isAuction
            return checkoutFailure?.let { Result.failure(it) } ?: Result.success(CheckoutUrl)
        }

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)

        override suspend fun billingProfile(): Result<ShopBillingProfile> =
            Result.success(ShopBillingProfile(firstName = "John"))
    }

    private companion object {
        const val CheckoutUrl = "https://checkout.airdropja.test/cart-session"
    }
}
