package com.ga.airdrop.feature.contacts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Assert.assertEquals
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

    @Test
    fun delayedReplyStatusIsVisibleWithoutReplacingTheComposer() {
        setContent(
            mode = ThemeController.Mode.LIGHT,
            state = LiveAgentChatUiState(
                input = "I can keep typing",
                status = LIVE_CHAT_STILL_WAITING_STATUS,
            ),
        )

        compose.onNodeWithTag("live-chat-status").assertIsDisplayed()
        compose.onNodeWithText(LIVE_CHAT_STILL_WAITING_STATUS).assertIsDisplayed()
        compose.onNodeWithText("I can keep typing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
    }

    @Test
    fun endedChatShowsWhyAsACentredNotice() {
        setContent(
            mode = ThemeController.Mode.DARK,
            state = LiveAgentChatUiState(
                customerDisplayName = "Chase Camp",
                messages = listOf(
                    LiveAgentChatTurn(
                        id = "customer-1",
                        role = LiveChatRole.Customer,
                        body = "Is my package ready?",
                        senderName = "Chase Camp",
                    ),
                    LiveAgentChatTurn(
                        id = "notice-1",
                        role = LiveChatRole.Notice,
                        body = "This chat was closed by our team. Send a message to start a new chat.",
                    ),
                ),
            ),
        )

        compose.onNodeWithTag("live-chat-notice").assertIsDisplayed()
        compose.onNodeWithText("This chat was closed by our team. Send a message to start a new chat.")
            .assertIsDisplayed()
        compose.onNodeWithText("Is my package ready?").assertIsDisplayed()
    }

    @Test
    fun endChatConfirmsThatItEndsTheConversation() {
        var ended = 0
        setContent(
            mode = ThemeController.Mode.LIGHT,
            state = LiveAgentChatUiState(conversationId = "5f0c3d1e-8a4b-4c55-9d6e-2b7a1c9e0f11"),
            onEndChat = { ended += 1 },
        )

        compose.onNodeWithContentDescription("About Nirvana and chat history").performClick()
        compose.onNodeWithText("End Chat & Start Fresh").performClick()
        compose.onNodeWithText("End this chat?").assertIsDisplayed()
        compose.onNodeWithText(
            "This ends the conversation and starts a fresh one with Nirvana. It cannot be undone.",
        ).assertIsDisplayed()
        compose.onNodeWithText("End & Start Fresh").performClick()

        assertEquals(1, ended)
    }

    private fun setContent(
        mode: ThemeController.Mode,
        state: LiveAgentChatUiState,
        onEndChat: () -> Unit = {},
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
                    onEndChat = onEndChat,
                )
            }
        }
    }
}
