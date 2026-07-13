package com.ga.airdrop.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignUpScreenIdentityParityTest {

    @get:Rule
    val compose = createComposeRule()

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
