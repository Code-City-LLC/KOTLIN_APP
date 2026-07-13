package com.ga.airdrop.feature.auth

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
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
class AuthFigmaParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun splashShowsFigmaWelcomeBeforeOnboarding() {
        setSplashContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Welcome to AirDrop").assertIsDisplayed()
        compose.onNodeWithText("Shop. Ship. Simplified.").assertIsDisplayed()
        saveRootScreenshot("auth_splash_figma.png")
    }

    @Test
    fun darkLoginUsesFigmaLargeLogoAndBottomSheetGeometry() {
        setLoginContent(ThemeController.Mode.DARK)

        val logo = compose.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("login-bottom-panel").getUnclippedBoundsInRoot()

        assertClose(321f, boundsWidth(logo), "Figma dark login logo width")
        assertClose(306f, boundsHeight(logo), "Figma dark login hero height")
        assertTrue(
            "Figma dark login sheet should start around y=288 on a 375x812 frame; actual=${panel.top.value}",
            panel.top.value in 280f..295f,
        )
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertRedFigmaLogoPixel(bitmap, xDp = 165f, yDp = 142f)
        compose.onNodeWithText("Welcome Back!").assertIsDisplayed()
        compose.onNodeWithText("Login to AirDrop").assertIsDisplayed()
        compose.onNodeWithText("Log In").assertIsDisplayed().assertHasClickAction()
        saveRootScreenshot("auth_login_dark_figma.png")
        compose.onNodeWithText("Register").assertIsDisplayed()
    }

    @Test
    fun lightLoginUsesFigmaWordmarkGeometryAndPosition() {
        setLoginContent(ThemeController.Mode.LIGHT)

        val logo = compose.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()

        assertClose(260f, boundsWidth(logo), "Figma light login logo width")
        assertClose(72f, boundsHeight(logo), "Figma light login logo height")
        assertClose(150f, logo.top.value, "Figma light login logo top")
        saveRootScreenshot("auth_login_light_figma.png")
    }

    @Test
    fun onboardingStartsWithChooseYourLookBeforeIntroSlides() {
        setOnboardingContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Choose Your Look").assertIsDisplayed()
        saveRootScreenshot("auth_onboarding_choose_your_look.png")
        assertEquals(
            "Swift/Figma shows Choose Your Look before the intro carousel",
            0,
            compose.onAllNodesWithText(
                "Browse products or upload shipments directly from your phone in just seconds.",
            ).fetchSemanticsNodes().size,
        )

        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(
                "Browse products or upload shipments directly from your phone in just seconds.",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        saveRootScreenshot("auth_onboarding_first_intro.png")
    }

    private fun setLoginContent(mode: ThemeController.Mode) {
        setTheme(mode)
        val viewModel = LoginViewModel()
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp),
                ) {
                    LoginScreen(
                        onLoggedIn = {},
                        onRegister = {},
                        onForgotPassword = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setSplashContent(mode: ThemeController.Mode) {
        setTheme(mode)
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp),
                ) {
                    SplashScreen(onFinished = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setOnboardingContent(mode: ThemeController.Mode) {
        setTheme(mode)
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp),
                ) {
                    OnboardingScreen(onFinished = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setTheme(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.init(InstrumentationRegistry.getInstrumentation().targetContext)
            ThemeController.set(mode)
        }
    }

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun boundsHeight(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 1.0f)
    }

    private fun assertRedFigmaLogoPixel(bitmap: Bitmap, xDp: Float, yDp: Float) {
        val x = (xDp * bitmap.width / 375f).toInt()
        val y = (yDp * bitmap.height / 812f).toInt()
        val pixel = bitmap.getPixel(x, y)
        val red = AndroidColor.red(pixel)
        val green = AndroidColor.green(pixel)
        val blue = AndroidColor.blue(pixel)
        assertTrue(
            "Figma dark login logo pixel should be red/orange at ($x,$y); actual rgb=($red,$green,$blue)",
            red >= 230 && green in 120..210 && blue <= 130,
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/auth_figma")
            .also { it.mkdirs() }
    }

    private fun saveScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/auth_figma/"
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
}
