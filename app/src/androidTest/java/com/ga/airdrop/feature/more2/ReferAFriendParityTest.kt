package com.ga.airdrop.feature.more2

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpRect
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
    fun referPageRestoresSwiftReferralLinkAndHistoryLight() {
        val repository = FakeReferAFriendRepository(
            friends = listOf(
                ReferredFriend(
                    id = 7,
                    friendFirstName = "Maya",
                    friendLastName = "Lee",
                    friendEmail = "maya@example.com",
                    status = 1,
                    statusText = "Completed",
                ),
            ),
        )
        val inviteClicks = AtomicInteger()

        setReferContent(
            repository = repository,
            mode = ThemeController.Mode.LIGHT,
            onInviteFriend = { inviteClicks.incrementAndGet() },
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.profileCalls.get() == 1 && repository.referredFriendsCalls.get() == 1
        }
        compose.waitForIdle()
        assertSwiftInitialLoads(repository)

        compose.onNodeWithTag("refer-hero-carousel").assertIsDisplayed()
        val heroBounds = compose.onNodeWithTag("refer-hero-carousel").getUnclippedBoundsInRoot()
        val firstCardBounds = compose.onNodeWithTag("refer-hero-card-invite").getUnclippedBoundsInRoot()
        assertEquals("Swift hero carousel height", 220f, boundsHeight(heroBounds), 1f)
        assertEquals("Swift hero card width", 238f, boundsWidth(firstCardBounds), 1f)
        assertTextExists("Earn AirCoins for every friend you invite")
        assertTextExists(
            "Each friend who signs up and completes their first order adds AirCoins " +
                "to your account. Apply your rewards toward your next shipment — " +
                "there’s no limit to how much you can earn!",
        )
        assertAbsent("Earn $2 USD Per Invite")

        scrollTo("refer-referral-link-card")
        compose.onNodeWithText("Your Referral Link").assertIsDisplayed()
        compose.onNodeWithText("https://airdropja.com/refer/AD-2048").assertIsDisplayed()
        compose.onNodeWithTag("refer-copy-button").performClick()
        compose.onNodeWithTag("refer-copy-toast").assertIsDisplayed()

        scrollTo("refer-invite-friends-button")
        compose.onNodeWithTag("refer-invite-friends-button").performClick()
        compose.runOnIdle {
            assertEquals("Swift Invite Friends CTA should open Invite Friend flow", 1, inviteClicks.get())
        }

        scrollTo("refer-referral-row-7")
        compose.onNodeWithText("Your Referrals").assertIsDisplayed()
        compose.onNodeWithText("Maya Lee").assertIsDisplayed()
        compose.onNodeWithText("maya@example.com").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()

        saveRootScreenshot("refer_friend_swift_light.png")
    }

    @Test
    fun referPageShowsSwiftEmptyStateDark() {
        val repository = FakeReferAFriendRepository(friends = emptyList())

        setReferContent(repository = repository, mode = ThemeController.Mode.DARK)

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.profileCalls.get() == 1 && repository.referredFriendsCalls.get() == 1
        }
        compose.waitForIdle()
        assertSwiftInitialLoads(repository)

        assertTextExists("Earn AirCoins for every friend you invite")
        scrollTo("refer-referrals-empty")
        compose.onNodeWithText("Your Referrals").assertIsDisplayed()
        compose.onNodeWithTag("refer-referrals-empty").assertIsDisplayed()
        compose.onNodeWithText(
            "You haven’t referred anyone yet. Tap Invite Friends above to share AirDrop.",
        ).assertIsDisplayed()
        assertEquals(
            "Stale bottom-only Figma CTA tag must stay absent",
            0,
            compose.onAllNodesWithTag("refer-invite-button").fetchSemanticsNodes().size,
        )
        saveRootScreenshot("refer_friend_swift_dark_empty.png")
    }

    @Test
    fun inviteCompletionFlagReloadsReferredFriendsLikeSwiftViewWillAppear() {
        val repository = FakeReferAFriendRepository(friends = emptyList())
        var triggerRefresh: (() -> Unit)? = null
        var consumed = 0

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            var refreshAfterInvite by remember { mutableStateOf(false) }
            triggerRefresh = { refreshAfterInvite = true }
            val viewModel = remember { ReferAFriendViewModel(repository) }
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = {},
                    refreshAfterInvite = refreshAfterInvite,
                    onRefreshAfterInviteConsumed = {
                        consumed += 1
                        refreshAfterInvite = false
                    },
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.profileCalls.get() == 1 && repository.referredFriendsCalls.get() == 1
        }
        assertSwiftInitialLoads(repository)
        compose.runOnIdle {
            repository.friends = listOf(
                ReferredFriend(
                    id = 11,
                    friendName = "Referred friend",
                    friendEmail = "new@example.com",
                    status = 0,
                ),
            )
            triggerRefresh?.invoke()
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.referredFriendsCalls.get() == 2 && consumed == 1
        }
        compose.waitForIdle()
        assertEquals(
            "Swift invite completion reloads referred friends without refetching the referral link",
            1,
            repository.profileCalls.get(),
        )
        assertEquals(
            "Swift invite completion should issue exactly one additional referred-friends call",
            2,
            repository.referredFriendsCalls.get(),
        )

        scrollTo("refer-referral-row-11")
        compose.onNodeWithText("Referred friend").assertIsDisplayed()
        compose.onNodeWithText("Pending").assertIsDisplayed()
    }

    private fun setReferContent(
        repository: FakeReferAFriendRepository,
        mode: ThemeController.Mode,
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
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("refer-scroll-content").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private fun assertAbsent(text: String, tag: String? = null) {
        assertEquals(
            "Refer page must not render stale Figma-only content: $text",
            0,
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size,
        )
        if (tag != null) {
            assertEquals(0, compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
        }
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected Refer page text: $text",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assertSwiftInitialLoads(repository: FakeReferAFriendRepository) {
        assertEquals(
            "Swift loadReferralData should fetch profile exactly once on first render",
            1,
            repository.profileCalls.get(),
        )
        assertEquals(
            "Swift viewWillAppear should fetch referred friends exactly once on first render",
            1,
            repository.referredFriendsCalls.get(),
        )
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

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
        val bitmap: Bitmap = compose.onNodeWithTag("refer-swift-screen").captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private class FakeReferAFriendRepository(
        @Volatile var friends: List<ReferredFriend>,
    ) : ReferAFriendRepository {
        val profileCalls = AtomicInteger()
        val referredFriendsCalls = AtomicInteger()

        override suspend fun referredFriends(limit: Int): Result<List<ReferredFriend>> {
            referredFriendsCalls.incrementAndGet()
            return Result.success(friends)
        }

        override suspend fun currentUser(): Result<AirdropUser> {
            profileCalls.incrementAndGet()
            return Result.success(AirdropUser(accountNumber = "AD-2048"))
        }
    }
}
