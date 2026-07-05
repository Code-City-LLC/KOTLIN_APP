package com.ga.airdrop.feature.home

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
        assertHomeChromeOpaque(
            mode = ThemeController.Mode.LIGHT,
            expectedRgb = intArrayOf(0xF5, 0xF5, 0xF5),
            filename = "home_chrome_opacity_swift_light.png",
        )
    }

    @Test
    fun homeChromeUsesSwiftOpaqueSurfacesDark() {
        assertHomeChromeOpaque(
            mode = ThemeController.Mode.DARK,
            expectedRgb = intArrayOf(0x33, 0x33, 0x33),
            filename = "home_chrome_opacity_swift_dark.png",
        )
    }

    private fun assertHomeChromeOpaque(
        mode: ThemeController.Mode,
        expectedRgb: IntArray,
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
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF00FF),
                                        Color(0xFF00FFFF),
                                        Color(0xFFFFF000),
                                    ),
                                    start = Offset.Zero,
                                    end = Offset.Infinite,
                                )
                            )
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
            samplePoint(headerBounds, xFraction = 0.03f, yInsetDp = 6f),
            samplePoint(headerBounds, xFraction = 0.03f, yInsetDp = headerBounds.heightDp() - 6f),
            samplePoint(footerBounds, xFraction = 0.03f, yInsetDp = 8f),
            samplePoint(footerBounds, xFraction = 0.97f, yInsetDp = 8f),
        ).forEachIndexed { index, point ->
            assertPixelNear(
                bitmap = bitmap,
                point = point,
                expectedRgb = expectedRgb,
                label = "Swift opaque gray200 chrome sample $index",
            )
        }

        saveRootScreenshot(bitmap, filename)
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

    private fun saveRootScreenshot(bitmap: Bitmap, filename: String) {
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/home_chrome_opacity")
            .also { it.mkdirs() }
    }

    private fun DpRect.widthDp(): Float = (right - left).value

    private fun DpRect.heightDp(): Float = (bottom - top).value
}
