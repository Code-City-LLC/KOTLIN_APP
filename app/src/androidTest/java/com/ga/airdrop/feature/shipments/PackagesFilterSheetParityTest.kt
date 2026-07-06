package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackagesFilterSheetParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun packagesFilterUsesSwiftFigmaGeometryAndCallbacksLight() {
        val recorder = setFilterSheetContent(ThemeController.Mode.LIGHT)

        assertSwiftFigmaGeometry()
        saveSheetScreenshot("packages_filter_swift_light.png")
        assertCallbacks(recorder)
    }

    @Test
    fun packagesFilterUsesSwiftFigmaGeometryDark() {
        setFilterSheetContent(ThemeController.Mode.DARK)

        assertSwiftFigmaGeometry()
        saveSheetScreenshot("packages_filter_swift_dark.png")
    }

    private fun setFilterSheetContent(mode: ThemeController.Mode): Recorder {
        val recorder = Recorder()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                PackagesFilterSheet(
                    statuses = sampleStatuses,
                    selectedStatus = 7,
                    selectedMethod = ShipmentTypeFilter.Standard,
                    onSelectStatus = { recorder.statusClicks += it },
                    onSelectMethod = { recorder.methodClicks += it },
                    onClose = { recorder.closeClicks += 1 },
                )
            }
        }
        compose.waitForIdle()
        return recorder
    }

    private fun assertSwiftFigmaGeometry() {
        compose.onNodeWithText("Sorting by").assertIsDisplayed()
        compose.onNodeWithText("Shipment Method").assertIsDisplayed()
        compose.onNodeWithText("Status of Shipment").assertIsDisplayed()
        compose.onNodeWithText("AirDrop").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("All Packages").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("AirDrop Standard").fetchSemanticsNodes().size)

        val root = compose.onNodeWithTag("packages-filter-root").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("packages-filter-header").getUnclippedBoundsInRoot()
        val headerRow = compose.onNodeWithTag("packages-filter-header-row").getUnclippedBoundsInRoot()
        val methodCard = compose.onNodeWithTag("packages-filter-method-card").getUnclippedBoundsInRoot()
        val statusCard = compose.onNodeWithTag("packages-filter-status-card").getUnclippedBoundsInRoot()
        val methodSection =
            compose.onNodeWithTag("packages-filter-section-Shipment Method").getUnclippedBoundsInRoot()
        val statusSection =
            compose.onNodeWithTag("packages-filter-section-Status of Shipment").getUnclippedBoundsInRoot()
        val standardRow = compose.onNodeWithTag("packages-filter-method-row-standard").getUnclippedBoundsInRoot()
        val expressRow = compose.onNodeWithTag("packages-filter-method-row-express").getUnclippedBoundsInRoot()
        val readyRow = compose.onNodeWithTag("packages-filter-status-row-7").getUnclippedBoundsInRoot()
        val paidRow = compose.onNodeWithTag("packages-filter-status-row-18").getUnclippedBoundsInRoot()
        val standardIcon =
            compose.onNodeWithTag("packages-filter-method-icon-standard", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val readyIcon = compose.onNodeWithTag("packages-filter-status-icon-7", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertClose(56f, boundsHeight(headerRow), "Swift header row height")
        assertClose(16f, boundsTop(methodCard) - boundsBottom(header), "Swift content gap below header")
        assertClose(40f, boundsWidth(root) - boundsWidth(methodCard), "Swift 20dp horizontal page inset")
        assertClose(boundsWidth(methodCard), boundsWidth(statusCard), "Filter cards share width")
        assertClose(16f, boundsTop(statusCard) - boundsBottom(methodCard), "Card stack gap")

        assertClose(54f, boundsHeight(methodSection), "Swift collapsible method bar")
        assertClose(54f, boundsHeight(statusSection), "Swift collapsible status bar")
        listOf(standardRow, expressRow, readyRow, paidRow).forEachIndexed { index, row ->
            assertClose(50f, boundsHeight(row), "Swift option row $index height")
        }
        listOf(standardIcon, readyIcon).forEachIndexed { index, icon ->
            assertClose(24f, boundsWidth(icon), "Figma icon $index width")
            assertClose(24f, boundsHeight(icon), "Figma icon $index height")
        }
        val airDropText = compose.onNodeWithText("AirDrop", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val readyText = compose.onNodeWithText("Ready for Pickup", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertClose(10f, boundsLeft(airDropText) - boundsRight(standardIcon), "Swift method icon/text gap")
        assertClose(10f, boundsLeft(readyText) - boundsRight(readyIcon), "Swift status icon/text gap")

        assertEquals(1, compose.onAllNodesWithTag("packages-filter-method-divider-standard").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("packages-filter-method-divider-express").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("packages-filter-status-divider-7").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("packages-filter-status-divider-12").fetchSemanticsNodes().size)
    }

    private fun assertCallbacks(recorder: Recorder) {
        compose.onNodeWithTag("packages-filter-status-row-7").performClick()
        compose.onNodeWithTag("packages-filter-method-row-express").performClick()
        compose.onNodeWithTag("packages-filter-close").performClick()
        compose.waitForIdle()

        assertEquals(listOf(0), recorder.statusClicks)
        assertEquals(listOf(ShipmentTypeFilter.Express), recorder.methodClicks)
        assertEquals(1, recorder.closeClicks)
    }

    private fun saveSheetScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag("packages-filter-root").captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/packages_filter_sheet",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun boundsLeft(bounds: DpRect): Float = bounds.left.value

    private fun boundsTop(bounds: DpRect): Float = bounds.top.value

    private fun boundsRight(bounds: DpRect): Float = bounds.right.value

    private fun boundsBottom(bounds: DpRect): Float = bounds.bottom.value

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private class Recorder {
        val statusClicks = mutableListOf<Int>()
        val methodClicks = mutableListOf<ShipmentTypeFilter>()
        var closeClicks = 0
    }

    private companion object {
        val sampleStatuses = listOf(
            PackageStatusInfo(7, "Ready for Pickup", "#0f03fc", 10),
            PackageStatusInfo(18, "Paid and Ready for Pick Up", "#19d144", 16),
            PackageStatusInfo(12, "In-Transit to counter", "#83d134", 9),
        )
    }
}
