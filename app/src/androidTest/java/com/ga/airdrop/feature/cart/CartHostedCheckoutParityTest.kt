package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.repo.DeliveryRepository
import com.ga.airdrop.feature.delivery.DeliveryMethodViewModel
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
            SavedForLaterStore.clearAll()
        }
    }

    @Test
    fun makePaymentRoutesToDeliveryMethodWithoutCreatingCheckout() {
        // New contract (Kemar-cleared Delivery Method, PARITY_GAP_SPECS §4):
        // Make Payment routes to the Delivery Method screen; Stripe checkout
        // runs later from the currency popup. Cart stays intact.
        val repo = FakeCartCheckoutRepository()
        val navigated = AtomicReference<String?>()

        setCartContent(
            repo = repo,
            lines = listOf(
                CartStore.CartLine(id = 2002, packageId = 7002, title = "Beta Lamp", priceUsd = 7.0),
                CartStore.CartLine(id = 2001, packageId = 7001, title = "Alpha Radio", priceUsd = 5.0),
            ),
            openCheckoutUrl = {},
            onNavigate = { navigated.set(it) },
        )

        waitForCart()
        compose.onNodeWithText("Make Payment").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            navigated.get() == Routes.DELIVERY_METHOD
        }

        assertEquals("Make Payment must route to Delivery Method", Routes.DELIVERY_METHOD, navigated.get())
        assertEquals("No checkout may be created before the currency choice", 0, repo.checkoutCalls.get())
        assertEquals("Routing to Delivery Method must not touch the cart", 2, CartStore.count)
    }

    @Test
    fun currencyChoiceRunsSwiftCheckoutPayloadAndKeepsCart() {
        // The Swift payload contract (sorted package ids, chosen currency,
        // is_auction=true, cart kept until verified-paid return) now lives in
        // DeliveryMethodViewModel.onCurrencyChosen — drive it directly.
        val repo = FakeCartCheckoutRepository()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = AtomicReference<DeliveryMethodViewModel>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.init(context)
            CartStore.clear()
            CartStore.add(CartStore.CartLine(id = 2002, packageId = 7002, title = "Beta Lamp", priceUsd = 7.0))
            CartStore.add(CartStore.CartLine(id = 2001, packageId = 7001, title = "Alpha Radio", priceUsd = 5.0))
            vm.set(
                DeliveryMethodViewModel(
                    repo = DeliveryRepository(ApiClient.service),
                    checkout = repo,
                ),
            )
            vm.get().onCurrencyChosen("USD")
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            vm.get().state.value.checkoutUrl == CheckoutUrl
        }

        assertEquals("Swift checkout sends sorted package IDs", listOf(7001, 7002), repo.lastPackageIds)
        assertEquals("Chosen currency is forwarded", "USD", repo.lastCurrency)
        assertEquals("Swift checkout sends is_auction=true", true, repo.lastIsAuction)
        assertEquals("Cart clears only after verified-paid return, not on checkout create", 2, CartStore.count)
    }

    @Test
    fun unauthenticatedCurrencyCheckoutUsesSwiftSignInRequiredAlert() {
        // The Swift sign-in-required branch moved behind the currency popup —
        // assert it on DeliveryMethodViewModel (same detection + copy).
        val repo = FakeCartCheckoutRepository(
            checkoutFailure = IllegalStateException("Unauthenticated"),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = AtomicReference<DeliveryMethodViewModel>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.init(context)
            CartStore.clear()
            CartStore.add(CartStore.CartLine(id = 3001, packageId = 8001, title = "Swift Cart Line", priceUsd = 12.0))
            vm.set(
                DeliveryMethodViewModel(
                    repo = DeliveryRepository(ApiClient.service),
                    checkout = repo,
                ),
            )
            vm.get().onCurrencyChosen("USD")
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            vm.get().state.value.errorTitle == "Sign in required"
        }

        val state = vm.get().state.value
        assertEquals("Sign in required", state.errorTitle)
        assertEquals("Log in to your Airdropja account before checking out.", state.errorMessage)
        assertNull("Unauthenticated checkout must not produce a hosted checkout URL", state.checkoutUrl)
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
        lines: List<CartStore.CartLine>,
        openCheckoutUrl: (String) -> Unit,
        onNavigate: (String) -> Unit = {},
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
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
                        onNavigate = onNavigate,
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
