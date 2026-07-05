package com.ga.airdrop.feature.more

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.R
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
class BackgroundImagesParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var backClicks = 0

    @Test
    fun backgroundImagesUsesSwiftTwoColumnPickerLight() {
        setBackgroundImages(mode = ThemeController.Mode.LIGHT)

        assertSwiftGridGeometry()
        assertSwiftSelectionAndSaveRail()
        saveRootScreenshot("background_images_swift_light.png")
    }

    @Test
    fun backgroundImagesUsesSwiftTwoColumnPickerDark() {
        setBackgroundImages(mode = ThemeController.Mode.DARK)

        assertSwiftGridGeometry()
        compose.onNodeWithTag("background-selected-0", useUnmergedTree = true)
            .assertIsDisplayed()
        saveRootScreenshot("background_images_swift_dark.png")
    }

    @Test
    fun backgroundStoreKeepsSwiftChoiceSetAndMigratesOldFigmaIds() {
        val context = targetContext()
        clearBackgroundPrefs(context)

        assertEquals(14, BackgroundStore.choices.size)
        assertEquals((0..13).toList(), BackgroundStore.choices.map { it.id })

        context.getSharedPreferences("airdrop_background", Context.MODE_PRIVATE)
            .edit()
            .putInt("BACKGROUND_IMAGE_ID", 32)
            .commit()
        assertEquals(BackgroundStore.DEFAULT_ID, BackgroundStore.selectedId(context))
        assertEquals(
            R.drawable.img_more_bg_default_light,
            BackgroundStore.currentBackgroundRes(context, isDark = false),
        )

        BackgroundStore.save(context, 99)
        assertEquals(BackgroundStore.DEFAULT_ID, BackgroundStore.selectedId(context))
    }

    private fun setBackgroundImages(mode: ThemeController.Mode) {
        backClicks = 0
        val context = targetContext()
        clearBackgroundPrefs(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                BackgroundImagesScreen(onBack = { backClicks += 1 })
            }
        }
    }

    private fun assertSwiftGridGeometry() {
        compose.onNodeWithText("Choose a background for your Home tab").assertIsDisplayed()
        assertNoTag("background-tile-14")

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag("background-tile-0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("background-tile-1").getUnclippedBoundsInRoot()
        val selected = compose.onNodeWithTag(
            "background-selected-0",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val unselected = compose.onNodeWithTag(
            "background-unselected-1",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        val expectedTileWidth = (boundsWidth(root) - 40f - 12f) / 2f
        assertClose(20f, first.left.value, "Swift left gutter")
        assertClose(expectedTileWidth, boundsWidth(first), "Swift 2-column tile width")
        assertClose(12f, second.left.value - first.right.value, "Swift column gap")
        assertClose(220f, boundsHeight(first), "Swift tile height")
        assertClose(44f, boundsWidth(selected), "Swift selected badge size")
        assertClose(44f, boundsWidth(unselected), "Swift unselected badge size")

        compose.onNodeWithTag("background-images-grid")
            .performScrollToNode(hasTestTag("background-tile-13"))
        compose.onNodeWithTag("background-tile-13").assertIsDisplayed()
        compose.onNodeWithTag("background-images-grid")
            .performScrollToNode(hasTestTag("background-tile-0"))
        compose.onNodeWithTag("background-tile-0").assertIsDisplayed()
    }

    private fun assertSwiftSelectionAndSaveRail() {
        val context = targetContext()

        compose.onAllNodesWithTag("background-default-pill", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .also { assertTrue("Default pill should be hidden while default is selected", it.isEmpty()) }
        compose.onNodeWithTag("background-tile-1").performClick()
        compose.onNodeWithTag("background-selected-1", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("background-default-pill", useUnmergedTree = true)
            .assertIsDisplayed()

        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, BackgroundStore.selectedId(context))
            assertEquals(
                R.drawable.img_more_bg_1,
                BackgroundStore.currentBackgroundRes(context, isDark = false),
            )
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
        File(targetContext().getExternalFilesDir(null), "screenshots/background_images").also {
            it.mkdirs()
        }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = targetContext()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/background_images")
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

    private fun clearBackgroundPrefs(context: Context) {
        context.getSharedPreferences("airdrop_background", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun assertNoTag(tag: String) {
        assertTrue(
            "$tag should not exist",
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value
}
