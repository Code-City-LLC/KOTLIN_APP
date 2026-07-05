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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.DeactivateAccountRequest
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.FaqItem
import com.ga.airdrop.data.model.LoginRequest
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.data.model.ReferFriendRequest
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.data.model.ShippingRates
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromotionsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var backClicks = 0

    @Test
    fun activeBannersUseSwiftCardGeometryAndToggleLight() {
        val api = FakePromotionsApi(
            banners = listOf(
                sampleBanner(
                    id = 101,
                    active = true,
                    description = LONG_DESCRIPTION,
                ),
                sampleBanner(
                    id = 202,
                    active = false,
                    title = "Inactive promotion",
                    description = "This inactive banner should be filtered before rendering.",
                ),
            ),
        )

        setPromotions(api = api, mode = ThemeController.Mode.LIGHT)

        assertEquals("Promotions should make one backend request on entry", 1, api.promotionalCalls.get())
        compose.onNodeWithText("Promotions").assertIsDisplayed()
        compose.onNodeWithText("View Details").assertIsDisplayed()
        assertNoText("Inactive promotion")
        assertNoText("This inactive banner")

        assertSwiftCardGeometry()
        val collapsedHeight = boundsHeight(bounds("promotions-card-0-description"))
        assertTrue("Swift collapsed description should stay near three body lines", collapsedHeight in 48f..84f)
        saveRootScreenshot("promotions_swift_light_collapsed.png")

        compose.onNodeWithTag("promotions-card-0-toggle", useUnmergedTree = true).performClick()
        compose.onNodeWithText("View Less").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 2_000) {
            boundsHeight(bounds("promotions-card-0-description")) > collapsedHeight + 20f
        }
        waitForTouchFeedbackToSettle()
        saveRootScreenshot("promotions_swift_light_expanded.png")

        compose.onNodeWithTag("more2-inner-header-back", useUnmergedTree = true).performClick()
        assertEquals("Back should dispatch through the shared Swift More2 header rail", 1, backClicks)
    }

    @Test
    fun activeBannersUseSwiftCardGeometryDark() {
        val api = FakePromotionsApi(
            banners = listOf(
                sampleBanner(
                    id = 303,
                    active = true,
                    description = "Short active promotion for dark-theme chrome proof.",
                ),
            ),
        )

        setPromotions(api = api, mode = ThemeController.Mode.DARK)

        assertEquals("Promotions should make one backend request in dark mode", 1, api.promotionalCalls.get())
        compose.onNodeWithText("View Details").assertIsDisplayed()
        assertSwiftCardGeometry()
        saveRootScreenshot("promotions_swift_dark.png")
    }

    @Test
    fun inactiveOnlyBannersRenderSwiftEmptyState() {
        val api = FakePromotionsApi(
            banners = listOf(sampleBanner(id = 404, active = false)),
        )

        setPromotions(api = api, mode = ThemeController.Mode.LIGHT)

        assertEquals("Promotions should load once before rendering the empty state", 1, api.promotionalCalls.get())
        compose.onNodeWithText("No promotions available right now. Check back soon!").assertIsDisplayed()
        assertNoTag("promotions-card-0")
    }

    private fun setPromotions(api: FakePromotionsApi, mode: ThemeController.Mode) {
        backClicks = 0
        lateinit var viewModel: PromotionsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = PromotionsViewModel(More2Repository(api))
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    PromotionsScreen(
                        onBack = { backClicks += 1 },
                        viewModel = viewModel,
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            api.promotionalCalls.get() == 1 && !viewModel.state.value.loading
        }
        compose.waitForIdle()
    }

    private fun assertSwiftCardGeometry() {
        val root = bounds("promotions-root")
        val scroll = bounds("promotions-scroll")
        val card = bounds("promotions-card-0")
        val hero = bounds("promotions-card-0-hero")
        val description = bounds("promotions-card-0-description")
        val toggle = bounds("promotions-card-0-toggle")

        assertClose(375f, boundsWidth(root), "Promotions frame width")
        assertClose(20f, card.left.value, "Swift card left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(card), "Swift card width")
        assertClose(20f, card.top.value - scroll.top.value, "Swift first-card top inset")
        assertClose(160f, boundsHeight(hero), "Swift hero image height")
        assertClose(card.left.value, hero.left.value, "Hero should start at card leading edge")
        assertClose(boundsWidth(card), boundsWidth(hero), "Hero should fill card width")
        assertClose(card.left.value + 16f, description.left.value, "Swift description left inset")
        assertClose(card.left.value + 16f, toggle.left.value, "Swift toggle left inset")
        assertClose(24f, boundsHeight(toggle), "Swift toggle row height")
    }

    private fun waitForTouchFeedbackToSettle() {
        Thread.sleep(500)
        compose.waitForIdle()
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not exist in the rendered Promotions tree",
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertNoTag(tag: String) {
        assertTrue(
            "$tag should not exist in the rendered Promotions tree",
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun sampleBanner(
        id: Int,
        active: Boolean,
        title: String = "Promo $id",
        description: String = "Reference site about Lorem Ipsum, giving information on its origins.",
    ): PromotionalBanner =
        PromotionalBanner(
            id = id,
            title = title,
            description = description,
            active = active,
        )

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/promotions",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/promotions")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        outputStream.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private class FakePromotionsApi(
        private val banners: List<PromotionalBanner>,
    ) : More2Api {
        val promotionalCalls = AtomicInteger()

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> {
            promotionalCalls.incrementAndGet()
            return Paginated(items = banners)
        }

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun profile(): DataEnvelope<AirdropUser> =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun faqs(): Paginated<FaqItem> =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in PromotionsParityTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in PromotionsParityTest")
    }

    private companion object {
        const val LONG_DESCRIPTION =
            "Reference site about Lorem Ipsum, giving information on its origins, " +
                "as well as a random Lipsum generator. This extra Swift parity copy " +
                "keeps the label long enough to prove the collapsed three-line state " +
                "and the View Details expansion rail."
    }
}
