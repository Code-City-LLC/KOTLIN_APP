package com.ga.airdrop.feature.homedetails

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AirdropNotification
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsStatusParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statusRowsRenderDistinctIconsAndReadyPickupNavigates() {
        var navigatedRoute: String? = null
        setNotificationsContent(ThemeController.Mode.LIGHT) { route ->
            navigatedRoute = route
        }

        compose.onNodeWithText("Package ready for pickup").assertIsDisplayed()
        compose.onNodeWithText("Package delivered").assertIsDisplayed()
        compose.onNodeWithText("Payment failed").assertIsDisplayed()

        val readyIcon = compose.onNodeWithTag(NotificationsTags.icon("ready"), useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
            .sampleHash()
        val deliveredIcon = compose.onNodeWithTag(NotificationsTags.icon("delivered"), useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
            .sampleHash()
        assertNotEquals("Ready-for-pickup and delivered rows must not share the same icon", readyIcon, deliveredIcon)

        compose.onNodeWithTag(NotificationsTags.row("ready"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(Routes.packageDetails("ADX-240524"), navigatedRoute)

        saveRootScreenshot("notifications_status_icons_light.png")
    }

    @Test
    fun statusRowsRenderInDarkThemeWithoutFallingOffScreen() {
        setNotificationsContent(ThemeController.Mode.DARK)

        compose.onNodeWithText("Package ready for pickup").assertIsDisplayed()
        compose.onNodeWithText("Package delivered").assertIsDisplayed()
        compose.onNodeWithText("Payment failed").assertIsDisplayed()
        compose.onNodeWithTag(NotificationsTags.icon("ready"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(NotificationsTags.icon("delivered"), useUnmergedTree = true).assertIsDisplayed()

        saveRootScreenshot("notifications_status_icons_dark.png")
    }

    private fun setNotificationsContent(
        mode: ThemeController.Mode,
        onNavigate: (String) -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                NotificationsScreenContent(
                    state = NotificationsUiState(items = fixtureNotifications, loadedOnce = true),
                    onBack = {},
                    onOpenSettings = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onNotificationTap = { notification ->
                        resolveNotificationRoute(notification)?.let(onNavigate)
                    },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(NotificationsTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveProofScreenshot(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/notifications_status")
            .also { it.mkdirs() }
    }

    @Suppress("InlinedApi")
    private fun saveProofScreenshot(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PROOF_SCREENSHOT_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create proof screenshot $filename" }

        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Unable to open proof screenshot $filename" }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun Bitmap.sampleHash(): Int {
        var hash = 17
        val stepX = (width / 12).coerceAtLeast(1)
        val stepY = (height / 12).coerceAtLeast(1)
        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                hash = 31 * hash + getPixel(x, y)
                y += stepY
            }
            x += stepX
        }
        return hash
    }

    private val fixtureNotifications = listOf(
        AirdropNotification(
            id = "ready",
            title = "Package ready for pickup",
            body = "Your package ADX-240524 is ready for pickup.",
            type = "package_ready_for_pickup",
            isRead = true,
            createdAt = "2026-07-07T10:30:00Z",
            referenceId = "ADX-240524",
            payload = mapOf("notification_type" to "package_ready_for_pickup"),
        ),
        AirdropNotification(
            id = "delivered",
            title = "Package delivered",
            body = "Proof of delivery is available.",
            type = "package_delivered",
            isRead = true,
            createdAt = "2026-07-07T09:00:00Z",
            referenceId = "PKG-2002",
        ),
        AirdropNotification(
            id = "payment",
            title = "Payment failed",
            body = "Please update your payment method.",
            type = "payment_failed",
            isRead = true,
            createdAt = "2026-07-06T16:45:00Z",
        ),
    )

    private companion object {
        const val PROOF_SCREENSHOT_DIR = "Pictures/AirdropProof/notifications_status"
    }
}
