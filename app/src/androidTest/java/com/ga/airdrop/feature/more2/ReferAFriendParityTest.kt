package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
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
    fun referLandingMatchesSwiftAndFigmaLight() {
        val inviteClicks = AtomicInteger()

        setReferContent(ThemeController.Mode.LIGHT) {
            inviteClicks.incrementAndGet()
        }

        compose.onNodeWithTag("refer-figma-screen").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-carousel").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-invite").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-reward").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-earn").assertIsDisplayed()
        compose.onNodeWithTag("refer-earn-title").assertIsDisplayed()
        compose.onNodeWithTag("refer-earn-body").assertIsDisplayed()
        compose.onNodeWithTag("refer-invite-button").assertIsDisplayed()

        assertTextExists("Invite your friends")
        assertTextExists("Refer. Reward. Repeat.")
        assertTextExists("Invite and Earn")
        assertTextExists("Earn $2 USD Per Invite")
        assertTextExists(
            "Each friend who signs up and completes their first order adds $2 USD to your " +
                "account. Apply your rewards toward your next shipment — there’s no limit to how " +
                "much you can earn!",
        )
        assertAbsent("Earn AirCoins for every friend you invite")
        assertAbsent("Your Referral Link")
        assertAbsent("Your Referrals")
        assertAbsent("Invite Friends")

        val card = compose.onNodeWithTag("refer-hero-card-reward").getUnclippedBoundsInRoot()
        val carousel = compose.onNodeWithTag("refer-hero-carousel").getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithTag("refer-hero-card-icon-reward").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("refer-hero-card-title-reward").getUnclippedBoundsInRoot()
        val body = compose.onNodeWithTag("refer-hero-card-body-reward").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("refer-invite-button").getUnclippedBoundsInRoot()
        assertEquals("Swift/Figma carousel viewport height", 339f, boundsHeight(carousel), 1f)
        assertEquals("Figma carousel card width", 238f, boundsWidth(card), 1f)
        assertEquals("Swift/Figma carousel card height", 326f, boundsHeight(card), 1f)
        assertEquals("Swift/Figma centered reward card x", 68.5f, card.left.value, 1f)
        assertEquals("Swift/Figma icon plate top", 60f, (icon.top - card.top).value, 1f)
        assertEquals("Swift/Figma icon plate width", 122f, boundsWidth(icon), 1f)
        assertEquals("Swift/Figma icon plate height", 122f, boundsHeight(icon), 1f)
        assertEquals("Swift/Figma icon-to-title gap", 20f, (title.top - icon.bottom).value, 1f)
        assertEquals("Swift/Figma Invite CTA height", 52f, boundsHeight(button), 1f)
        assertTrue(
            "Reward-card body text must fit inside the Figma card, not clip",
            body.bottom.value <= card.bottom.value,
        )

        compose.onNodeWithTag("refer-invite-button").performClick()
        compose.runOnIdle {
            assertEquals("Invite CTA opens Send Invitation", 1, inviteClicks.get())
        }
        saveRootScreenshot("refer_friend_figma_light.png")
    }

    @Test
    fun referLandingMatchesSwiftAndFigmaDark() {
        setReferContent(ThemeController.Mode.DARK)

        compose.onNodeWithTag("refer-figma-screen").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-invite").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-reward").assertIsDisplayed()
        compose.onNodeWithTag("refer-hero-card-earn").assertIsDisplayed()
        assertTextExists("Earn $2 USD Per Invite")
        assertAbsent("Your Referral Link")
        assertAbsent("Your Referrals")
        assertAbsent("Invite Friends")
        saveRootScreenshot("refer_friend_figma_dark.png")
    }

    private fun setReferContent(
        mode: ThemeController.Mode,
        onInviteFriend: () -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            val refreshAfterInvite by remember { mutableStateOf(false) }
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150)
                ) {
                    ReferAFriendScreen(
                        onBack = {},
                        onInviteFriend = onInviteFriend,
                        refreshAfterInvite = refreshAfterInvite,
                    )
                }
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
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/refer_friend/"
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(filename, "%kotlin_ui_proof/refer_friend%"),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
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
}
