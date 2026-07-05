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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuctionCheckoutParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nullImageUsesSwiftGiftHeroPlaceholder() {
        setCheckoutContent(product = SampleProduct.copy(imageUrl = null))

        waitForCheckout()

        compose.onNodeWithTag("auction-checkout-hero-placeholder").assertIsDisplayed()
        compose.onNodeWithText(GiftPlaceholder).assertIsDisplayed()
        saveRootScreenshot("auction_checkout_gift_placeholder_swift_light.png")
    }

    @Test
    fun failedImageLoadRestoresSwiftGiftHeroPlaceholder() {
        setCheckoutContent(
            product = SampleProduct.copy(imageUrl = "file:///android_asset/missing_checkout_image.png"),
        )

        waitForCheckout()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("auction-checkout-hero-placeholder").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("auction-checkout-hero-placeholder").assertIsDisplayed()
    }

    @Test
    fun unauthenticatedCheckoutUsesSwiftSignInRequiredAlert() {
        setCheckoutContent(
            checkoutFailure = IllegalStateException("Unauthenticated"),
        )

        waitForCheckout()
        compose.onNodeWithTag("auction-checkout-continue").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Sign in required").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Sign in required").assertIsDisplayed()
        compose.onNodeWithText("Log in to your Airdropja account before checking out.").assertIsDisplayed()
        assertTrue(
            "Swift unauthenticated checkout should not surface the generic failure title",
            compose.onAllNodesWithText("Checkout failed").fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun setCheckoutContent(
        product: ShopProduct = SampleProduct,
        checkoutFailure: Throwable? = null,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            ShopCheckoutStore.product = product
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    val repo = remember(checkoutFailure) {
                        FakeShopCheckoutRepository(checkoutFailure = checkoutFailure)
                    }
                    val viewModel = remember(product, checkoutFailure) {
                        AuctionCheckoutViewModel(checkout = repo)
                    }
                    AuctionCheckoutScreen(
                        onBack = {},
                        onCheckoutOpened = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    private fun waitForCheckout() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Continue to pay").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class FakeShopCheckoutRepository(
        private val checkoutFailure: Throwable?,
    ) : ShopCheckoutRepository {
        override suspend fun createCheckout(
            packageIds: List<Int>,
            currency: String,
            isAuction: Boolean,
        ): Result<String> = checkoutFailure?.let { Result.failure(it) }
            ?: Result.success("https://checkout.airdropja.test/session")

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)

        override suspend fun billingProfile(): Result<ShopBillingProfile> =
            Result.success(ShopBillingProfile())
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
            "screenshots/auction_checkout_parity",
        )
        dir.mkdirs()
        return dir
    }

    private companion object {
        const val GiftPlaceholder = "\uD83C\uDF81"

        val SampleProduct = ShopProduct(
            id = 7101,
            slug = "swift-checkout-parity-gift",
            title = "Swift Checkout Parity Gift",
            imageUrl = null,
            priceUsd = 42.0,
            packageId = 9101,
        )
    }
}
