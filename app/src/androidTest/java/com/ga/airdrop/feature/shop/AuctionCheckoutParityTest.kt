package com.ga.airdrop.feature.shop

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
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

    @Test
    fun sharedCheckoutAndCartFieldsUseSwiftMakeFieldTokens() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray150),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TypeInputField(
                        label = "First Name",
                        value = "John",
                        onValueChange = {},
                        required = true,
                        testTagPrefix = "swift-type-input",
                    )
                    ShopDropdownField(
                        label = "Payment Currency",
                        value = "USD",
                        options = listOf("USD", "JMD"),
                        onSelect = {},
                        required = true,
                        testTagPrefix = "swift-dropdown",
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("swift-type-input-required", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("swift-dropdown-required", useUnmergedTree = true).assertIsDisplayed()
        assertClose(
            expected = 48f,
            actual = boundsHeight("swift-type-input-card"),
            label = "Swift TypeInputField card height",
        )
        assertClose(
            expected = 48f,
            actual = boundsHeight("swift-dropdown-card"),
            label = "Swift dropdown card height",
        )
        assertClose(
            expected = 20f,
            actual = boundsHeight("swift-dropdown-chevron"),
            label = "Swift dropdown chevron height",
        )
        assertNodeContainsColor("swift-type-input-card", 0xFFFFFFFF.toInt(), "TypeInputField gray100 card")
        assertNodeContainsColor("swift-dropdown-card", 0xFFFFFFFF.toInt(), "Dropdown gray100 card")
        assertNodeContainsColor(
            "swift-type-input-required",
            0xFFF15114.toInt(),
            "TypeInputField orange required star",
        )
        assertNodeContainsColor(
            "swift-dropdown-required",
            0xFFF15114.toInt(),
            "Dropdown orange required star",
        )
        assertNodeContainsColor(
            "swift-dropdown-chevron",
            0xFF9E9E9E.toInt(),
            "Dropdown gray500 chevron",
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

    private fun boundsHeight(tag: String): Float =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { (it.bottom - it.top).value }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun assertNodeContainsColor(tag: String, target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24
                if (alpha < 24) continue
                val dr = kotlin.math.abs(AndroidColor.red(pixel) - AndroidColor.red(target))
                val dg = kotlin.math.abs(AndroidColor.green(pixel) - AndroidColor.green(target))
                val db = kotlin.math.abs(AndroidColor.blue(pixel) - AndroidColor.blue(target))
                if (dr <= 20 && dg <= 20 && db <= 20) return
            }
        }
        error("$label should contain #${Integer.toHexString(target)}")
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
