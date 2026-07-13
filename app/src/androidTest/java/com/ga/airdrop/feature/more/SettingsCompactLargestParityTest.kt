package com.ga.airdrop.feature.more

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.TextSizeController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PR92 review (#23990): full-screen pinned-chrome proof at Largest on a
 * compact viewport. Renders the REAL SettingsScreen inside an exact
 * 360x500dp box, sets Largest, then scrolls ONLY the finite rows container
 * and pins that the header and the Logout bar do not move by a single
 * pixel while Account Deletion becomes reachable.
 *
 * Also exports the scrolled state as a clean capture
 * (pr92-settings-largest-compact-scrolled.png in device Pictures) — the
 * replacement for the artifact-laden adb screencap.
 */
@RunWith(AndroidJUnit4::class)
class SettingsCompactLargestParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun largestOnCompactScrollsRowsOnlyWithHeaderAndLogoutPinned() {
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .requiredSize(360.dp, 500.dp)
                        .testTag("compact-viewport"),
                ) {
                    SettingsScreen(onBack = {}, onNavigate = {}, onLoggedOut = {})
                }
            }
        }

        compose.runOnUiThread { TextSizeController.set(TextSizeController.Level.LARGEST) }
        compose.waitForIdle()

        val headerBefore = compose.onNodeWithText("Settings").getBoundsInRoot()
        val logoutBefore = compose.onNodeWithTag(SettingsTags.LOGOUT_BAR).getBoundsInRoot()
        val rowsBefore = compose.onNodeWithTag(SettingsTags.ROWS).getBoundsInRoot()

        compose.onNodeWithTag(SettingsTags.ROWS).performTouchInput { swipeUp() }
        compose.waitForIdle()

        val headerAfter = compose.onNodeWithText("Settings").getBoundsInRoot()
        val logoutAfter = compose.onNodeWithTag(SettingsTags.LOGOUT_BAR).getBoundsInRoot()
        val rowsAfter = compose.onNodeWithTag(SettingsTags.ROWS).getBoundsInRoot()

        assertEquals("header top must not move", headerBefore.top, headerAfter.top)
        assertEquals("header bottom must not move", headerBefore.bottom, headerAfter.bottom)
        assertEquals("logout top must not move", logoutBefore.top, logoutAfter.top)
        assertEquals("logout bottom must not move", logoutBefore.bottom, logoutAfter.bottom)
        assertEquals("rows viewport itself must not move", rowsBefore.top, rowsAfter.top)
        assertEquals("rows viewport bottom fixed", rowsBefore.bottom, rowsAfter.bottom)

        // The overflowing bottom row is now reachable inside the pinned frame.
        compose.onNodeWithText("Account Deletion").assertIsDisplayed()

        // Clean replacement capture of exactly this proven state.
        val bitmap = compose.onNodeWithTag("compact-viewport").captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val entry = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "pr92-settings-largest-compact-scrolled.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, entry)?.let { uri ->
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}
