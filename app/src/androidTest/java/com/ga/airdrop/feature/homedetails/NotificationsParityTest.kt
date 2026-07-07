package com.ga.airdrop.feature.homedetails

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirdropNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for Swift origin/main Notifications parity:
 * FigmaNotificationsListViewController keeps the Figma empty state, but renders
 * notification cards whenever local/server rows exist.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun populatedInboxRendersSwiftCardsAndTapRail() {
        var tappedId: String? = null
        setNotificationsContent(
            state = NotificationsUiState(
                items = listOf(
                    AirdropNotification(
                        id = "invoice-1",
                        title = "Invoice Required",
                        body = "Upload an invoice for package AD123.",
                        type = "invoice_required",
                        isRead = false,
                    ),
                    AirdropNotification(
                        id = "payment-1",
                        title = "Payment Required",
                        body = "Storage fee payment is due.",
                        type = "payment_required",
                        isRead = true,
                    ),
                ),
                loadedOnce = true,
            ),
            onNotificationTapped = { tappedId = it.id },
        )

        compose.onNodeWithTag("notifications-list").assertIsDisplayed()
        assertTagAbsent("notifications-empty-state")
        compose.onNodeWithTag("notification-row-invoice-1").assertIsDisplayed()
        assertTagPresent("notification-unread-dot-invoice-1")
        compose.onNodeWithText("Invoice Required").assertIsDisplayed()
        compose.onNodeWithText("Upload an invoice for package AD123.").assertIsDisplayed()
        compose.onNodeWithText("Check Mail").assertIsDisplayed()

        compose.onNodeWithTag("notification-row-invoice-1").performClick()
        compose.runOnIdle {
            assertEquals("invoice-1", tappedId)
        }
    }

    @Test
    fun emptyLoadedInboxKeepsFigmaSettingsState() {
        setNotificationsContent(
            state = NotificationsUiState(loadedOnce = true),
        )

        compose.onNodeWithTag("notifications-empty-state").assertIsDisplayed()
        assertTagAbsent("notifications-list")
        compose.onNodeWithText("You’re all caught up.").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun setNotificationsContent(
        state: NotificationsUiState,
        onNavigate: (String) -> Unit = {},
        onNotificationTapped: (AirdropNotification) -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    NotificationsScreenContent(
                        state = state,
                        onBack = {},
                        onNavigate = onNavigate,
                        onRetry = {},
                        onLoadMore = {},
                        onNotificationTapped = onNotificationTapped,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertTagAbsent(tag: String) {
        assertTrue(
            "$tag should not be in the current Notifications branch",
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertTagPresent(tag: String) {
        assertTrue(
            "$tag should be in the current Notifications branch",
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
