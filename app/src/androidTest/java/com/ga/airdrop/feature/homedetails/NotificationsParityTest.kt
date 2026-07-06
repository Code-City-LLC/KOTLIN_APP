package com.ga.airdrop.feature.homedetails

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.more.NotificationSettingsViewModel
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStateFollowsSwiftFigmaAndSettingsActionsLight() {
        val routes = mutableListOf<String>()
        setNotificationsContent(
            mode = ThemeController.Mode.LIGHT,
            notificationsOn = false,
            routes = routes,
        )

        assertOffCopy()
        assertNoBackendInboxSurface()
        assertSwiftGeometry()

        compose.onNodeWithTag("notifications-settings-link", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("notifications-settings-button", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(Routes.NOTIFICATION_SETTINGS, Routes.NOTIFICATION_SETTINGS),
                routes,
            )
        }
        saveRootScreenshot("notifications_swift_empty_light.png")
    }

    @Test
    fun enabledMasterUsesSwiftAllSetCopyDark() {
        val routes = mutableListOf<String>()
        setNotificationsContent(
            mode = ThemeController.Mode.DARK,
            notificationsOn = true,
            routes = routes,
        )

        compose.onNodeWithText("You’re all set!").assertIsDisplayed()
        compose.onNodeWithText("We’ll notify you about important activity.").assertIsDisplayed()
        assertTextAbsent(OffBody)
        assertTextAbsent(OffLink)
        assertNoBackendInboxSurface()
        assertSwiftGeometry()

        compose.onNodeWithTag("notifications-settings-button", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(listOf(Routes.NOTIFICATION_SETTINGS), routes)
        }
        saveRootScreenshot("notifications_swift_empty_dark_enabled.png")
    }

    private fun setNotificationsContent(
        mode: ThemeController.Mode,
        notificationsOn: Boolean,
        routes: MutableList<String>,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        seedNotificationPrefs(context, notificationsOn)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    NotificationsScreen(
                        onBack = {},
                        onNavigate = { routes.add(it) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun seedNotificationPrefs(context: Context, notificationsOn: Boolean) {
        context.getSharedPreferences(NotificationSettingsViewModel.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("isNotifications", notificationsOn)
            .apply()
    }

    private fun assertOffCopy() {
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("You’re all caught up.").assertIsDisplayed()
        compose.onNodeWithText(OffBody).assertIsDisplayed()
        compose.onNodeWithText(OffLink).assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun assertNoBackendInboxSurface() {
        compose.onNodeWithTag("notifications-empty-root").assertIsDisplayed()
        assertTextAbsent("Unable to load notifications")
        assertTextAbsent("Try Again")
        assertTextAbsent("Package tracking alert")
        assertTextAbsent("No notifications yet")
    }

    private fun assertTextAbsent(text: String) {
        assertEquals(0, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
    }

    private fun assertSwiftGeometry() {
        val root = bounds("notifications-screen")
        val card = bounds("notifications-empty-card")
        val hero = bounds("notifications-empty-hero")
        val footer = bounds("notifications-footer")
        val button = bounds("notifications-settings-button")

        assertClose(375f, width(root), "Reference viewport width")
        assertClose(20f, card.left.value, "Swift card leading inset")
        assertClose(335f, width(card), "Swift card width")
        assertClose(261.3f, width(hero), "Swift hero width, card * 0.78")
        assertClose(238.3f, height(hero), "Swift hero 340:310 box height")
        assertClose(0f, footer.left.value, "Footer spans screen left")
        assertClose(375f, width(footer), "Footer spans screen width")
        assertClose(root.bottom.value, footer.bottom.value, "Footer pinned to screen bottom")
        assertClose(20f, button.left.value, "Swift Settings button leading inset")
        assertClose(335f, width(button), "Swift Settings button width")
        assertClose(52f, height(button), "Swift Settings button height")
        assertTrue("Footer should contain the Settings button", button.top.value >= footer.top.value)
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
        return File(context.getExternalFilesDir(null), "screenshots/notifications_swift")
            .also { it.mkdirs() }
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun width(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun height(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private companion object {
        private const val OffBody =
            "Turn on notifications to get real-time updates on package tracking, " +
                "status changes, pricing updates, and special offers."
        private const val OffLink =
            "Not sure if it’s enabled? Check your notification settings."
    }
}
