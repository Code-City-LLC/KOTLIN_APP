package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InviteFriendParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun contactsIconUsesSwiftDuotoneLight() {
        setInviteFriend(mode = ThemeController.Mode.LIGHT)

        assertContactsRowGeometry()
        assertIconContainsColor(ORANGE, "orange signal arcs in light mode")
        assertIconContainsColor(DARK_HANDSET, "dark handset in light mode")
        saveRootScreenshot("invite_friend_contacts_icon_light.png")
    }

    @Test
    fun contactsIconUsesSwiftDuotoneDark() {
        setInviteFriend(mode = ThemeController.Mode.DARK)

        assertContactsRowGeometry()
        assertIconContainsColor(ORANGE, "orange signal arcs in dark mode")
        assertIconContainsColor(WHITE_HANDSET, "white handset in app dark mode")
        saveRootScreenshot("invite_friend_contacts_icon_dark.png")
    }

    private fun setInviteFriend(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                InviteFriendScreen(onBack = {})
            }
        }
        compose.waitForIdle()
    }

    private fun assertContactsRowGeometry() {
        val row = compose.onNodeWithTag("invite-friend-contacts-row")
            .getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithTag(
            "invite-friend-contacts-icon",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(59f, boundsHeight(row), "Contacts row height")
        assertClose(24f, boundsWidth(icon), "Contacts icon width")
        assertClose(24f, boundsHeight(icon), "Contacts icon height")
    }

    private fun assertIconContainsColor(target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(
            "invite-friend-contacts-icon",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()

        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(blue - targetBlue) <= COLOR_TOLERANCE
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private companion object {
        const val ORANGE = 0xFFF15114.toInt()
        const val DARK_HANDSET = 0xFF292929.toInt()
        const val WHITE_HANDSET = 0xFFFFFFFF.toInt()
        const val COLOR_TOLERANCE = 18
    }
}
