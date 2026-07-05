package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class More2InnerHeaderParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun innerHeaderUsesSwiftBackChevronLight() {
        val backClicks = setHeader(mode = ThemeController.Mode.LIGHT)

        assertSwiftHeaderGeometry()
        assertBackGlyphIsChevronNotTailedArrow(DARK_CHEVRON)
        assertChevronContainsColor(DARK_CHEVRON, "Swift textDarkTitle chevron in light mode")
        saveRootScreenshot("more2_inner_header_swift_light.png")
        compose.waitForIdle()
        compose.onNodeWithTag("more2-inner-header-back", useUnmergedTree = true).performClick()
        assertEquals(1, backClicks.get())
    }

    @Test
    fun innerHeaderUsesSwiftBackChevronDark() {
        val backClicks = setHeader(mode = ThemeController.Mode.DARK)

        assertSwiftHeaderGeometry()
        assertBackGlyphIsChevronNotTailedArrow(WHITE_CHEVRON)
        assertChevronContainsColor(WHITE_CHEVRON, "Swift textDarkTitle chevron in dark mode")
        saveRootScreenshot("more2_inner_header_swift_dark.png")
        compose.waitForIdle()
        compose.onNodeWithTag("more2-inner-header-back", useUnmergedTree = true).performClick()
        assertEquals(1, backClicks.get())
    }

    private fun setHeader(mode: ThemeController.Mode): AtomicInteger {
        val backClicks = AtomicInteger()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(132.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    More2InnerHeader(
                        title = "Promotions",
                        onBack = { backClicks.incrementAndGet() },
                    )
                }
            }
        }
        compose.waitForIdle()
        return backClicks
    }

    private fun assertSwiftHeaderGeometry() {
        val back = bounds("more2-inner-header-back")
        val chevron = bounds("more2-inner-header-back-chevron")

        assertClose(36f, boundsWidth(back), "Swift back tap target width")
        assertClose(36f, boundsHeight(back), "Swift back tap target height")
        assertClose(24f, boundsWidth(chevron), "Swift back chevron width")
        assertClose(24f, boundsHeight(chevron), "Swift back chevron height")
    }

    private fun assertBackGlyphIsChevronNotTailedArrow(target: Int) {
        val bitmap = compose.onNodeWithTag(
            "more2-inner-header-back-chevron",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        val colored = bitmap.coloredBounds(target)
        val coloredWidth = colored.maxX - colored.minX + 1
        val coloredHeight = colored.maxY - colored.minY + 1

        assertTrue(
            "Swift rotated chevron should be taller than it is wide; tailed arrow is wide",
            coloredHeight > coloredWidth,
        )
    }

    private fun assertChevronContainsColor(target: Int, label: String) {
        val bitmap = compose.onNodeWithTag(
            "more2-inner-header-back-chevron",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()

        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun Bitmap.coloredBounds(target: Int): PixelBounds {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) > COLOR_TOLERANCE ||
                    kotlin.math.abs(green - targetGreen) > COLOR_TOLERANCE ||
                    kotlin.math.abs(blue - targetBlue) > COLOR_TOLERANCE
                ) {
                    continue
                }
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        assertTrue("Chevron should render colored pixels", maxX >= minX && maxY >= minY)
        return PixelBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
    }

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
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/more2_inner_header",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/more2_inner_header")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        outputStream.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private data class PixelBounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
    )

    private companion object {
        const val DARK_CHEVRON = 0xFF292929.toInt()
        const val WHITE_CHEVRON = 0xFFFFFFFF.toInt()
        const val COLOR_TOLERANCE = 18
    }
}
