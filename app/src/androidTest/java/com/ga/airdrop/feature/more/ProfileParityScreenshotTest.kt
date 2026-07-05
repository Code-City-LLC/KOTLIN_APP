package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profileAvatarUsesSwiftGeometryLight() {
        setProfileAvatar(mode = ThemeController.Mode.LIGHT)

        assertSwiftAvatarGeometry()
        saveRootScreenshot("profile_avatar_swift_geometry_light.png")
    }

    @Test
    fun profileAvatarUsesSwiftGeometryDark() {
        setProfileAvatar(mode = ThemeController.Mode.DARK)

        assertSwiftAvatarGeometry()
        saveRootScreenshot("profile_avatar_swift_geometry_dark.png")
    }

    @Test
    fun dobSelectorRejectsFutureDatesLikeSwiftMaximumDate() {
        val utc = TimeZone.getTimeZone("UTC")
        val today = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JULY, 5)
        }.timeInMillis
        val yesterday = today - 24L * 60L * 60L * 1000L
        val tomorrow = today + 24L * 60L * 60L * 1000L

        assertTrue(isSelectableDobDate(yesterday, today))
        assertTrue(isSelectableDobDate(today, today))
        assertFalse(isSelectableDobDate(tomorrow, today))
        assertTrue(isSelectableDobYear(2026, today))
        assertFalse(isSelectableDobYear(2027, today))
    }

    private fun setProfileAvatar(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(Modifier.size(88.dp)) {
                    ProfileAvatar(avatar = null, loading = false, onClick = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftAvatarGeometry() {
        val wrap = compose.onNodeWithTag("profile-avatar-wrap").getUnclippedBoundsInRoot()
        val circle = compose.onNodeWithTag("profile-avatar-circle").getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag("profile-avatar-edit-badge").getUnclippedBoundsInRoot()
        val placeholder = compose.onNodeWithTag(
            "profile-avatar-placeholder",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(88f, boundsWidth(wrap), "Avatar wrap width")
        assertClose(88f, boundsHeight(wrap), "Avatar wrap height")
        assertClose(80f, boundsWidth(circle), "Avatar circle width")
        assertClose(80f, boundsHeight(circle), "Avatar circle height")
        assertClose(44f, boundsWidth(placeholder), "Avatar placeholder width")
        assertClose(44f, boundsHeight(placeholder), "Avatar placeholder height")
        assertClose(24f, boundsWidth(badge), "Edit badge width")
        assertClose(24f, boundsHeight(badge), "Edit badge height")
        assertClose(2f, (badge.right - circle.right).value, "Badge trailing overshoot")
        assertClose(2f, (badge.bottom - circle.bottom).value, "Badge bottom overshoot")
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
}
