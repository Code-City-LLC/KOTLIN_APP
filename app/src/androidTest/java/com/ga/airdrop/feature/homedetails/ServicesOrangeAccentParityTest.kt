package com.ga.airdrop.feature.homedetails

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServicesOrangeAccentParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun servicesOrangeAccentsUseSwiftFigmaLightOrange() {
        setServicesContent(ThemeController.Mode.LIGHT)
        saveRootScreenshot("services_orange_light.png")

        assertServicesSwiftFigmaGeometry()
        assertTaggedNodeContainsColor(
            tag = "services-main-heading",
            target = FIGMA_ORANGE_LIGHT,
            label = "Services light main heading orange accent",
        )
        assertTaggedNodeColorDominates(
            tag = "services-main-heading",
            expected = FIGMA_ORANGE_LIGHT,
            alternate = FIGMA_DARK_FUNCTION_ORANGE,
            label = "Services light main heading should prefer light accent",
        )

        compose.onNodeWithTag("services-tax-free-heading").performScrollTo()
        compose.waitForIdle()
        assertTaggedNodeContainsColor(
            tag = "services-tax-free-heading",
            target = FIGMA_ORANGE_LIGHT,
            label = "Services light tax-free heading orange accent",
        )
    }

    @Test
    fun servicesOrangeAccentsUseFigmaDarkFunctionOrange() {
        setServicesContent(ThemeController.Mode.DARK)
        saveRootScreenshot("services_orange_dark.png")

        assertServicesSwiftFigmaGeometry()
        assertTaggedNodeContainsColor(
            tag = "services-main-heading",
            target = FIGMA_DARK_FUNCTION_ORANGE,
            label = "Services dark main heading orange accent",
        )
        assertTaggedNodeColorDominates(
            tag = "services-main-heading",
            expected = FIGMA_DARK_FUNCTION_ORANGE,
            alternate = FIGMA_ORANGE_LIGHT,
            label = "Services dark main heading should prefer dark accent",
        )

        compose.onNodeWithTag("services-tax-free-heading").performScrollTo()
        compose.waitForIdle()
        assertTaggedNodeContainsColor(
            tag = "services-tax-free-heading",
            target = FIGMA_DARK_FUNCTION_ORANGE,
            label = "Services dark tax-free heading orange accent",
        )
    }

    private fun setServicesContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                ServicesScreen(onBack = {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("services-root").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTaggedNodeContainsColor(tag: String, target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertServicesSwiftFigmaGeometry() {
        assertClose(68f, nodeHeightDp("services-customer-pill"), "Services customer pill height")
        assertClose(101f, nodeWidthDp("services-customer-avatars"), "Services avatar group width")
        assertClose(27f, nodeHeightDp("services-customer-avatars"), "Services avatar group height")
        assertClose(250.2766f, nodeHeightDp("services-hero"), "Services hero viewport height")
        assertClose(667f, nodeWidthDp("services-hero-image"), "Services canonical hero width")
        assertClose(253f, nodeHeightDp("services-hero-image"), "Services canonical hero height")

        compose.onNodeWithTag("services-logo-marquee").performScrollTo()
        compose.waitForIdle()
        // Semantics reports the content inside the wrapper's 1dp vertical padding.
        assertClose(88f, nodeHeightDp("services-logo-marquee"), "Services marquee content height")
        assertClose(40f, nodeHeightDp("services-logo-row-top"), "Services top marquee row height")
        assertClose(40f, nodeHeightDp("services-logo-row-bottom"), "Services bottom marquee row height")
    }

    private fun nodeWidthDp(tag: String): Float {
        val widthPx = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates.size.width
        return widthPx / compose.activity.resources.displayMetrics.density
    }

    private fun nodeHeightDp(tag: String): Float {
        val heightPx = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates.size.height
        return heightPx / compose.activity.resources.displayMetrics.density
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 1f)
    }

    private fun assertTaggedNodeColorDominates(
        tag: String,
        expected: Int,
        alternate: Int,
        label: String,
    ) {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        val expectedCount = bitmap.pixelCountNear(expected)
        val alternateCount = bitmap.pixelCountNear(alternate)
        assertTrue(
            "$label: expectedCount=$expectedCount alternateCount=$alternateCount",
            expectedCount > alternateCount,
        )
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
        return File(context.getExternalFilesDir(null), "screenshots/services_orange")
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

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        return pixelCountNear(target) > 0
    }

    private fun Bitmap.pixelCountNear(target: Int): Int {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        var count = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    abs(red - targetRed) <= COLOR_TOLERANCE &&
                    abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    abs(blue - targetBlue) <= COLOR_TOLERANCE
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private companion object {
        private const val FIGMA_ORANGE_LIGHT = 0xFFF15114.toInt()
        private const val FIGMA_DARK_FUNCTION_ORANGE = 0xFFF46427.toInt()
        private const val COLOR_TOLERANCE = 8
        private const val PROOF_SCREENSHOT_DIR = "Pictures/kotlin_ui_proof/services_orange"
    }
}
