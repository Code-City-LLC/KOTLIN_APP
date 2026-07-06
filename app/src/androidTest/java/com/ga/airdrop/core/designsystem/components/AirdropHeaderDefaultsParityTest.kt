package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirdropHeaderDefaultsParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun blankHeaderDataKeepsSwiftDefaultTierAndAirCoinsLight() {
        assertSwiftDefaults(ThemeController.Mode.LIGHT)
    }

    @Test
    fun blankHeaderDataKeepsSwiftDefaultTierAndAirCoinsDark() {
        assertSwiftDefaults(ThemeController.Mode.DARK)
    }

    private fun assertSwiftDefaults(mode: ThemeController.Mode) {
        var tierClicks = 0
        var airCoinClicks = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(140.dp)
                ) {
                    AirdropHeader(
                        greeting = "",
                        tierName = "",
                        airCoins = "",
                        style = AirdropHeaderStyle.Solid,
                        onTierClick = { tierClicks += 1 },
                        onAirCoinsClick = { airCoinClicks += 1 },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .testTag("swift-default-header"),
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("swift-default-header").assertIsDisplayed()
        compose.onNodeWithText(defaultGreetingForNow()).assertIsDisplayed()
        compose.onNodeWithText("Gold Standard").assertIsDisplayed()
        compose.onNode(
            hasClickAction() and (hasText("Gold Standard") or hasAnyDescendant(hasText("Gold Standard"))),
            useUnmergedTree = true,
        ).performClick()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithContentDescription("AirCoins").assertIsDisplayed()
        compose.onNode(
            hasClickAction() and hasContentOrDescendant("AirCoins"),
            useUnmergedTree = true,
        ).performClick()

        compose.runOnIdle {
            assertEquals("Swift default tier remains tappable", 1, tierClicks)
            assertEquals("Swift default AirCoins pill remains tappable", 1, airCoinClicks)
        }
    }

    private fun defaultGreetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    private fun hasContentOrDescendant(description: String): SemanticsMatcher =
        hasContentDescription(description) or hasAnyDescendant(hasContentDescription(description))
}
