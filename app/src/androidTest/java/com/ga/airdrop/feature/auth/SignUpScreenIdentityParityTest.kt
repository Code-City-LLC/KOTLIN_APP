package com.ga.airdrop.feature.auth

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignUpScreenIdentityParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun canonicalChannelsCanBeSelectedAndRetainedOnRealSignupForm() {
        val viewModel = SignUpViewModel()
        compose.setContent {
            AirdropTheme {
                SignUpScreen(onBack = {}, onRegistered = {}, viewModel = viewModel)
            }
        }
        listOf("Saw our ad on Facebook/Instagram", "TikTok", "WhatsApp", "Other").forEach { channel ->
            compose.onNodeWithTag("signup-hear-type").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("airdrop-option-picker").assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(channel))
            compose.onNodeWithText(channel).assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val output = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots/signup-channels")
            output.mkdirs()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            FileOutputStream(File(output, "channel-${hearAboutUsOptions.indexOf(channel)}.png")).use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            compose.onNodeWithText(channel).performClick()
            compose.runOnIdle { assertEquals(channel, viewModel.state.value.hearType) }
            compose.onNodeWithText(channel).assertIsDisplayed()
        }
    }

    @Test
    fun signUpRendersIdentityFieldsAndIdTypePicker() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        val viewModel = SignUpViewModel()

        compose.setContent {
            AirdropTheme {
                SignUpScreen(
                    onBack = {},
                    onRegistered = {},
                    viewModel = viewModel,
                )
            }
        }

        compose.onNodeWithText("Tax Registration Number").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("signup-trn-card").assertIsDisplayed()
        compose.onNodeWithText("ID Type").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("signup-identity-type").assertIsDisplayed().performClick()
        compose.onNodeWithText("Passport").assertIsDisplayed().performClick()
        compose.onNodeWithText("Passport").assertIsDisplayed()
        compose.onNodeWithText("ID Number").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("signup-identity-number-card").assertIsDisplayed()
    }
}
