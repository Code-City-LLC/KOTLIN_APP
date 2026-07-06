package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
import com.ga.airdrop.data.model.CurrentUserResponse
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
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaqParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun faqInitialStateAndTypographyFollowSwiftLight() {
        setFaqContent(ThemeController.Mode.LIGHT)

        assertSwiftTypographyTokens()
        assertCollapsedInitialState()
        assertSwiftCardWidth()

        compose.onNodeWithTag("faq-1-header", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("faq-1-answer", useUnmergedTree = true).assertIsDisplayed()
        saveRootScreenshot("faq_swift_light_expanded.png")
    }

    @Test
    fun faqInitialStateAndTypographyFollowSwiftDark() {
        setFaqContent(ThemeController.Mode.DARK)

        assertSwiftTypographyTokens()
        assertCollapsedInitialState()
        assertSwiftCardWidth()

        compose.onNodeWithTag("faq-1-header", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("faq-1-answer", useUnmergedTree = true).assertIsDisplayed()
        saveRootScreenshot("faq_swift_dark_expanded.png")
    }

    private fun setFaqContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    FaqScreen(
                        onBack = {},
                        viewModel = FaqViewModel(More2Repository(FakeMore2Api())),
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(
                "How do I sign up for an AirDrop account?",
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSwiftTypographyTokens() {
        assertEquals("Swift FAQ question uses Title2/Bold", FontWeight.Bold, AirdropType.title2.fontWeight)
        assertEquals("Swift FAQ answer uses Body2/Regular", FontWeight.Normal, AirdropType.body2.fontWeight)
        assertEquals("Swift FAQ answer uses Body2 size", 14f, AirdropType.body2.fontSize.value, 0.01f)
        assertEquals("Swift FAQ answer uses Body2 line height", 22f, AirdropType.body2.lineHeight.value, 0.01f)
    }

    private fun assertCollapsedInitialState() {
        compose.onNodeWithTag("faq-1-card", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            "Swift FAQ initial state has no expanded answers",
            0,
            compose.onAllNodesWithTag("faq-1-answer", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    private fun assertSwiftCardWidth() {
        val bounds = compose.onNodeWithTag("faq-1-card", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals("Swift/Figma FAQ card width", 335f, (bounds.right - bounds.left).value, 0.75f)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/faq_swift",
        ).also { it.mkdirs() }

    private class FakeMore2Api : More2Api {
        override suspend fun faqs(): Paginated<FaqItem> =
            Paginated(
                items = listOf(
                    FaqItem(
                        id = "1",
                        question = "How do I sign up for an AirDrop account?",
                        answer = "Our Sign-Up process is fast, easy and free. Click on SignUp button to get " +
                            "started. We’ll send you your very own US shipping address. After signing up you " +
                            "can visit our location to formalize your account.",
                    ),
                    FaqItem(
                        id = "2",
                        question = "How much will I pay for the weight of my package?",
                        answer = "Airdrop charges you based on the weight of your package, please refer to our rate sheet.",
                    ),
                ),
            )

        override suspend fun authorizedUsers(): AuthorizedUsersEnvelope =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun authorizedUser(id: Int): AuthorizedUserEnvelope =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun addAuthorizedUser(body: AuthorizedUserRequest): AuthorizedUserEnvelope =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun updateAuthorizedUser(
            id: Int,
            body: AuthorizedUserRequest,
        ): AuthorizedUserEnvelope =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun deleteAuthorizedUser(id: Int): MutationResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun activateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun deactivateAuthorizedUser(id: Int, body: EmptyRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun referredFriends(limit: Int): Paginated<ReferredFriend> =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun referFriend(body: ReferFriendRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun profile(): CurrentUserResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun promotionalBanners(): Paginated<PromotionalBanner> =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun shippingRates(): DataEnvelope<ShippingRates> =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun termsContent(): ResponseBody =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun privacyContent(): ResponseBody =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun verifyLogin(body: LoginRequest): LoginResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")

        override suspend fun deactivateAccount(body: DeactivateAccountRequest): MutationResponse =
            throw AssertionError("Unused in FaqParityScreenshotTest")
    }
}
