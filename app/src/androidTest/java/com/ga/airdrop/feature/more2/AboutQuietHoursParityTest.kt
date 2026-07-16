package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.TimePicker
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.push.QuietHoursStore
import com.ga.airdrop.core.session.clearLocalUserSession
import com.ga.airdrop.feature.more.QuietHoursViewModel
import java.io.FileNotFoundException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutQuietHoursParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetPreferences() {
        QuietHoursStore.clear(context)
        ThemeController.set(ThemeController.Mode.LIGHT)
    }

    @After
    fun cleanUp() {
        QuietHoursStore.clear(context)
        ThemeController.set(ThemeController.Mode.LIGHT)
    }

    @Test
    fun quietHoursRowUsesSwiftOrderGeometryAndIcon() {
        setAbout(ThemeController.Mode.LIGHT)

        val quiet = compose.onNodeWithTag("about-row-quiet-hours")
        val delivery = compose.onNodeWithTag("about-row-delivery")
        quiet.assertHeightIsEqualTo(56.dp)
        compose.onNodeWithTag("about-row-quiet-hours-icon", useUnmergedTree = true)
            .assertHeightIsEqualTo(24.dp)
        assertTrue(
            "Quiet hours must sit immediately before Default delivery method",
            quiet.getUnclippedBoundsInRoot().top < delivery.getUnclippedBoundsInRoot().top,
        )
        assertEquals(
            "Quiet hours and Default delivery method must use Swift's 16pt About row gap",
            16f,
            (delivery.getUnclippedBoundsInRoot().top - quiet.getUnclippedBoundsInRoot().bottom).value,
            0.5f,
        )
    }

    @Test
    fun aboutRowOpensSwiftSheetWithDefaultsAndBackDismisses() {
        setAbout(ThemeController.Mode.LIGHT)
        openSheet()

        compose.onNodeWithTag("quiet-hours-sheet").assertExists()
        compose.onNodeWithTag("quiet-hours-title").assertExists()
        compose.onNodeWithText("Enable quiet hours").assertExists()
        compose.onNodeWithText(
            "AirDrop notifications stay silent inside the window — they still land in " +
                "Notification Center, you just don't get the banner or sound. " +
                "Back-in-stock alerts you subscribed to are exempt.",
        ).assertExists()
        compose.onNodeWithText("10:00 PM").assertExists()
        compose.onNodeWithText("7:00 AM").assertExists()
        compose.onNodeWithTag("quiet-hours-from-control").assertIsNotEnabled()
        compose.onNodeWithTag("quiet-hours-until-control").assertIsNotEnabled()

        pressBack()
        waitForSheetDismissal()
    }

    @Test
    fun toggleAndClampedTimesPersistAcrossDismissAndReopen() {
        val viewModel = QuietHoursViewModel()
        setAbout(ThemeController.Mode.LIGHT, viewModel)
        openSheet()

        compose.onNodeWithTag("quiet-hours-enable-switch").performClick()
        compose.waitForIdle()
        assertTrue(QuietHoursStore.isEnabled(context))
        compose.onNodeWithTag("quiet-hours-from-control").assertHasClickAction()
        compose.onNodeWithTag("quiet-hours-until-control").assertHasClickAction()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.setStart(context, -10)
            viewModel.setEnd(context, 2_000)
        }
        compose.waitForIdle()
        compose.onNodeWithText("12:00 AM").assertExists()
        compose.onNodeWithText("11:59 PM").assertExists()
        assertEquals(0, QuietHoursStore.startMinutes(context))
        assertEquals(1_439, QuietHoursStore.endMinutes(context))

        pressBack()
        waitForSheetDismissal()
        openSheet()

        compose.onNodeWithText("12:00 AM").assertExists()
        compose.onNodeWithText("11:59 PM").assertExists()
        assertTrue(QuietHoursStore.isEnabled(context))
    }

    @Test
    fun enabledTimeControlOpensThePlatformPicker() {
        setAbout(ThemeController.Mode.LIGHT)
        openSheet()
        compose.onNodeWithTag("quiet-hours-enable-switch").performClick()
        compose.onNodeWithTag("quiet-hours-from-control").performClick()
        compose.waitForIdle()

        onView(isAssignableFrom(TimePicker::class.java))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        pressBack()
    }

    @Test
    fun sheetSupportsSwipeDismissal() {
        setAbout(ThemeController.Mode.LIGHT)
        openSheet()

        compose.onNodeWithTag("quiet-hours-sheet").performTouchInput { swipeDown() }
        waitForSheetDismissal()
    }

    @Test
    fun canonicalLogoutSweepClearsQuietHours() {
        QuietHoursStore.setEnabled(context, true)
        QuietHoursStore.setStartMinutes(context, 615)
        QuietHoursStore.setEndMinutes(context, 1_080)

        clearLocalUserSession(context)

        assertFalse(QuietHoursStore.isEnabled(context))
        assertEquals(QuietHoursStore.DEFAULT_START_MINUTES, QuietHoursStore.startMinutes(context))
        assertEquals(QuietHoursStore.DEFAULT_END_MINUTES, QuietHoursStore.endMinutes(context))
    }

    @Test
    fun quietHoursSheetLightScreenshot() {
        captureSheet(ThemeController.Mode.LIGHT, "issue106-quiet-hours-light.png")
    }

    @Test
    fun quietHoursSheetDarkScreenshot() {
        captureSheet(ThemeController.Mode.DARK, "issue106-quiet-hours-dark.png")
    }

    @Test
    fun quietHoursAboutRowLightScreenshot() {
        captureAboutRow(ThemeController.Mode.LIGHT, "issue106-about-row-light.png")
    }

    @Test
    fun quietHoursAboutRowDarkScreenshot() {
        captureAboutRow(ThemeController.Mode.DARK, "issue106-about-row-dark.png")
    }

    private fun setAbout(
        mode: ThemeController.Mode,
        quietHoursViewModel: QuietHoursViewModel = QuietHoursViewModel(),
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                AboutScreen(
                    onBack = {},
                    onNavigate = {},
                    quietHoursViewModel = quietHoursViewModel,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("about-row-quiet-hours").performScrollTo()
        compose.waitForIdle()
    }

    private fun openSheet() {
        compose.onNodeWithTag("about-row-quiet-hours").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("quiet-hours-sheet").assertExists()
    }

    private fun waitForSheetDismissal() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("quiet-hours-sheet")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun captureSheet(mode: ThemeController.Mode, filename: String) {
        QuietHoursStore.setEnabled(context, true)
        QuietHoursStore.setStartMinutes(context, 22 * 60)
        QuietHoursStore.setEndMinutes(context, 7 * 60)
        setAbout(mode)
        openSheet()

        val bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        saveScreenshot(bitmap, filename)
    }

    private fun captureAboutRow(mode: ThemeController.Mode, filename: String) {
        setAbout(mode)
        val bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        saveScreenshot(bitmap, filename)
    }

    private fun saveScreenshot(bitmap: Bitmap, filename: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/kotlin_ui_proof/quiet_hours_issue106",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        )
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } ?: throw FileNotFoundException(uri.toString())
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
