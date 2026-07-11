package com.ga.airdrop.feature.homedetails

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldPriorityParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun goldPriorityForcesSwiftLightStatusBarIcons() {
        compose.activityRule.scenario.onActivity { activity ->
            statusController(activity).isAppearanceLightStatusBars = true
        }
        val showGoldPriority = mutableStateOf(true)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    if (showGoldPriority.value) {
                        GoldPriorityContent(onBack = {})
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse(statusController(compose.activity).isAppearanceLightStatusBars)
        }

        compose.runOnIdle {
            showGoldPriority.value = false
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(statusController(compose.activity).isAppearanceLightStatusBars)
        }
    }

    @Test
    fun platinumTierNameFitsSwiftTitleRowLight() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.LIGHT,
            initialPage = platinumIndex,
            widthDp = 360,
        )

        assertPlatinumTitleFitsSwiftRow()
        saveRootScreenshot("gold_priority_platinum_swift_light_360.png")
    }

    @Test
    fun platinumTierNameFitsSwiftTitleRowDark() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.DARK,
            initialPage = platinumIndex,
            widthDp = 360,
        )

        assertPlatinumTitleFitsSwiftRow()
        saveRootScreenshot("gold_priority_platinum_swift_dark_360.png")
    }

    private fun setGoldPriorityContent(
        mode: ThemeController.Mode,
        initialPage: Int = goldIndex,
        widthDp: Int,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(widthDp.dp)
                        .height(812.dp)
                ) {
                    GoldPriorityContent(
                        onBack = {},
                        initialPage = initialPage,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertPlatinumTitleFitsSwiftRow() {
        compose.onNodeWithText("Platinum Priority").assertIsDisplayed()
        val row = compose.onNodeWithTag("gold-priority-title-row").getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag("gold-priority-tier-badge", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val name = compose.onNodeWithTag("gold-priority-tier-name", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        // Swift TierPageCell: 70×70 PNG glyph, 15dp title-row spacing.
        assertClose(70f, boundsWidth(badge), "Swift tier glyph width")
        assertClose(70f, boundsHeight(badge), "Swift tier glyph height")
        assertClose(15f, boundsLeft(name) - boundsRight(badge), "Swift glyph/name gap")
        assertTrue(
            "Tier name should fit inside title row, nameRight=${boundsRight(name)} rowRight=${boundsRight(row)}",
            boundsRight(name) <= boundsRight(row) + 0.75f,
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag("gold-priority-root").captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/gold_priority_swift").also { it.mkdirs() }
    }

    private fun statusController(activity: ComponentActivity) =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    private fun boundsLeft(rect: DpRect): Float = rect.left.value

    private fun boundsRight(rect: DpRect): Float = rect.right.value

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun boundsHeight(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private val platinumIndex: Int
        get() = tierPages.indexOfFirst { it.id == "platinum" }

    private val goldIndex: Int
        get() = tierPages.indexOfFirst { it.id == "gold" }
}
