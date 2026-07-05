package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun preferencesSelectFieldsUseSwiftGeometryLight() {
        setPreferencesRows(mode = ThemeController.Mode.LIGHT)

        assertSwiftSelectFieldGeometry()
        assertNoRequiredAsterisksLikeSwift()
        saveRootScreenshot("preferences_select_field_swift_light.png")
    }

    @Test
    fun preferencesSelectFieldsUseSwiftGeometryDark() {
        setPreferencesRows(mode = ThemeController.Mode.DARK)

        assertSwiftSelectFieldGeometry()
        assertNoRequiredAsterisksLikeSwift()
        saveRootScreenshot("preferences_select_field_swift_dark.png")
    }

    @Test
    fun selectablePreferencesRowsRemainClickableLikeSwiftActions() {
        val pickupClicks = AtomicInteger()
        val currencyClicks = AtomicInteger()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
                ) {
                    MoreSelectField(
                        label = "Set Pickup Location",
                        value = "Montego Bay",
                        placeholder = "Select a pickup location",
                        onClick = { pickupClicks.incrementAndGet() },
                        testTagPrefix = "preferences-pickup",
                    )
                    MoreSelectField(
                        label = "Set Default Currency",
                        value = "USD",
                        placeholder = "Select a payment currency",
                        onClick = { currencyClicks.incrementAndGet() },
                        testTagPrefix = "preferences-currency",
                    )
                }
            }
        }

        compose.onNodeWithTag("preferences-pickup-card").performClick()
        compose.onNodeWithTag("preferences-currency-card").performClick()

        assertEquals(1, pickupClicks.get())
        assertEquals(1, currencyClicks.get())
    }

    private fun setPreferencesRows(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
                ) {
                    MoreSelectField(
                        label = "Email Address",
                        value = "Chasecash@focus.com",
                        placeholder = "your.email@example.com",
                        required = false,
                        enabled = false,
                        onClick = null,
                        trailingIconRes = null,
                        testTagPrefix = "preferences-email",
                    )
                    MoreSelectField(
                        label = "Set Pickup Location",
                        value = "Montego Bay",
                        placeholder = "Select a pickup location",
                        required = false,
                        onClick = {},
                        testTagPrefix = "preferences-pickup",
                    )
                    MoreSelectField(
                        label = "Set Default Currency",
                        value = "USD",
                        placeholder = "Select a payment currency",
                        required = false,
                        onClick = {},
                        testTagPrefix = "preferences-currency",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftSelectFieldGeometry() {
        val emailCard = compose.onNodeWithTag("preferences-email-card")
            .getUnclippedBoundsInRoot()
        val pickupCard = compose.onNodeWithTag("preferences-pickup-card")
            .getUnclippedBoundsInRoot()
        val currencyCard = compose.onNodeWithTag("preferences-currency-card")
            .getUnclippedBoundsInRoot()
        val pickupChevron = compose.onNodeWithTag(
            "preferences-pickup-chevron",
            useUnmergedTree = true,
        )
            .getUnclippedBoundsInRoot()
        val currencyChevron = compose.onNodeWithTag(
            "preferences-currency-chevron",
            useUnmergedTree = true,
        )
            .getUnclippedBoundsInRoot()

        assertClose(335f, boundsWidth(emailCard), "Email field width")
        assertClose(335f, boundsWidth(pickupCard), "Pickup field width")
        assertClose(335f, boundsWidth(currencyCard), "Currency field width")
        assertClose(52f, boundsHeight(emailCard), "Email field height")
        assertClose(52f, boundsHeight(pickupCard), "Pickup field height")
        assertClose(52f, boundsHeight(currencyCard), "Currency field height")
        assertClose(12f, boundsWidth(pickupChevron), "Pickup chevron width")
        assertClose(12f, boundsHeight(pickupChevron), "Pickup chevron height")
        assertClose(12f, boundsWidth(currencyChevron), "Currency chevron width")
        assertClose(12f, boundsHeight(currencyChevron), "Currency chevron height")
        assertEquals(
            "Disabled email row should not render a chevron",
            0,
            compose.onAllNodesWithTag(
                "preferences-email-chevron",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
    }

    private fun assertNoRequiredAsterisksLikeSwift() {
        assertEquals(0, compose.onAllNodesWithText("*").fetchSemanticsNodes().size)
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
