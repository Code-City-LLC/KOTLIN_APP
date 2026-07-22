package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.external.AffiliateAndMediaLinks
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.feature.shop.ShopProduct
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromotionsParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bannerCardKeepsSwiftGeometryToggleAndBackOnCompactLight() {
        val feeds = FakePromotionsFeeds(
            banners = queue(
                Result.success(
                    listOf(
                        PromotionalBanner(
                            id = 101,
                            title = "Summer shipping",
                            description = LONG_DESCRIPTION,
                            active = true,
                        ),
                    ),
                ),
            ),
            featured = queue(Result.success(emptyList())),
            sales = queue(Result.success(emptyList())),
        )
        var backClicks = 0
        val viewModel = render(
            feeds = feeds,
            mode = ThemeController.Mode.LIGHT,
            widthDp = 320,
            heightDp = 568,
            onBack = { backClicks += 1 },
        )

        waitForLoad(viewModel, feeds, expectedCalls = 1)
        compose.onNodeWithText("More Promotions").assertIsDisplayed()
        compose.onNodeWithTag("promotions-card-0-toggle", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("View Less").assertIsDisplayed()

        val card = bounds("promotions-card-0")
        val hero = bounds("promotions-card-0-hero")
        assertClose(20f, card.left.value, "card left gutter")
        assertClose(280f, boundsWidth(card), "compact card width")
        assertClose(160f, boundsHeight(hero), "preserved Swift hero height")

        compose.onNodeWithTag("more2-inner-header-back", useUnmergedTree = true).performClick()
        assertEquals(1, backClicks)
        saveRootScreenshot("promotions_compact_light_banner.png")
    }

    @Test
    fun partialProductFeedsRenderAmazonAndSaleOnLargeDark() {
        val feeds = FakePromotionsFeeds(
            banners = queue(Result.failure(IllegalStateException("banner feed unavailable"))),
            featured = queue(
                Result.success(
                    listOf(
                        product(1, "Apple Watch"),
                        product(2, "MacBook Pro"),
                    ),
                ),
            ),
            sales = queue(
                Result.success(
                    listOf(product(3, "AirDrop Sale Laptop", amazonUrl = null)),
                ),
            ),
        )
        var amazonOpened: String? = null
        var saleOpened: Int? = null
        val viewModel = render(
            feeds = feeds,
            mode = ThemeController.Mode.DARK,
            widthDp = 430,
            heightDp = 932,
            onOpenAmazon = { amazonOpened = it },
            onOpenSale = { saleOpened = it.id },
        )

        waitForLoad(viewModel, feeds, expectedCalls = 1)
        compose.onNodeWithText("Apple Finds on Amazon").assertIsDisplayed()
        compose.onNodeWithText("MacBook Pro").assertIsDisplayed()
        compose.onNodeWithText(AffiliateAndMediaLinks.AMAZON_ASSOCIATE_DISCLOSURE)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("promotions-amazon-0").performClick()
        assertEquals("https://www.amazon.com/dp/2?tag=partner", amazonOpened)

        compose.onNodeWithText("AirDrop Sale Highlights").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("promotions-sale-0").performScrollTo().performClick()
        assertEquals(3, saleOpened)
        assertNoTag("promotions-banner-section")
        saveRootScreenshot("promotions_large_dark_partial_feeds.png")
    }

    @Test
    fun allFeedErrorRetryRecoversWithoutLeavingErrorState() {
        val feeds = FakePromotionsFeeds(
            banners = queue(
                Result.failure(IllegalStateException("first")),
                Result.success(listOf(PromotionalBanner(id = 44, title = "Recovered"))),
            ),
            featured = queue(
                Result.failure(IllegalStateException("first")),
                Result.success(emptyList()),
            ),
            sales = queue(
                Result.failure(IllegalStateException("first")),
                Result.success(emptyList()),
            ),
        )
        val viewModel = render(feeds, ThemeController.Mode.LIGHT)

        waitForLoad(viewModel, feeds, expectedCalls = 1)
        compose.onNodeWithText(PROMOTIONS_ALL_FEEDS_ERROR).assertIsDisplayed()
        compose.onNodeWithTag("promotions-retry").performClick()
        waitForLoad(viewModel, feeds, expectedCalls = 2)

        compose.onNodeWithText("More Promotions").assertIsDisplayed()
        assertNoText(PROMOTIONS_ALL_FEEDS_ERROR)
        assertEquals(2, feeds.bannerCalls)
        assertEquals(2, feeds.featuredCalls)
        assertEquals(2, feeds.saleCalls)
    }

    @Test
    fun oneSuccessfulEmptyFeedRendersEmptyState() {
        val feeds = FakePromotionsFeeds(
                banners = queue(Result.success(emptyList())),
                featured = queue(Result.failure(IllegalStateException("featured"))),
                sales = queue(Result.failure(IllegalStateException("sales"))),
            )
        val viewModel = render(
            feeds = feeds,
            mode = ThemeController.Mode.DARK,
        )

        waitForLoad(viewModel, feeds, expectedCalls = 1)
        compose.onNodeWithTag("promotions-empty").assertIsDisplayed()
        compose.onNodeWithText("No promotions available right now. Check back soon!")
            .assertIsDisplayed()
    }

    private fun render(
        feeds: FakePromotionsFeeds,
        mode: ThemeController.Mode,
        widthDp: Int = 375,
        heightDp: Int = 812,
        onBack: () -> Unit = {},
        onOpenAmazon: ((String) -> Unit)? = null,
        onOpenSale: ((ShopProduct) -> Unit)? = null,
    ): PromotionsViewModel {
        lateinit var viewModel: PromotionsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = PromotionsViewModel(feeds)
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(widthDp.dp)
                        .height(heightDp.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    PromotionsScreen(
                        onBack = onBack,
                        viewModel = viewModel,
                        onOpenAmazon = onOpenAmazon,
                        onOpenSale = onOpenSale,
                    )
                }
            }
        }
        return viewModel
    }

    private fun waitForLoad(
        viewModel: PromotionsViewModel,
        feeds: FakePromotionsFeeds,
        expectedCalls: Int,
    ) {
        compose.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.loading &&
                viewModel.state.value.hasLoaded &&
                feeds.bannerCalls == expectedCalls &&
                feeds.featuredCalls == expectedCalls &&
                feeds.saleCalls == expectedCalls
        }
        compose.waitForIdle()
    }

    private fun product(
        id: Int,
        title: String,
        amazonUrl: String? = "https://www.amazon.com/dp/$id?tag=partner",
    ) = ShopProduct(
        id = id,
        title = title,
        imageUrl = "https://cdn.test/$id.png",
        priceUsd = 999.0,
        amazonUrl = amazonUrl,
    )

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun boundsWidth(value: DpRect): Float = (value.right - value.left).value
    private fun boundsHeight(value: DpRect): Float = (value.bottom - value.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not exist",
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertNoTag(tag: String) {
        assertTrue(
            "$tag should not exist",
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/promotions")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/promotions",
        ).also(File::mkdirs)

    private class FakePromotionsFeeds(
        private val banners: ArrayDeque<Result<List<PromotionalBanner>>>,
        private val featured: ArrayDeque<Result<List<ShopProduct>>>,
        private val sales: ArrayDeque<Result<List<ShopProduct>>>,
    ) : PromotionsFeeds {
        var bannerCalls = 0
        var featuredCalls = 0
        var saleCalls = 0

        override suspend fun banners(): Result<List<PromotionalBanner>> {
            bannerCalls += 1
            return banners.removeFirst()
        }

        override suspend fun featuredProducts(): Result<List<ShopProduct>> {
            featuredCalls += 1
            return featured.removeFirst()
        }

        override suspend fun saleProducts(): Result<List<ShopProduct>> {
            saleCalls += 1
            return sales.removeFirst()
        }
    }

    private fun <T> queue(vararg values: T): ArrayDeque<T> = ArrayDeque(values.toList())

    private companion object {
        const val LONG_DESCRIPTION =
            "Reference site about Lorem Ipsum, giving information on its origins, " +
                "as well as a random Lipsum generator. This extra Swift parity copy " +
                "keeps the label long enough to prove the collapsed three-line state " +
                "and the View Details expansion rail."
    }
}
