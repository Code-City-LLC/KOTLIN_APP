package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirdropHeaderUnreadTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unreadNotificationLightsHeaderIndicator() {
        compose.setContent {
            AirdropTheme {
                AirdropHeader(
                    greeting = "Good morning",
                    tierName = "Gold Standard",
                    hasUnreadNotifications = true,
                )
            }
        }
        compose.onNodeWithTag("header-notification-unread").assertIsDisplayed()
    }

    @Test
    fun noUnreadNotificationLeavesIndicatorOff() {
        compose.setContent {
            AirdropTheme {
                AirdropHeader(
                    greeting = "Good morning",
                    tierName = "Gold Standard",
                    hasUnreadNotifications = false,
                )
            }
        }
        compose.onNodeWithTag("header-notification-unread").assertDoesNotExist()
    }
}
