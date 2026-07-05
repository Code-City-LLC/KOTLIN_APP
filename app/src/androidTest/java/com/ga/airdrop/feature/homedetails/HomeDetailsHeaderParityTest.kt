package com.ga.airdrop.feature.homedetails

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeaderTags
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDetailsHeaderParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longTitleUsesSwiftShrinkToFitLight() {
        val backClicks = setHeader(ThemeController.Mode.LIGHT)

        assertLongTitleFitsSwiftHeader()
        assertBackGlyphAndAction(DARK_ICON, backClicks)
        saveRootScreenshot("home_details_header_sales_taxes_swift_light.png")
    }

    @Test
    fun longTitleUsesSwiftShrinkToFitDark() {
        val backClicks = setHeader(ThemeController.Mode.DARK)

        assertLongTitleFitsSwiftHeader()
        assertBackGlyphAndAction(WHITE_ICON, backClicks)
        saveRootScreenshot("home_details_header_sales_taxes_swift_dark.png")
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
                    HomeDetailsHeader(
                        title = LongTitle,
                        onBack = { backClicks.incrementAndGet() },
                    )
                }
            }
        }
        compose.waitForIdle()
        return backClicks
    }

    private fun assertLongTitleFitsSwiftHeader() {
        compose.onNodeWithText(LongTitle, useUnmergedTree = true).assertIsDisplayed()

        val title = bounds(HomeDetailsHeaderTags.TITLE)
        val chevron = bounds(HomeDetailsHeaderTags.BACK_ICON)

        assertClose(24f, width(chevron), "Swift back chevron width")
        assertClose(24f, height(chevron), "Swift back chevron height")
        assertTrue(
            "Swift title should shrink to one title1 line instead of wrapping: height=${height(title)}",
            height(title) <= 33f,
        )
    }

    private fun assertBackGlyphAndAction(targetColor: Int, backClicks: AtomicInteger) {
        val bitmap = compose.onNodeWithTag(
            HomeDetailsHeaderTags.BACK_ICON,
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertTrue("Back icon should use Swift textDarkTitle color", bitmap.hasPixelNear(targetColor))

        compose.onNodeWithTag(HomeDetailsHeaderTags.BACK, useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(1, backClicks.get())
        }
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
            "screenshots/home_details_header",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/home_details_header")
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

    private fun width(rect: DpRect): Float = (rect.right - rect.left).value

    private fun height(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 160) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    abs(red - targetRed) <= COLOR_TOLERANCE &&
                    abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    abs(blue - targetBlue) <= COLOR_TOLERANCE
                ) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        private const val LongTitle = "Shop Tax-Free with AirDrop Limited"
        private const val DARK_ICON = 0xFF292929.toInt()
        private const val WHITE_ICON = 0xFFFFFFFF.toInt()
        private const val COLOR_TOLERANCE = 18
    }
}
