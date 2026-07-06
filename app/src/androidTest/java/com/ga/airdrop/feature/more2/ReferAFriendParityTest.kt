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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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
    fun referPageUsesFigmaOnlyStructureLight() {
        val inviteClicks = AtomicInteger()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        compose.setContent {
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = { inviteClicks.incrementAndGet() },
                )
            }
        }
        compose.waitForIdle()

        assertFigmaOnlyStructure()
        val inviteCardBounds = compose.onNodeWithTag("refer-hero-card-invite")
            .getUnclippedBoundsInRoot()
        assertEquals("Figma hero cards are 238dp wide", 238f, boundsWidth(inviteCardBounds), 1f)
        assertEquals("Figma hero cards fill the 300dp carousel rail", 300f, boundsHeight(inviteCardBounds), 1f)

        compose.onNodeWithTag("refer-invite-button").performClick()
        compose.runOnIdle {
            assertEquals("Figma bottom Invite button should open Invite Friend flow", 1, inviteClicks.get())
        }
        saveRootScreenshot("refer_friend_figma_override_light.png")
    }

    @Test
    fun referPageUsesFigmaOnlyStructureDark() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.DARK)
        }

        compose.setContent {
            AirdropTheme {
                ReferAFriendScreen(onBack = {}, onInviteFriend = {})
            }
        }
        compose.waitForIdle()

        assertFigmaOnlyStructure()
        saveRootScreenshot("refer_friend_figma_override_dark.png")
    }

    @Test
    fun inviteCompletionFlagIsConsumedWithoutRestoringSwiftReferralList() {
        var consumed = 0

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        compose.setContent {
            var refreshAfterInvite by remember { mutableStateOf(true) }
            AirdropTheme {
                ReferAFriendScreen(
                    onBack = {},
                    onInviteFriend = {},
                    refreshAfterInvite = refreshAfterInvite,
                    onRefreshAfterInviteConsumed = {
                        consumed += 1
                        refreshAfterInvite = false
                    },
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) { consumed == 1 }
        compose.waitForIdle()

        assertFigmaOnlyStructure()
        assertEquals(
            "Resetting the Invite completion flag must not trigger a second consumption",
            1,
            consumed,
        )
    }

    private fun assertFigmaOnlyStructure() {
        compose.onNodeWithTag("refer-hero-carousel").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-invite").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithTag("refer-hero-card-reward").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("refer-hero-card-earn").fetchSemanticsNodes().size)
        compose.onNodeWithText("Earn $2 USD Per Invite").assertIsDisplayed()
        compose.onNodeWithTag("refer-invite-button").assertIsDisplayed()

        assertAbsent("Earn AirCoins for every friend you invite")
        assertAbsent("Your Referral Link")
        assertAbsent("Copy")
        assertAbsent("Your Referrals")
        assertAbsent("Invite Friends")
        assertAbsent("https://airdropja.com/refer", substring = true)
        assertAbsent("referred anyone", substring = true)
    }

    private fun assertAbsent(text: String, substring: Boolean = false) {
        assertEquals(
            "Figma-only Refer page must not render stale Swift content: $text",
            0,
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size,
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
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }
}
