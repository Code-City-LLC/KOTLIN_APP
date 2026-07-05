package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShipmentsSectionCardParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sectionCardHeaderDividerMatchesSwiftLight() {
        setSectionCardContent(ThemeController.Mode.LIGHT)

        assertSwiftDividerGeometry()
        saveRootScreenshot("shipments_section_card_swift_light.png")
    }

    @Test
    fun sectionCardHeaderDividerMatchesSwiftDark() {
        setSectionCardContent(ThemeController.Mode.DARK)

        assertSwiftDividerGeometry()
        saveRootScreenshot("shipments_section_card_swift_dark.png")
    }

    private fun setSectionCardContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(260.dp)
                        .background(AirdropTheme.colors.gray150)
                        .padding(20.dp)
                ) {
                    ShipmentsSectionCard(
                        title = "Order Summary",
                        headerDividerTestTag = "section-card-header-divider",
                    ) {
                        ShipmentsListRow(
                            label = "Order Description",
                            value = "Studio Monitor",
                            showDivider = false,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftDividerGeometry() {
        compose.onNodeWithText("Order Summary").assertIsDisplayed()
        compose.onNodeWithText("Studio Monitor").assertIsDisplayed()
        val divider = compose.onNodeWithTag("section-card-header-divider")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertClose(335f, boundsWidth(divider), "Swift section divider full card width")
        assertClose(1f, boundsHeight(divider), "Swift section divider 1dp height", tolerance = 0.2f)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/shipments_section_cards",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
