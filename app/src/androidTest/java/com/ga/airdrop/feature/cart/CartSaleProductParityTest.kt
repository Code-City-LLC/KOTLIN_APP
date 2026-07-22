package com.ga.airdrop.feature.cart

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
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
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.session.AuthenticatedRequestOwner
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.feature.shop.AffiliatePromotionProductsSource
import com.ga.airdrop.feature.shop.ShopBillingProfile
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import com.ga.airdrop.feature.shop.ShopProduct
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
    fun myCartUsesEligibleServerHeroExactDisclosureAndPreservesCartOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val heroProduct = eligibleHero()
        val preloadResult = runBlocking {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(R.drawable.img_cart_macbook_hero)
                    .memoryCacheKey(TEST_HERO_IMAGE_URL)
                    .build(),
            )
        }
        val preloadFailure = when (preloadResult) {
            is ErrorResult -> preloadResult.throwable.stackTraceToString()
            else -> "Unexpected Coil result: ${preloadResult::class.java.name}"
        }
        assertTrue(
            "Coil preload failed for controlled MacBook fixture\n$preloadFailure",
            preloadResult is SuccessResult,
        )
        assertEquals(
            "Controlled fixture must populate the exact URL key consumed by Cart",
            TEST_HERO_IMAGE_URL,
            (preloadResult as SuccessResult).memoryCacheKey?.key,
        )
        val line = CartStore.CartLine(
            id = 815,
            packageId = 815,
            title = "Cart hierarchy package",
            priceUsd = 20.0,
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )
        var openedAmazonUrl: String? = null

        renderCart(
            line = line,
            mode = ThemeController.Mode.LIGHT,
            featuredProducts = listOf(heroProduct),
            onOpenAmazon = { openedAmazonUrl = it },
        )

        compose.onNodeWithTag("cart-featured-apple-hero").assertIsDisplayed()
        compose.onNodeWithText("MacBook Pro").assertIsDisplayed()
        compose.onNodeWithText("$2,342.00").assertIsDisplayed()
        compose.onNodeWithText(
            "As an Amazon Associate, AirDrop earns from qualifying purchases.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Basket (1 Item)").assertIsDisplayed()
        compose.onNodeWithTag("cart-frosted-totals-footer").assertIsDisplayed()
        compose.onNodeWithText("Fax").assertIsDisplayed()
        compose.onNodeWithText("$ 5.00").assertIsDisplayed()
        compose.onNodeWithText("USD 1 = JMD 161.00").assertIsDisplayed()
        compose.onNodeWithText("Choose Delivery").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(
                "cart-featured-apple-image-loaded",
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag(
            "cart-featured-apple-image-loaded",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        saveScreenshot(context, "cart_dynamic_featured_apple_hero.png")

        val hero = compose.onNodeWithTag("cart-featured-apple-hero").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("cart-sale-line-815").getUnclippedBoundsInRoot()
        val note = compose.onNodeWithTag("cart-your-note-row").getUnclippedBoundsInRoot()
        assertEquals("Figma hero is exactly 172dp tall", 172f, (hero.bottom - hero.top).value, 0.75f)
        assertEquals("Figma Your Note row is exactly 59dp tall", 59f, (note.bottom - note.top).value, 0.75f)
        assertTrue("Hero must precede cart cards", hero.bottom <= card.top)
        assertTrue("Your Note must follow cart cards", card.bottom <= note.top)
        compose.onNodeWithTag("cart-featured-apple-hero").performClick()
        assertEquals("https://www.amazon.com/dp/mac?tag=partner", openedAmazonUrl)
        compose.onNodeWithTag("cart-your-note-row").performScrollTo().assertIsDisplayed()

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
    fun myCartCollapsesMarketingSlotWhenNoEligibleServerProductExists() {
        val line = CartStore.CartLine(
            id = 817,
            packageId = 817,
            title = "Cart item without an eligible hero",
            priceUsd = 20.0,
            kind = CartStore.CartLineKind.AUCTION,
            isAuction = true,
        )

        renderCart(
            line = line,
            mode = ThemeController.Mode.DARK,
            featuredProducts = listOf(
                eligibleHero().copy(
                    title = "MacBook protective case",
                    amazonUrl = "https://www.amazon.com/dp/case?tag=partner",
                ),
            ),
            expectHero = false,
        )

        compose.onNodeWithTag("cart-featured-apple-hero").assertDoesNotExist()
        compose.onNodeWithText("Basket (1 Item)").assertIsDisplayed()
        compose.onNodeWithTag("cart-sale-line-817").assertIsDisplayed()
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

        compose.onNodeWithTag("cart-sale-line-812").performScrollTo().assertIsDisplayed()
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
    fun saleProductRejectsUnsafeImageSchemeAsNeutralWellWithoutShipmentIdentity() {
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

    private fun renderCart(
        line: CartStore.CartLine,
        mode: ThemeController.Mode,
        widthDp: Int = 375,
        heightDp: Int = 812,
        fontScale: Float = 1f,
        featuredProducts: List<ShopProduct> = listOf(eligibleHero()),
        expectHero: Boolean = true,
        onOpenAmazon: ((String) -> Unit)? = {},
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val promotionSource = FakePromotionProductsSource(featuredProducts)

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
                            )
                        }
                        CartScreen(
                            onBack = {},
                            onShopNow = {},
                            viewModel = viewModel,
                            promotionProductsSource = promotionSource,
                            onOpenAmazon = onOpenAmazon,
                        )
                    }
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            val saleLineVisible = compose.onAllNodesWithTag("cart-sale-line-${line.id}")
                .fetchSemanticsNodes().isNotEmpty()
            val heroStateReached = promotionSource.featuredCalls > 0 &&
                if (expectHero) {
                    compose.onAllNodesWithTag("cart-featured-apple-hero")
                        .fetchSemanticsNodes().isNotEmpty()
                } else {
                    compose.onAllNodesWithTag("cart-featured-apple-hero")
                        .fetchSemanticsNodes().isEmpty()
                }
            saleLineVisible && heroStateReached
        }
    }

    private class FakePromotionProductsSource(
        private val featured: List<ShopProduct>,
    ) : AffiliatePromotionProductsSource {
        var featuredCalls = 0

        override suspend fun featuredProducts(): Result<List<ShopProduct>> {
            featuredCalls += 1
            return Result.success(featured)
        }

        override suspend fun saleProducts(): Result<List<ShopProduct>> =
            throw AssertionError("Cart must not load the sale promotions feed")
    }

    private fun eligibleHero() = ShopProduct(
        id = 901,
        title = "MacBook Pro",
        imageUrl = TEST_HERO_IMAGE_URL,
        priceUsd = 2_342.0,
        amazonUrl = TEST_AMAZON_AFFILIATE_URL,
    )

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
        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/cart_sale")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaValues,
        ) ?: return
        context.contentResolver.openOutputStream(mediaUri)?.use { mediaOutput ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, mediaOutput)
        }
        mediaValues.clear()
        mediaValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(mediaUri, mediaValues, null, null)
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

    private companion object {
        const val TEST_HERO_IMAGE_URL =
            "https://fixtures.invalid/airdrop/cart/macbook-pro-2342.png"
        const val TEST_AMAZON_AFFILIATE_URL =
            "https://www.amazon.com/dp/mac?tag=partner"
    }
}
