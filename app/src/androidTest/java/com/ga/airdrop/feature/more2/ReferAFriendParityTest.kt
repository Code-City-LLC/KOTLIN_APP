package com.ga.airdrop.feature.more2

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.ReferredFriend
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferAFriendParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroIllustrationAssetsRemainAvailable() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        assertHeroAsset(resources, R.drawable.img_more2_refer_friends, 500, 500, "Friends")
        assertHeroAsset(resources, R.drawable.img_more2_refer_cash, 80, 80, "Cash")
        assertHeroAsset(resources, R.drawable.img_more2_refer_cap, 500, 500, "Cap")
    }

    @Test
    fun referLandingMatchesCurrentSwiftFigmaLight() {
        val inviteClicks = AtomicInteger()

        setReferContent(ThemeController.Mode.LIGHT) {
            inviteClicks.incrementAndGet()
        }

        compose.onNodeWithTag("refer-figma-screen").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-carousel").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-reward").assertIsDisplayed()
        compose.onNodeWithTag("refer-earn-title").assertIsDisplayed()
        compose.onNodeWithTag("refer-earn-body").assertIsDisplayed()
        compose.onNodeWithTag("refer-invite-button").assertIsDisplayed()
        compose.onNodeWithTag("refer-referral-link-card").assertIsDisplayed()
        compose.onNodeWithTag("refer-inline-referrals").assertIsDisplayed()

        assertTextExists("Refer. Reward. Repeat.")
        assertTextExists("Earn $2 USD Per Invite")
        assertTextExists(
            "Each friend who signs up and completes their first order adds $2 USD to your " +
                "account. Apply your rewards toward your next shipment — there’s no limit to how " +
                "much you can earn!",
        )
        assertAbsent("Earn AirCoins for every friend you invite")
        assertTextExists("Your Referral Link")
        assertTextExists("Your Referrals")
        assertTextExists("https://airdropja.com/refer/AD-2048")
        assertTextExists("Maya Lee")
        assertTextExists("maya@example.com")

        val card = compose.onNodeWithTag("refer-hero-card-reward").getUnclippedBoundsInRoot()
        val body = compose.onNodeWithTag("refer-hero-card-body-reward").getUnclippedBoundsInRoot()
        assertEquals("Figma carousel card width", 238f, boundsWidth(card), 1f)
        assertEquals("Figma carousel card height", 339f, boundsHeight(card), 1f)
        assertTrue(
            "Reward-card body text must fit inside the Figma card, not clip",
            body.bottom.value <= card.bottom.value - 42f,
        )

        compose.onNodeWithTag("refer-invite-button").performClick()
        compose.runOnIdle {
            assertEquals("Invite CTA opens Send Invitation", 1, inviteClicks.get())
        }
        saveRootScreenshot("refer_friend_figma_light.png")
    }

    @Test
    fun referLandingMatchesCurrentSwiftFigmaDark() {
        setReferContent(ThemeController.Mode.DARK)

        compose.onNodeWithTag("refer-figma-screen").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-reward").assertIsDisplayed()
        compose.onNodeWithTag("refer-referral-link-card").assertIsDisplayed()
        assertTextExists("Earn $2 USD Per Invite")
        assertTextExists("Your Referral Link")
        assertTextExists("Your Referrals")
        saveRootScreenshot("refer_friend_figma_dark.png")
    }

    @Test
    fun referralLinkCopyShowsSwiftFeedback() {
        val repository = FakeReferRepository()
        setReferContent(
            mode = ThemeController.Mode.LIGHT,
            repository = repository,
        )

        compose.onNodeWithTag("refer-referral-link-copy").performClick()

        compose.onNodeWithText("Link copied").assertIsDisplayed()
    }

    private fun setReferContent(
        mode: ThemeController.Mode,
        repository: FakeReferRepository = FakeReferRepository(),
        onInviteFriend: () -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            val refreshAfterInvite by remember { mutableStateOf(false) }
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = onInviteFriend,
                    refreshAfterInvite = refreshAfterInvite,
                    viewModel = ReferAFriendViewModel(repository),
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            !repository.loading.get()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("https://airdropja.com/refer/AD-2048").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertAbsent(text: String) {
        assertEquals(
            "Refer landing must not render stale content: $text",
            0,
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size,
        )
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected Refer landing text: $text",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private fun assertHeroAsset(
        resources: Resources,
        resId: Int,
        expectedWidth: Int,
        expectedHeight: Int,
        label: String,
    ) {
        val bitmap = BitmapFactory.decodeResource(resources, resId)
        assertEquals("$label asset width", expectedWidth, bitmap.width)
        assertEquals("$label asset height", expectedHeight, bitmap.height)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap: Bitmap = compose.onNodeWithTag("refer-figma-screen").captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private class FakeReferRepository : ReferAFriendRepository {
        val loading = AtomicBoolean(true)

        override suspend fun currentUser(): Result<AirdropUser> =
            Result.success(AirdropUser(accountNumber = "AD-2048"))

        override suspend fun referredFriends(limit: Int): Result<List<ReferredFriend>> {
            loading.set(false)
            return Result.success(
                listOf(
                    ReferredFriend(
                        id = 12,
                        friendFirstName = "Maya",
                        friendLastName = "Lee",
                        friendEmail = "maya@example.com",
                        referDate = "2024-12-15",
                        status = 1,
                        statusText = "Completed",
                    ),
                ),
            )
        }
    }
}
