package com.ga.airdrop.feature.home

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.components.AirdropBottomBar
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.components.AirdropTab
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeChromeOpacityParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeChromeUsesSwiftOpaqueSurfacesLight() {
        assertHomeChromeSwiftOpaque(
            mode = ThemeController.Mode.LIGHT,
            expectedSurfaceRgb = lightGray200Rgb,
            filename = "home_chrome_swift_opaque_light.png",
        )
    }

    @Test
    fun homeChromeUsesSwiftOpaqueSurfacesDark() {
        assertHomeChromeSwiftOpaque(
            mode = ThemeController.Mode.DARK,
            expectedSurfaceRgb = darkGray200Rgb,
            filename = "home_chrome_swift_opaque_dark.png",
        )
    }

    private fun assertHomeChromeSwiftOpaque(
        mode: ThemeController.Mode,
        expectedSurfaceRgb: IntArray,
        filename: String,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFF00FF))
                    )
                    AirdropHeader(
                        greeting = "Good Morning Christien-Es...",
                        tierName = "Gold Priority",
                        cartCount = 12,
                        airCoins = "123",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .testTag("home-chrome-header"),
                    )
                    AirdropBottomBar(
                        selected = AirdropTab.Home,
                        onSelect = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag("home-chrome-footer"),
                    )
                }
            }
        }
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val headerBounds = compose.onNodeWithTag("home-chrome-header").getUnclippedBoundsInRoot()
        val footerBounds = compose.onNodeWithTag("home-chrome-footer").getUnclippedBoundsInRoot()

        listOf(
            PixelSample(
                point = samplePoint(headerBounds, xFraction = 0.03f, yInsetDp = 6f),
                expectedRgb = expectedSurfaceRgb,
                label = "Swift opaque hero header top",
            ),
            PixelSample(
                point = samplePoint(headerBounds, xFraction = 0.03f, yInsetDp = headerBounds.heightDp() - 6f),
                expectedRgb = expectedSurfaceRgb,
                label = "Swift opaque hero header bottom",
            ),
            PixelSample(
                point = samplePoint(footerBounds, xFraction = 0.03f, yInsetDp = 8f),
                expectedRgb = expectedSurfaceRgb,
                label = "Swift opaque footer leading",
            ),
            PixelSample(
                point = samplePoint(footerBounds, xFraction = 0.97f, yInsetDp = 8f),
                expectedRgb = expectedSurfaceRgb,
                label = "Swift opaque footer trailing",
            ),
        ).forEach { sample ->
            assertPixelNear(
                bitmap = bitmap,
                point = sample.point,
                expectedRgb = sample.expectedRgb,
                label = sample.label,
            )
        }

        assertHeaderIconUsesSwiftThemeTint(mode)
        saveRootScreenshot(bitmap, filename)
    }

    private fun assertHeaderIconUsesSwiftThemeTint(mode: ThemeController.Mode) {
        val icon = compose.onNodeWithContentDescription("Notifications")
            .captureToImage()
            .asAndroidBitmap()
        val matchingPixels = countPixels(icon) { alpha, red, green, blue ->
            if (mode == ThemeController.Mode.DARK) {
                alpha > 80 && red > 210 && green > 210 && blue > 210
            } else {
                alpha > 80 && red < 80 && green < 80 && blue < 80
            }
        }
        assertTrue(
            "Home header notification icon must use Swift theme tint in $mode",
            matchingPixels > 8,
        )
    }

    private fun countPixels(bitmap: Bitmap, predicate: (Int, Int, Int, Int) -> Boolean): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24 and 0xFF
                val red = pixel ushr 16 and 0xFF
                val green = pixel ushr 8 and 0xFF
                val blue = pixel and 0xFF
                if (predicate(alpha, red, green, blue)) count += 1
            }
        }
        return count
    }

    private fun samplePoint(bounds: DpRect, xFraction: Float, yInsetDp: Float): Pair<Int, Int> {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
            .density
        val x = ((bounds.left.value + (bounds.widthDp() * xFraction)) * density).toInt()
        val y = ((bounds.top.value + yInsetDp) * density).toInt()
        return x to y
    }

    private fun assertPixelNear(
        bitmap: Bitmap,
        point: Pair<Int, Int>,
        expectedRgb: IntArray,
        label: String,
    ) {
        val color = bitmap.getPixel(
            point.first.coerceIn(0, bitmap.width - 1),
            point.second.coerceIn(0, bitmap.height - 1),
        )
        val actual = intArrayOf(
            AndroidColor.red(color),
            AndroidColor.green(color),
            AndroidColor.blue(color),
        )
        val close = actual.indices.all { abs(actual[it] - expectedRgb[it]) <= 2 }
        assertTrue(
            "$label expected=${expectedRgb.joinToString()} actual=${actual.joinToString()}",
            close,
        )
    }

    private data class PixelSample(
        val point: Pair<Int, Int>,
        val expectedRgb: IntArray,
        val label: String,
    )

    private fun saveRootScreenshot(bitmap: Bitmap, filename: String) {
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/home_chrome_opacity")
            .also { it.mkdirs() }
    }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/home_chrome_opacity/"
        runCatching {
            context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                arrayOf(filename, relativePath),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        runCatching {
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
    }

    private fun DpRect.widthDp(): Float = (right - left).value

    private fun DpRect.heightDp(): Float = (bottom - top).value

    private companion object {
        val lightGray200Rgb = intArrayOf(0xF5, 0xF5, 0xF5)
        val darkGray200Rgb = intArrayOf(0x33, 0x33, 0x33)
    }
}
