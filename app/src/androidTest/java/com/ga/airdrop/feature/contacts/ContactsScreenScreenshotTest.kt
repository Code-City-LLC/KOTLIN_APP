package com.ga.airdrop.feature.contacts

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureHelpLight() {
        captureHelp(
            mode = ThemeController.Mode.LIGHT,
            topFilename = "help_top_light.png",
            socialFilename = "help_social_light.png",
        )
    }

    @Test
    fun captureHelpDark() {
        captureHelp(
            mode = ThemeController.Mode.DARK,
            topFilename = "help_top_dark.png",
            socialFilename = "help_social_dark.png",
        )
    }

    @Test
    fun liveChatRowRoutes() {
        var route: String? = null

        setHelpContent(ThemeController.Mode.LIGHT) {
            route = it
        }

        compose.onNodeWithText("Live Chat").performClick()
        compose.runOnIdle {
            assertEquals(Routes.LIVE_CHAT, route)
        }
    }

    @Test
    fun copyButtonShowsCopiedToast() {
        setHelpContent(ThemeController.Mode.DARK)

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Copy").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Copy")[0].performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("All the information is copied").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun captureHelp(
        mode: ThemeController.Mode,
        topFilename: String,
        socialFilename: String,
    ) {
        setHelpContent(mode)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Contact Number").fetchSemanticsNodes().isNotEmpty()
        }
        saveRootScreenshot(topFilename)

        compose.onNodeWithText("Tiktok: @airdropja").performScrollTo()
        compose.waitForIdle()
        saveRootScreenshot(socialFilename)
    }

    private fun setHelpContent(
        mode: ThemeController.Mode,
        onNavigate: (String) -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }

        compose.setContent {
            AirdropTheme {
                ContactsScreen(onNavigate = onNavigate)
            }
        }
    }

    private fun saveRootScreenshot(filename: String) {
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
