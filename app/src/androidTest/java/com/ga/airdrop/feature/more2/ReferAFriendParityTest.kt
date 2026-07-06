package com.ga.airdrop.feature.more2

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.ReferredFriend
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
    fun referLandingMatchesLocalSwiftLight() {
        val inviteClicks = AtomicInteger()
        setReferContent(
            mode = ThemeController.Mode.LIGHT,
            repository = FakeReferRepository(
                accountNumber = "AD-2048",
                referrals = listOf(sampleFriend(id = 21)),
            ),
            onInviteFriend = { inviteClicks.incrementAndGet() },
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("https://airdropja.com/refer/AD-2048").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("refer-figma-screen").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-carousel").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-reward").assertIsDisplayed()
        assertTextExists("Earn AirCoins for every friend you invite")
        assertTextExists(
            "Each friend who signs up and completes their first order adds AirCoins to your " +
                "account. Apply your rewards toward your next shipment — there’s no limit to how " +
                "much you can earn!",
        )
        assertTextExists("Your Referral Link")
        assertTextExists("https://airdropja.com/refer/AD-2048")
        assertTextExists("Invite Friends")
        assertTextExists("Your Referrals")
        compose.onNodeWithTag("refer-referral-row-21").performScrollTo().assertIsDisplayed()
        assertTextExists("Maya Brown")
        assertTextExists("maya@example.com")
        assertTextExists("Completed")
        assertAbsent("Earn $2 USD Per Invite")
        assertAbsent("Your Referral Link moved")

        val card = compose.onNodeWithTag("refer-hero-card-reward").getUnclippedBoundsInRoot()
        assertEquals("Swift-local carousel card width", 238f, boundsWidth(card), 1f)
        assertEquals("Swift-local carousel card height", 220f, boundsHeight(card), 1f)

        compose.onNodeWithTag("refer-invite-button").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Invite Friends CTA opens Send Invitation", 1, inviteClicks.get())
        }

        compose.onNodeWithTag("refer-copy-link-button").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Link copied").fetchSemanticsNodes().isNotEmpty()
        }
        saveRootScreenshot("refer_friend_swift_local_light.png")
    }

    @Test
    fun referLandingMatchesLocalSwiftDarkEmptyState() {
        setReferContent(
            mode = ThemeController.Mode.DARK,
            repository = FakeReferRepository(accountNumber = "AD-2048", referrals = emptyList()),
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("You haven’t referred anyone yet. Tap Invite Friends above to share AirDrop.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("refer-figma-screen").assertIsDisplayed()
        compose.onNodeWithTag("refer-referral-link-card").assertIsDisplayed()
        compose.onNodeWithTag("refer-referrals-empty").performScrollTo().assertIsDisplayed()
        assertTextExists("Your Referrals")
        assertAbsent("Your Referral Link moved into Send Invitation")
        saveRootScreenshot("refer_friend_swift_local_dark.png")
    }

    private fun setReferContent(
        mode: ThemeController.Mode,
        repository: FakeReferRepository,
        onInviteFriend: () -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            val viewModel = remember { ReferAFriendViewModel(repository) }
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = onInviteFriend,
                    refreshAfterInvite = false,
                    viewModel = viewModel,
                )
            }
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

    private fun sampleFriend(id: Int) = ReferredFriend(
        id = id,
        friendFirstName = "Maya",
        friendLastName = "Brown",
        friendEmail = "maya@example.com",
        status = 1,
    )

    private class FakeReferRepository(
        private val accountNumber: String,
        private val referrals: List<ReferredFriend>,
    ) : ReferAFriendRepository {
        override suspend fun currentUser(): Result<AirdropUser> =
            Result.success(AirdropUser(accountNumber = accountNumber))

        override suspend fun referredFriends(limit: Int): Result<List<ReferredFriend>> =
            Result.success(referrals.take(limit))
    }
}
