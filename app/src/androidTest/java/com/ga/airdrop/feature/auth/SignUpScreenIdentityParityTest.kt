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

    /**
     * KEMAR RULING 2026-07-19 (Swift 64f4fdc): sign-up must NOT collect
     * TRN/identity — customers add identity via Profile after shipping a
     * package. This test locks the ruling so no parity pass re-adds the fields.
     */
    @Test
    fun signUpRendersNoIdentityFieldsPerKemarRuling() {
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

        compose.onNodeWithText("First Name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tax Registration Number").assertDoesNotExist()
        compose.onNodeWithTag("signup-trn-card").assertDoesNotExist()
        compose.onNodeWithText("ID Type").assertDoesNotExist()
        compose.onNodeWithTag("signup-identity-type").assertDoesNotExist()
        compose.onNodeWithText("ID Number").assertDoesNotExist()
        compose.onNodeWithTag("signup-identity-number-card").assertDoesNotExist()
    }
}
