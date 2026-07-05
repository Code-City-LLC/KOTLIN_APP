package com.ga.airdrop.feature.home

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeActivityTilesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureHomeActivityTilesLight() {
        captureHomeActivityTiles(
            mode = ThemeController.Mode.LIGHT,
            filename = "home_activity_tiles_light.png",
        )
    }

    @Test
    fun captureHomeActivityTilesDark() {
        captureHomeActivityTiles(
            mode = ThemeController.Mode.DARK,
            filename = "home_activity_tiles_dark.png",
        )
    }

    private fun captureHomeActivityTiles(
        mode: ThemeController.Mode,
        filename: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ThemeController.set(mode) }

        compose.setContent {
            AirdropTheme {
                HomeScreen(onNavigate = {})
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Services").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Services").performScrollTo()
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }
}
