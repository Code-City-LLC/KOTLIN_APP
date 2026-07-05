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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesTaxesParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun salesTaxesStepIconsUseSwiftDuotoneRolesInAppLight() {
        setSalesTaxesContent(ThemeController.Mode.LIGHT)

        assertSwiftIconPalette(dark = false)
        assertFirstStepIconSwiftFigmaGeometry()
        saveRootScreenshot("sales_taxes_icons_swift_light.png")
    }

    @Test
    fun salesTaxesStepIconsUseSwiftDuotoneRolesInAppDark() {
        setSalesTaxesContent(ThemeController.Mode.DARK)

        assertSwiftIconPalette(dark = true)
        assertFirstStepIconSwiftFigmaGeometry()
        saveRootScreenshot("sales_taxes_icons_swift_dark.png")
    }

    private fun setSalesTaxesContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    SalesTaxesScreen(onBack = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftIconPalette(dark: Boolean) {
        salesTaxIconTags.forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            compose.waitForIdle()

            val bitmap = compose.onNodeWithTag(
                tag,
                useUnmergedTree = true,
            ).captureToImage().asAndroidBitmap()
            assertTrue(
                "$tag should keep Swift/Figma orange accent",
                bitmap.countPixels { a, r, g, b -> a > 80 && r > 200 && g in 55..120 && b < 70 } > 4,
            )
            val secondaryCount = if (dark) {
                bitmap.countPixels { a, r, g, b -> a > 80 && r > 220 && g > 220 && b > 220 }
            } else {
                bitmap.countPixels { a, r, g, b -> a > 80 && r in 25..65 && g in 25..65 && b in 25..65 }
            }
            assertTrue(
                "$tag should use Swift DesignTokens textDarkTitle as the secondary stroke",
                secondaryCount > 4,
            )
        }
    }

    private fun assertFirstStepIconSwiftFigmaGeometry() {
        compose.onNodeWithTag(SalesTaxesTags.LOGIN_ICON, useUnmergedTree = true)
            .performScrollTo()
        compose.waitForIdle()

        val root = bounds(SalesTaxesTags.ROOT)
        val icon = bounds(SalesTaxesTags.LOGIN_ICON)
        assertEquals("Sales Taxes frame width", 375f, width(root), 0.75f)
        assertEquals("Sales Taxes step icon width", 40f, width(icon), 0.75f)
        assertEquals("Sales Taxes step icon height", 40f, height(icon), 0.75f)
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun width(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun height(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun Bitmap.countPixels(predicate: (Int, Int, Int, Int) -> Boolean): Int {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                val alpha = pixel ushr 24 and 0xFF
                val red = pixel ushr 16 and 0xFF
                val green = pixel ushr 8 and 0xFF
                val blue = pixel and 0xFF
                if (predicate(alpha, red, green, blue)) count += 1
            }
        }
        return count
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
            "screenshots/sales_taxes_icons",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/sales_taxes_icons")
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
        private val salesTaxIconTags = listOf(
            SalesTaxesTags.LOGIN_ICON,
            SalesTaxesTags.MARKETPLACE_ICON,
            SalesTaxesTags.BUILDING_ICON,
            SalesTaxesTags.FILE_SETTINGS_ICON,
            SalesTaxesTags.FOLDER_CURVE_ICON,
            SalesTaxesTags.SHOPPING_BAG_ICON,
        )
    }
}
