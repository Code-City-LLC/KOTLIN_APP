package com.ga.airdrop.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    fun darkLoginUsesLargeSwiftFigmaHeroLogo() {
        setLoginContent(ThemeController.Mode.DARK)

        val logo = compose.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("login-bottom-panel").getUnclippedBoundsInRoot()

        assertClose(321f, boundsWidth(logo), "Dark login hero width")
        assertClose(321f / (709f / 720f), boundsHeight(logo), "Dark login hero exported height")
        assertClose(106f, logo.top.value, "Dark login hero top")
        assertTrue(
            "Dark login hero should sit behind the rounded bottom sheet like Swift/Figma",
            logo.bottom.value > panel.top.value,
        )
    }

    @Test
    fun lightLoginKeepsSwiftWordmarkWidth() {
        setLoginContent(ThemeController.Mode.LIGHT)

        val logo = compose.onNodeWithTag("login-logo").getUnclippedBoundsInRoot()

        assertClose(260f, boundsWidth(logo), "Light login wordmark width")
        assertClose(260f / (649f / 180f), boundsHeight(logo), "Light login wordmark height")
        assertTrue(
            "Light logo should stay the wide AirDrop wordmark",
            boundsWidth(logo) / boundsHeight(logo) > 3.4f,
        )
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
}
