package com.ga.airdrop.feature.auth

import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenDarkFigmaParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetTheme() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.init(compose.activity.applicationContext)
            ThemeController.set(ThemeController.Mode.SYSTEM)
        }
    }

    @Test
    fun darkLoginMatchesFigmaLogoScaleAndCoreControls() {
        setLoginContent(ThemeController.Mode.DARK)

        compose.onNodeWithTag("login-root").assertIsDisplayed()
        compose.onNodeWithText("Welcome Back!").assertIsDisplayed()
        compose.onNodeWithText("Login to AirDrop").assertIsDisplayed()
        compose.onNodeWithText("Email Address").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Forgot Password?").assertIsDisplayed()
        compose.onNodeWithText("Log In").assertIsDisplayed()
        compose.onNodeWithText("Register").assertIsDisplayed()
        compose.onNodeWithTag("login-email-card").assertIsDisplayed()
        compose.onNodeWithTag("login-password-card").assertIsDisplayed()
        compose.onNodeWithTag("login-password-password-toggle").assertIsDisplayed()

        val logo = compose.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        assertClose(321f, boundsWidth(logo), "Dark logo width from Figma node 40006149:75728")
        assertClose(321f / (642f / 612f), boundsHeight(logo), "Dark logo height")
        assertClose(27f, logo.left.value, "Dark logo left inset")
        assertClose(106f, logo.top.value, "Dark logo top inset")

        val panel = compose.onNodeWithTag("login-panel").getUnclippedBoundsInRoot()
        assertClose(375f, boundsWidth(panel), "Login panel width")
        assertTrue(
            "Login panel should start near the Figma y=288 sheet edge; actual=${panel.top.value}",
            panel.top.value in 275f..315f,
        )

        val button = compose.onNodeWithTag("login-button").getUnclippedBoundsInRoot()
        assertTrue(
            "Login button should sit near Figma y=660; actual=${button.top.value}",
            button.top.value in 640f..700f,
        )
        val register = compose.onNodeWithText("Register").getUnclippedBoundsInRoot()
        assertTrue(
            "Register footer should be visible near Figma y=742; actual=${register.top.value}",
            register.top.value in 715f..790f,
        )

        saveRootScreenshot("login_dark_figma_40006149_75728_current.png")
    }

    private fun setLoginContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.init(compose.activity.applicationContext)
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    LoginScreen(
                        onLoggedIn = {},
                        onRegister = {},
                        onForgotPassword = {},
                        viewModel = LoginViewModel(),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun boundsHeight(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 1.0f)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/login_dark"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create screenshot MediaStore entry")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Unable to open screenshot output stream")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }
}
