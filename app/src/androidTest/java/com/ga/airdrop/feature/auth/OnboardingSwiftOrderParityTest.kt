package com.ga.airdrop.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingSwiftOrderParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chooseLookIsSeparateScreenBeforeIntroCarousel() {
        var continued = false
        setContent {
            ChooseYourLookScreen(onContinue = { continued = true })
        }

        compose.onNodeWithText("Choose Your Look").assertIsDisplayed()
        compose.onNodeWithText("Select Light or Dark mode for the best experience.").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertTrue("Continue should advance to the intro onboarding route", continued)
        }
    }

    @Test
    fun introCarouselStartsAfterChooseLookAndCanFinish() {
        var finished = false
        setContent {
            OnboardingScreen(onFinished = { finished = true })
        }

        compose.onNodeWithText("Shop & Send\nEasily").assertIsDisplayed()
        compose.onNodeWithText("Choose Your Look").assertDoesNotExist()
        compose.onNodeWithText("Skip").performClick()

        compose.runOnIdle {
            assertTrue("Skip should mark intro onboarding complete", finished)
        }
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.init(compose.activity.applicationContext)
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                content()
            }
        }
        compose.waitForIdle()
    }
}
