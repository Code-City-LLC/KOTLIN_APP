package com.ga.airdrop.feature.more

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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()
    private var backClicks = 0
    private var loggedOut = 0

    @Test
    fun rowsUseSwiftGeometryAndIconColorsLight() {
        setSettings(mode = ThemeController.Mode.LIGHT)

        assertSwiftRowGeometry()
        assertIconContainsColor("${SettingsTags.NOTIFICATIONS}-icon", DARK_ICON, "notifications icon is iconSelected in light mode")
        assertIconContainsColor("${SettingsTags.BACKGROUNDS}-icon", DARK_ICON, "background icon is iconSelected in light mode")
        assertIconContainsColor("${SettingsTags.MODE}-icon", DARK_ICON, "mode icon is iconSelected in light mode")
        assertIconDoesNotContainColor("${SettingsTags.NOTIFICATIONS}-icon", ORANGE, "Swift templates notifications icon, so Figma orange accent is not retained")
        assertIconDoesNotContainColor("${SettingsTags.BACKGROUNDS}-icon", ORANGE, "Swift templates background icon, so Figma orange accent is not retained")
        assertIconContainsColor("${SettingsTags.ACCOUNT_DELETION}-icon", ERROR_RED, "account deletion keeps the Swift destructive red icon")
        saveRootScreenshot("settings_swift_light.png")
    }

    @Test
    fun rowsUseSwiftGeometryAndRuntimeTintDark() {
        setSettings(mode = ThemeController.Mode.DARK)

        assertSwiftRowGeometry()
        assertIconContainsColor("${SettingsTags.NOTIFICATIONS}-icon", WHITE_ICON, "notifications icon follows app-dark iconSelected")
        assertIconContainsColor("${SettingsTags.BACKGROUNDS}-icon", WHITE_ICON, "background icon follows app-dark iconSelected")
        assertIconContainsColor("${SettingsTags.MODE}-icon", WHITE_ICON, "mode icon follows app-dark iconSelected")
        assertIconDoesNotContainColor("${SettingsTags.NOTIFICATIONS}-icon", ORANGE, "Swift templates notifications icon in dark mode, so Figma orange accent is not retained")
        assertIconDoesNotContainColor("${SettingsTags.BACKGROUNDS}-icon", ORANGE, "Swift templates background icon in dark mode, so Figma orange accent is not retained")
        assertIconDoesNotContainColor("${SettingsTags.MODE}-icon", ORANGE, "Swift templates mode icon in dark mode, so Figma orange accent is not retained")
        assertIconContainsColor("${SettingsTags.ACCOUNT_DELETION}-icon", ERROR_RED, "account deletion keeps the Swift destructive red icon")
        saveRootScreenshot("settings_swift_dark.png")
    }

    @Test
    fun rowsAndHeaderEmitSwiftRoutesAndActions() {
        setSettings(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, backClicks) }

        compose.onNodeWithTag("${SettingsTags.NOTIFICATIONS}-row", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(Routes.NOTIFICATION_SETTINGS, navigatedRoutes.lastOrNull()) }

        compose.onNodeWithTag("${SettingsTags.BACKGROUNDS}-row", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(Routes.BACKGROUNDS, navigatedRoutes.lastOrNull()) }

        compose.onNodeWithTag("${SettingsTags.ACCOUNT_DELETION}-row", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(Routes.ACCOUNT_DELETION, navigatedRoutes.lastOrNull()) }

        compose.onNodeWithTag("${SettingsTags.MODE}-row", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(ThemeController.Mode.DARK, ThemeController.mode) }

        compose.onNodeWithTag(SettingsTags.MODE_TOGGLE, useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(ThemeController.Mode.LIGHT, ThemeController.mode) }

        compose.runOnIdle { assertEquals(0, loggedOut) }
    }

    @Test
    fun clearCacheAndLogoutChromeMatchSwiftBehavior() {
        setSettings(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag(SettingsTags.CACHE, useUnmergedTree = true).performClick()
        compose.onNodeWithTag(SettingsTags.CACHE_SHEET, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Cache Cleared Successfully!").assertIsDisplayed()
        compose.onNodeWithText("You’ve successfully cleared your cache. Enjoy smoother performance and more storage space.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(SettingsTags.CACHE_SHEET, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        assertEquals(
            0,
            compose.onAllNodesWithTag(SettingsTags.CACHE_SHEET, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )

        compose.onNodeWithText("Logout").performClick()
        assertTrue(
            "Swift logout alert should show title and destructive action",
            compose.onAllNodesWithText("Log Out").fetchSemanticsNodes().size >= 2,
        )
        compose.onNodeWithText("Sign out of this AirDrop account?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Sign out of this AirDrop account?")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        assertEquals(
            0,
            compose.onAllNodesWithText("Sign out of this AirDrop account?")
                .fetchSemanticsNodes().size,
        )
    }

    private fun setSettings(mode: ThemeController.Mode) {
        navigatedRoutes.clear()
        backClicks = 0
        loggedOut = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    SettingsScreen(
                        onBack = { backClicks += 1 },
                        onNavigate = navigatedRoutes::add,
                        onLoggedOut = { loggedOut += 1 },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("${SettingsTags.NOTIFICATIONS}-row", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertSwiftRowGeometry() {
        val notifications = bounds("${SettingsTags.NOTIFICATIONS}-row")
        val backgrounds = bounds("${SettingsTags.BACKGROUNDS}-row")
        val mode = bounds("${SettingsTags.MODE}-row")
        val accountDeletion = bounds("${SettingsTags.ACCOUNT_DELETION}-row")
        val notificationIcon = bounds("${SettingsTags.NOTIFICATIONS}-icon")

        listOf(
            "Notification Settings" to notifications,
            "Background Images" to backgrounds,
            "Mode" to mode,
            "Account Deletion" to accountDeletion,
        ).forEach { (label, row) ->
            assertClose(335f, boundsWidth(row), "$label row width")
            assertClose(59f, boundsHeight(row), "$label row height")
        }

        assertClose(14f, verticalGap(notifications, backgrounds), "Swift stack spacing after Notification Settings")
        assertClose(14f, verticalGap(backgrounds, mode), "Swift stack spacing after Background Images")
        assertClose(36f, verticalGap(mode, accountDeletion), "Swift custom spacing after Mode")
        assertClose(20f, (notificationIcon.left - notifications.left).value, "Settings icon leading inset")
        assertClose(24f, boundsWidth(notificationIcon), "Settings icon width")
        assertClose(24f, boundsHeight(notificationIcon), "Settings icon height")
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(
        tag,
        useUnmergedTree = true,
    ).getUnclippedBoundsInRoot()

    private fun assertIconContainsColor(tag: String, target: Int, label: String) {
        val bitmap = iconBitmap(tag)
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertIconDoesNotContainColor(tag: String, target: Int, label: String) {
        val bitmap = iconBitmap(tag)
        assertFalse(label, bitmap.hasPixelNear(target))
    }

    private fun iconBitmap(tag: String): Bitmap =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

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
        return File(context.filesDir, "screenshots/settings").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private fun verticalGap(first: androidx.compose.ui.unit.DpRect, second: androidx.compose.ui.unit.DpRect): Float =
        (second.top - first.bottom).value

    private companion object {
        const val ORANGE = 0xFFF15114.toInt()
        const val DARK_ICON = 0xFF292929.toInt()
        const val WHITE_ICON = 0xFFFFFFFF.toInt()
        const val ERROR_RED = 0xFFD92A2A.toInt()
        const val COLOR_TOLERANCE = 22
    }
}
