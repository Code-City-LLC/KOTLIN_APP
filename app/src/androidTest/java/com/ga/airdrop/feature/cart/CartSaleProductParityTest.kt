package com.ga.airdrop.feature.cart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.ShopProductsRepository
import com.ga.airdrop.feature.shop.eligibleAppleAmazonProducts
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CartSaleProductParityTest {

    private val owner = AuthenticatedSessionOwner("cart-sale-parity", 815)

    @get:Rule
    val compose = createComposeRule()

    @After
    fun cleanCart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CartStore.clear()
            SavedForLaterStore.clearAll()
            CartNoteStore.save(owner, "")
        }
    }

    @Test
    fun myCartUsesExactHeroOrderCompactNoteAndFrostedFooter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val amazonUrl = "https://www.amazon.com/dp/M4?tag=airdrop00-20"
        var openedAmazon: String? = null
        val line = CartStore.CartLine(
            id = 815,
            packageId = 815,
            title = "Cart hierarchy package",
            priceUsd = 20.0,
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )

        renderCart(
            line = line,
            mode = ThemeController.Mode.LIGHT,
            featuredProducts = listOf(
                ShopProduct(
                    id = 901,
                    title = "MacBook Pro M4",
                    imageUrl = "android.resource://${context.packageName}/${R.drawable.img_home_hero}",
                    priceUsd = 1599.0,
                    amazonUrl = amazonUrl,
                ),
            ),
            onOpenAmazon = { openedAmazon = it },
        )

        compose.onNodeWithTag("cart-apple-hero").assertIsDisplayed()
        compose.onNodeWithTag("cart-apple-hero-image", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("MacBook Pro M4").assertIsDisplayed()
        compose.onNodeWithTag("cart-apple-disclosure").assertIsDisplayed()
        compose.onNodeWithText("Basket (1 Item)").assertIsDisplayed()
        compose.onNodeWithTag("cart-your-note-row").assertIsDisplayed()
        compose.onNodeWithTag("cart-frosted-totals-footer").assertIsDisplayed()
        compose.onNodeWithText("Fax").assertIsDisplayed()
        compose.onNodeWithText("$ 5.00").assertIsDisplayed()
        compose.onNodeWithText("USD 1 = JMD 161.00").assertIsDisplayed()
        compose.onNodeWithText("Choose Delivery").assertIsDisplayed()

        val hero = compose.onNodeWithTag("cart-apple-hero").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("cart-sale-line-815").getUnclippedBoundsInRoot()
        val note = compose.onNodeWithTag("cart-your-note-row").getUnclippedBoundsInRoot()
        assertEquals("Figma hero is exactly 172dp tall", 172f, (hero.bottom - hero.top).value, 0.75f)
        assertEquals("Figma Your Note row is exactly 59dp tall", 59f, (note.bottom - note.top).value, 0.75f)
        assertTrue("Hero must precede cart cards", hero.bottom <= card.top)
        assertTrue("Your Note must follow cart cards", card.bottom <= note.top)

        compose.onNodeWithTag("cart-apple-hero").performClick()
        assertEquals(amazonUrl, openedAmazon)

        compose.onNodeWithTag("cart-your-note-row").performClick()
        compose.onNodeWithTag("cart-note-popup").assertIsDisplayed()
        // The approved contour is intentionally much larger than the clipped
        // dialog surface, so its semantics bounds are not "displayed" even
        // though the intersecting pixels render. Existence plus the byte and
        // dimension assertions below lock the actual visual asset.
        compose.onNodeWithTag("cart-note-popup-pattern", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("cart-note-input").performTextInput("Leave at reception")
        compose.onNodeWithTag("cart-note-save").performClick()
        compose.onNodeWithText("Leave at reception").assertIsDisplayed()

        val patternBytes = context.resources.openRawResource(R.drawable.img_cart_note_popup_pattern)
            .use { it.readBytes() }
        val patternDigest = MessageDigest.getInstance("SHA-256")
            .digest(patternBytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            "Popup contour must stay identical to the approved PDF raster",
            "e48e7b3b8e741ef0a4182678751ddf9c58a2f2e9b56d37d758e42e3dfbcb5e8d",
            patternDigest,
        )
        BitmapFactory.decodeByteArray(patternBytes, 0, patternBytes.size).let { bitmap ->
            assertEquals(1633, bitmap.width)
            assertEquals(1848, bitmap.height)
        }
    }

    @Test
    fun narrowLargestTextKeepsFooterVisibleAndScrollableTailReachable() {
        val line = CartStore.CartLine(
            id = 816,
            packageId = 816,
            title = "Long accessible product title that must remain reachable",
            priceUsd = 20.0,
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )

        renderCart(
            line = line,
            mode = ThemeController.Mode.LIGHT,
            widthDp = 320,
            heightDp = 568,
            fontScale = 2f,
        )

        compose.onNodeWithTag("cart-frosted-totals-footer").assertIsDisplayed()
        compose.onNodeWithText("Choose Delivery").assertIsDisplayed()
        compose.waitForIdle()
        // onSizeChanged publishes the measured 2x-font footer inset through a
        // second composition. Scroll only after that state has settled.
        compose.onNodeWithTag("cart-your-note-row").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("cart-your-note-row").performScrollTo().assertIsDisplayed()
        val viewport = compose.onNodeWithTag("cart-scroll-viewport").getUnclippedBoundsInRoot()
        val footer = compose.onNodeWithTag("cart-frosted-totals-footer").getUnclippedBoundsInRoot()
        assertTrue("Scroll viewport must stop above the fixed footer", viewport.bottom <= footer.top)
    }

    @Test
    fun saleProductUsesSwiftCardGeometryWithoutATypeBadgeOrIcon() {
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

        renderCart(line, ThemeController.Mode.DARK)

        compose.onNodeWithTag("cart-sale-title-812", useUnmergedTree = true)
            .assertTextEquals("Swift Sale Product")
            .assertIsDisplayed()
        compose.onNodeWithTag("cart-sale-price-812", useUnmergedTree = true)
            .assertTextEquals("USD 30.00")
            .assertIsDisplayed()
        compose.onNodeWithText("Auction item", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Sale item", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cart-auction-type-812", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cart-sale-image-fallback-812", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Product image unavailable",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "Auction image unavailable",
            useUnmergedTree = true,
        ).assertDoesNotExist()
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

        saveScreenshot(context, "sale_dark_product_fallback_card.png")
    }

    @Test
    fun saleProductUsesLightImageTileAndDescriptionWithoutAuctionIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val line = CartStore.CartLine(
            id = 814,
            packageId = 814,
            title = "Light Sale Product",
            priceUsd = 18.0,
            qty = 1,
            imageUrl = null,
            isAuction = true,
        )

        renderCart(line, ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Auction item", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Sale item", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cart-auction-type-814", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cart-sale-image-fallback-814", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Product image unavailable",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "Auction image unavailable",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNodeWithTag("cart-sale-title-814", useUnmergedTree = true)
            .assertTextEquals("Light Sale Product")
            .assertIsDisplayed()

        saveScreenshot(context, "sale_light_product_fallback_card.png")
    }

    @Test
    fun saleProductRejectsNonHttpsImageAsNeutralWellWithoutShipmentIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val line = CartStore.CartLine(
            id = 813,
            packageId = 813,
            title = "Sale Hero Product",
            priceUsd = 24.5,
            qty = 1,
            imageUrl = "android.resource://${context.packageName}/${R.drawable.img_home_hero}",
            isAuction = true,
        )

        renderCart(line, ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("cart-sale-image-fallback-813", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Sale Hero Product",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNodeWithText("Auction item", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Sale item", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cart-auction-type-813", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cart-sale-title-813", useUnmergedTree = true)
            .assertTextEquals("Sale Hero Product")
            .assertIsDisplayed()
        compose.onNodeWithTag("cart-sale-price-813", useUnmergedTree = true)
            .assertTextEquals("USD 24.50")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Auction image unavailable",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "Product image unavailable",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNodeWithTag("cart-sale-image-loaded-813", useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithText("Drop Number", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("AIR0000000813", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Standard", useUnmergedTree = true).assertDoesNotExist()

        saveScreenshot(context, "sale_non_https_neutral_image_well.png")
    }

    @Test
    fun cartCollapsesAppleHeroWhenFeaturedFeedHasNoEligibleTaggedAppleProduct() {
        val line = CartStore.CartLine(
            id = 817,
            packageId = 817,
            title = "Cart package without Apple promotion",
            priceUsd = 20.0,
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )

        renderCart(
            line = line,
            mode = ThemeController.Mode.LIGHT,
            featuredProducts = listOf(
                ShopProduct(
                    id = 902,
                    title = "Generic television",
                    imageUrl = "https://images.example.test/tv.jpg",
                    priceUsd = 499.0,
                    amazonUrl = "https://www.amazon.com/dp/TV?tag=airdrop00-20",
                ),
            ),
        )

        compose.onNodeWithTag("cart-apple-hero").assertDoesNotExist()
        compose.onNodeWithTag("cart-apple-disclosure").assertDoesNotExist()
        compose.onNodeWithText("Basket (1 Item)").assertIsDisplayed()
    }

    private fun renderCart(
        line: CartStore.CartLine,
        mode: ThemeController.Mode,
        widthDp: Int = 375,
        heightDp: Int = 812,
        fontScale: Float = 1f,
        featuredProducts: List<ShopProduct>? = null,
        onOpenAmazon: (String) -> Unit = {},
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolvedFeaturedProducts = featuredProducts ?: listOf(
            ShopProduct(
                id = 900,
                title = "MacBook Air",
                imageUrl = "android.resource://${context.packageName}/${R.drawable.img_home_hero}",
                priceUsd = 999.0,
                amazonUrl = "https://www.amazon.com/dp/AIR?tag=airdrop00-20",
            ),
        )
        val products = FakeProductsRepository(resolvedFeaturedProducts)

        instrumentation.runOnMainSync {
            ThemeController.set(mode)
            CartStore.init(context)
            SavedForLaterStore.init(context)
            CheckoutFlowStore.init(context)
            CartStore.onAuthenticatedSessionChanged(owner)
            SavedForLaterStore.onAuthenticatedSessionChanged(owner)
            CartStore.clear()
            SavedForLaterStore.clearAll()
            check(CartNoteStore.save(owner, ""))
            check(CartStore.add(line))
        }

        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale),
            ) {
                AirdropThemeProvider {
                    Box(
                        Modifier
                            .width(widthDp.dp)
                            .height(heightDp.dp)
                            .background(AirdropTheme.colors.gray100)
                            .testTag("cart-sale-proof-root")
                    ) {
                        val viewModel = remember {
                            CartViewModel(
                                checkout = FakeCheckoutRepository(),
                                sessionBoundary = FakeSessionBoundary(owner),
                                products = products,
                            )
                        }
                        CartScreen(
                            onBack = {},
                            onShopNow = {},
                            viewModel = viewModel,
                            onOpenAmazon = onOpenAmazon,
                        )
                    }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            val lineVisible = compose.onAllNodesWithTag("cart-sale-line-${line.id}")
                .fetchSemanticsNodes().isNotEmpty()
            val heroSettled = products.featuredCalls.get() == 1 &&
                (eligibleAppleAmazonProducts(resolvedFeaturedProducts).isEmpty() ||
                    compose.onAllNodesWithTag("cart-apple-hero").fetchSemanticsNodes().isNotEmpty())
            lineVisible && heroSettled
        }
    }

    private fun saveScreenshot(context: Context, fileName: String) {
        val screenshotDir = File(context.getExternalFilesDir(null), "screenshots/cart_sale")
            .also { it.mkdirs() }
        val output = File(screenshotDir, fileName)
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
            userNote: String?,
            expectedSession: AuthTokenStore.RequestProvenance,
        ): Result<CheckoutResponse> = Result.failure(IllegalStateException("Not used by visual proof"))

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)

        override suspend fun billingProfile(): Result<ShopBillingProfile> =
            Result.success(ShopBillingProfile(firstName = "Swift"))
    }

    private class FakeProductsRepository(
        private val featured: List<ShopProduct>,
    ) : ShopProductsRepository {
        val featuredCalls = AtomicInteger()

        override suspend fun auctionProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> = throw AssertionError("Unused in CartSaleProductParityTest")

        override suspend fun featuredProducts(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<List<ShopProduct>> {
            featuredCalls.incrementAndGet()
            return Result.success(featured)
        }

        override suspend fun productBySlug(
            slug: String,
            featured: Boolean,
        ): Result<ShopProduct> = throw AssertionError("Unused in CartSaleProductParityTest")
    }

    /**
     * The note rail is account-owned. Keep these visual tests authenticated,
     * while returning no request provenance so CartViewModel never hydrates
     * from the network during a rendering assertion.
     */
    private class FakeSessionBoundary(initial: AuthenticatedSessionOwner) : AuthenticatedSessionBoundary {
        private val current = MutableStateFlow<AuthenticatedSessionOwner?>(initial)
        override val changes = current

        override fun capture(): AuthenticatedSessionOwner? = current.value
        override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean = current.value == owner
        override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
            if (!isCurrent(owner)) return false
            action()
            return true
        }

        override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean =
            isCurrent(owner) && action()

        override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? = null

        override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
            isCurrent(owner) && owner.accountId == accountId
    }
}
