package com.ga.airdrop.feature.more2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaqParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroMatchesSwiftCopyInLight() {
        setFaq(ThemeController.Mode.LIGHT)
        assertSwiftHero()
    }

    @Test
    fun heroMatchesSwiftCopyInDark() {
        setFaq(ThemeController.Mode.DARK)
        assertSwiftHero()
    }

    @Test
    fun noMatchKeepsSwiftHintAndContactSupportRoute() {
        var route: String? = null
        setFaq(ThemeController.Mode.LIGHT, onNavigate = { route = it })

        compose.onNode(hasSetTextAction(), useUnmergedTree = true)
            .performTextInput("zzz-no-faq-match")
        compose.onNodeWithText("No matches for “zzz-no-faq-match”")
            .assertIsDisplayed()
        compose.onNodeWithText("Try different keywords, or tap Contact Support below.")
            .assertIsDisplayed()

        compose.onNodeWithText("Contact Support").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Routes.CONTACTS, route) }
    }

    private fun assertSwiftHero() {
        compose.onNodeWithText("Frequently Asked").assertIsDisplayed()
        compose.onNodeWithText(
            "Quick answers to the questions our customers ask most. " +
                "Tap any card to see the full answer.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("faq-search").assertIsDisplayed()
    }

    private fun setFaq(
        mode: ThemeController.Mode,
        onNavigate: (String) -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                FaqScreen(onBack = {}, onNavigate = onNavigate)
            }
        }
    }
}
