package com.ga.airdrop.feature.contacts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveAgentChatScreenParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStateMatchesFigmaLiveChatShell() {
        setContent(
            mode = ThemeController.Mode.DARK,
            state = LiveAgentChatUiState(),
        )

        compose.onNodeWithTag("live-chat-screen").assertIsDisplayed()
        compose.onNodeWithText("Live Chat").assertIsDisplayed()
        compose.onNodeWithText("How may I help\nyou today!").assertIsDisplayed()
        compose.onNodeWithText("Type your question here...").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("About Nirvana and chat history").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
    }

    @Test
    fun activeStateRendersCustomerAndNirvanaBubbles() {
        setContent(
            mode = ThemeController.Mode.LIGHT,
            state = LiveAgentChatUiState(
                customerDisplayName = "Chase Camp",
                agentDisplayName = "Nirvana",
                messages = listOf(
                    LiveAgentChatTurn(
                        id = "customer-1",
                        role = LiveChatRole.Customer,
                        body = "AI Note: Refer to Section 4.2",
                        senderName = "Chase Camp",
                    ),
                    LiveAgentChatTurn(
                        id = "assistant-1",
                        role = LiveChatRole.Assistant,
                        body = "How can I help you Ahmed?",
                        senderName = "Nirvana",
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Chase Camp").assertIsDisplayed()
        compose.onNodeWithText("AI Note: Refer to Section 4.2").assertIsDisplayed()
        compose.onNodeWithText("Nirvana").assertIsDisplayed()
        compose.onNodeWithText("How can I help you Ahmed?").assertIsDisplayed()
    }

    private fun setContent(
        mode: ThemeController.Mode,
        state: LiveAgentChatUiState,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                LiveAgentChatContent(
                    state = state,
                    onBack = {},
                    onInputChange = {},
                    onSend = {},
                )
            }
        }
    }
}
