package com.ga.airdrop.core.designsystem.components

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
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
class AirdropBottomBarIconParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bottomTabIconsUseSwiftRolesInAppLight() {
        assertBottomBarIconRoles(
            mode = ThemeController.Mode.LIGHT,
            inactiveColor = DARK_ICON,
            screenshotName = "bottom_tab_icons_swift_light.png",
        )
    }

    @Test
    fun bottomTabIconsUseSwiftRolesInAppDark() {
        assertBottomBarIconRoles(
            mode = ThemeController.Mode.DARK,
            inactiveColor = WHITE_ICON,
            screenshotName = "bottom_tab_icons_swift_dark.png",
        )
    }

    private fun assertBottomBarIconRoles(
        mode: ThemeController.Mode,
        inactiveColor: Int,
        screenshotName: String,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        val selectedState = mutableStateOf(AirdropTab.Home)
        compose.setContent {
            AirdropTheme {
                Box(
                    modifier = Modifier
                        .width(375.dp)
                        .height(140.dp)
                        .background(AirdropTheme.colors.gray200),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AirdropBottomBar(
                        selected = selectedState.value,
                        onSelect = {},
                        modifier = Modifier.testTag(ROOT_TAG),
                    )
                }
            }
        }
        compose.waitForIdle()

        AirdropTab.entries.forEach { selectedTab ->
            compose.runOnIdle {
                selectedState.value = selectedTab
            }
            compose.waitForIdle()

            AirdropTab.entries.forEach { tab ->
                val expected = if (tab == selectedTab) ORANGE else inactiveColor
                assertNodeContainsColor(
                    contentDescription = tab.label,
                    target = expected,
                    label = "${mode.name} ${tab.label} icon",
                )
            }

            assertNodeTextContainsColor(
                text = selectedTab.label,
                target = ORANGE,
                label = "${mode.name} ${selectedTab.label} selected label",
            )

            val selectedIcon = captureIcon(selectedTab.label)
            val inactivePeer = AirdropTab.entries.first { it != selectedTab }
            compose.runOnIdle {
                selectedState.value = inactivePeer
            }
            compose.waitForIdle()
            val inactiveIcon = captureIcon(selectedTab.label)
            assertSameOpaqueMask(
                expected = inactiveIcon,
                actual = selectedIcon,
                label = "${mode.name} ${selectedTab.label} selected icon should keep Swift outline shape",
            )

            if (selectedTab == AirdropTab.Home) {
                compose.runOnIdle {
                    selectedState.value = selectedTab
                }
                compose.waitForIdle()
                saveRootScreenshot(screenshotName)
            }
        }
    }

    private fun captureIcon(contentDescription: String): Bitmap =
        compose.onNodeWithContentDescription(
            contentDescription,
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()

    private fun assertNodeContainsColor(
        contentDescription: String,
        target: Int,
        label: String,
    ) {
        val bitmap = captureIcon(contentDescription)
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertNodeTextContainsColor(
        text: String,
        target: Int,
        label: String,
    ) {
        val bitmap = compose.onNodeWithText(text, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun assertSameOpaqueMask(
        expected: Bitmap,
        actual: Bitmap,
        label: String,
    ) {
        assertEquals("$label width", expected.width, actual.width)
        assertEquals("$label height", expected.height, actual.height)

        var expectedOpaque = 0
        var actualOpaque = 0
        var mismatches = 0
        for (x in 0 until expected.width) {
            for (y in 0 until expected.height) {
                val expectedPixelOpaque = expected.alphaAt(x, y) >= MASK_ALPHA_THRESHOLD
                val actualPixelOpaque = actual.alphaAt(x, y) >= MASK_ALPHA_THRESHOLD
                if (expectedPixelOpaque) expectedOpaque += 1
                if (actualPixelOpaque) actualOpaque += 1
                if (expectedPixelOpaque != actualPixelOpaque) mismatches += 1
            }
        }

        val tolerance = maxOf(4, expectedOpaque / 30)
        assertTrue(
            "$label: expected active/inactive masks to match; " +
                "expectedOpaque=$expectedOpaque actualOpaque=$actualOpaque mismatches=$mismatches tolerance=$tolerance",
            abs(expectedOpaque - actualOpaque) <= tolerance && mismatches <= tolerance,
        )
    }

    private fun Bitmap.alphaAt(x: Int, y: Int): Int = (getPixel(x, y) ushr 24) and 0xFF

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag(ROOT_TAG)
            .captureToImage()
            .asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
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

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/bottom_tab_icons")
            .also { it.mkdirs() }
    }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/bottom_tab_icons")
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

    private companion object {
        private const val ROOT_TAG = "bottom-tab-icon-proof-root"
        private const val ORANGE = 0xFFF15114.toInt()
        private const val DARK_ICON = 0xFF292929.toInt()
        private const val WHITE_ICON = 0xFFFFFFFF.toInt()
        private const val COLOR_TOLERANCE = 12
        private const val MASK_ALPHA_THRESHOLD = 80
    }
}
