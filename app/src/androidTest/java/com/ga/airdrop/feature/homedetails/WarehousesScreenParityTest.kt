package com.ga.airdrop.feature.homedetails

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
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
class WarehousesScreenParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun warehouseHeroUsesSwiftGeometryLight() {
        setWarehouseContent(ThemeController.Mode.LIGHT, initialType = "express")

        assertSwiftHeroGeometry()
        assertSwiftNoteGeometry()
        compose.onNodeWithText("Express (Air Express)").assertIsDisplayed()
        saveRootScreenshot("warehouse_express_swift_light.png")
    }

    @Test
    fun warehouseHeroUsesSwiftGeometryDark() {
        setWarehouseContent(ThemeController.Mode.DARK, initialType = "standard")

        assertSwiftHeroGeometry()
        assertSwiftNoteGeometry()
        compose.onNodeWithText("AirDrop (Air Freight)").assertIsDisplayed()
        saveRootScreenshot("warehouse_standard_swift_dark.png")
    }

    @Test
    fun warehouseTabsKeepSwiftSingleScreenFlow() {
        setWarehouseContent(ThemeController.Mode.LIGHT, initialType = "standard")

        compose.onNodeWithTag("warehouse-tab-seadrop").performClick()
        compose.onNodeWithText("SeaDrop (Sea Freight)").assertIsDisplayed()
        assertSwiftHeroGeometry()

        compose.onNodeWithTag("warehouse-tab-express").performClick()
        compose.onNodeWithText("Express (Air Express)").assertIsDisplayed()
        assertSwiftHeroGeometry()
    }

    @Test
    fun copyAllToastUsesSwiftBottomPlacement() {
        setWarehouseContent(ThemeController.Mode.DARK, initialType = "standard")

        compose.onNodeWithContentDescription("Copy all warehouse info").performClick()
        compose.onNodeWithTag("warehouse-copy-toast").assertIsDisplayed()

        val root = compose.onNodeWithTag("warehouse-root").getUnclippedBoundsInRoot()
        val toast = compose.onNodeWithTag("warehouse-copy-toast").getUnclippedBoundsInRoot()
        assertClose(
            40f,
            (root.bottom - toast.bottom).value,
            "Swift warehouse copy toast bottom safe-area offset",
        )
        saveRootScreenshot("warehouse_copy_toast_swift_dark.png")
    }

    private fun setWarehouseContent(
        mode: ThemeController.Mode,
        initialType: String,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    WarehousesScreen(
                        onBack = {},
                        initialType = initialType,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftHeroGeometry() {
        assertClose(240f, boundsHeight("warehouse-hero-wrap"), "Swift hero wrap height")
        assertClose(240f, boundsHeight("warehouse-hero-image"), "Swift hero image height")
        assertClose(60f, boundsWidth("warehouse-hero-badge"), "Swift badge width")
        assertClose(60f, boundsHeight("warehouse-hero-badge"), "Swift badge height")
        assertClose(28f, boundsWidth("warehouse-hero-badge-icon"), "Swift badge icon width")
        assertClose(28f, boundsHeight("warehouse-hero-badge-icon"), "Swift badge icon height")
        compose.onNodeWithTag("warehouse-title").assertIsDisplayed()
    }

    private fun assertSwiftNoteGeometry() {
        assertClose(20f, boundsWidth("warehouse-note-icon"), "Swift note icon width")
        assertClose(20f, boundsHeight("warehouse-note-icon"), "Swift note icon height")
        assertTrue(
            compose.onAllNodesWithTag("warehouse-note-title").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag("warehouse-root").captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/warehouses_swift").also { it.mkdirs() }
    }

    private fun boundsWidth(tag: String): Float =
        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.right - it.left).value }

    private fun boundsHeight(tag: String): Float =
        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }
}
