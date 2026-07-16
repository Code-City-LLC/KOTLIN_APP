package com.ga.airdrop.feature.shop

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShopInnerHeaderParityTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun restoreTheme() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
    }

    @Test
    fun myCartBackControlKeepsFigmaGlyphInsideSeparateHitFrame() {
        val backClicks = AtomicInteger(0)
        renderHeader(
            mode = ThemeController.Mode.LIGHT,
            title = "My Cart",
            backClicks = backClicks,
        )

        assertExactBackGeometryAndAction(backClicks)
        assertGlyphActuallyRenders()
        saveRootScreenshot("shop_inner_header_my_cart_figma_light.png")
    }

    @Test
    fun sharedHeaderPreservesBackGeometryWithDarkThemeAndTrailingContent() {
        val backClicks = AtomicInteger(0)
        renderHeader(
            mode = ThemeController.Mode.DARK,
            title = "Auction Checkout",
            backClicks = backClicks,
            includeTrailing = true,
        )

        assertExactBackGeometryAndAction(backClicks)
        assertGlyphActuallyRenders()
        saveRootScreenshot("shop_inner_header_shared_figma_dark.png")
    }

    private fun renderHeader(
        mode: ThemeController.Mode,
        title: String,
        backClicks: AtomicInteger,
        includeTrailing: Boolean = false,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(140.dp),
                ) {
                    ShopInnerHeader(
                        title = title,
                        onBack = backClicks::incrementAndGet,
                        trailing = {
                            if (includeTrailing) {
                                Box(Modifier.size(24.dp))
                            }
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertExactBackGeometryAndAction(backClicks: AtomicInteger) {
        val targetNode = compose.onNodeWithTag(
            ShopInnerHeaderTags.BACK_TARGET,
            useUnmergedTree = true,
        )
        val glyphNode = compose.onNodeWithTag(
            ShopInnerHeaderTags.BACK_GLYPH,
            useUnmergedTree = true,
        )

        targetNode.assertHasClickAction()
        val target = targetNode.getUnclippedBoundsInRoot()
        val glyph = glyphNode.getUnclippedBoundsInRoot()

        val targetWidth = target.right.value - target.left.value
        val targetHeight = target.bottom.value - target.top.value
        val glyphWidth = glyph.right.value - glyph.left.value
        val glyphHeight = glyph.bottom.value - glyph.top.value

        assertClose(24f, targetWidth, "Figma back touch-frame width")
        assertClose(24f, targetHeight, "Figma back touch-frame height")
        assertClose(18f, glyphWidth, "Figma back glyph width")
        assertClose(15f, glyphHeight, "Figma back glyph height")
        assertClose(
            target.left.value + targetWidth / 2f,
            glyph.left.value + glyphWidth / 2f,
            "centered back glyph x",
        )
        assertClose(
            target.top.value + targetHeight / 2f,
            glyph.top.value + glyphHeight / 2f,
            "centered back glyph y",
        )

        targetNode.performClick()
        assertEquals("the 24dp control must own the Back action", 1, backClicks.get())
    }

    private fun assertGlyphActuallyRenders() {
        val bitmap = compose.onNodeWithTag(
            ShopInnerHeaderTags.BACK_GLYPH,
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertTrue(
            "the exact Figma vector must contrast with its corner/background pixel",
            bitmap.hasContrastingPixel(),
        )
    }

    private fun saveRootScreenshot(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(
            File(context.getExternalFilesDir(null), "screenshots/shop_header").also { it.mkdirs() },
            fileName,
        )
        FileOutputStream(output).use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun Bitmap.hasContrastingPixel(): Boolean {
        val background = getPixel(0, 0)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                val distance = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                    kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                    kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background)) +
                    kotlin.math.abs(android.graphics.Color.alpha(pixel) - android.graphics.Color.alpha(background))
                if (distance > 32) return true
            }
        }
        return false
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals("$label: expected=$expected actual=$actual", expected, actual, 0.6f)
    }
}
