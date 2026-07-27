package com.ga.airdrop.feature.shipments

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Device proof for status 20. The unit test pins the CATALOGUE; this pins that
 * the row a customer actually taps really renders and really reports id 20.
 *
 * A catalogue entry that no screen shows is not a shipped feature — that is the
 * exact gap this whole change existed to close, so verifying it only in a unit
 * test would repeat the mistake one layer up.
 */
class StatusTwentyFilterRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sheet(onSelect: (Int) -> Unit = {}) {
        compose.setContent {
            AirdropTheme {
                PackagesFilterSheet(
                    statuses = ShipmentStatusCatalog.defaults,
                    selectedStatus = 0,
                    selectedMethod = ShipmentTypeFilter.All,
                    onSelectStatus = onSelect,
                    onSelectMethod = {},
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun statusTwentyRowIsVisibleAndSelectable() {
        var picked = -1
        sheet { picked = it }

        compose.onNodeWithTag("packages-filter-status-card")
            .performScrollToNode(hasText("Paid and Ready for Delivery"))
        compose.onNodeWithText("Paid and Ready for Delivery").assertIsDisplayed()

        compose.onNodeWithText("Paid and Ready for Delivery").performClick()
        compose.waitForIdle()

        assertEquals(
            "tapping the row must filter by status 20, not the Pick Up status it borrows its colour from",
            20,
            picked,
        )
    }

    /**
     * The two share a colour on Kemar's instruction. If they ever collapsed into
     * one row — or the wrong id were wired — the customer would filter for
     * delivery packages and be shown pickup ones, silently.
     */
    @Test
    fun pickUpAndDeliveryAreTwoSeparateRows() {
        var picked = -1
        sheet { picked = it }

        compose.onNodeWithTag("packages-filter-status-card")
            .performScrollToNode(hasText("Paid and Ready for Pick Up"))
        compose.onNodeWithText("Paid and Ready for Pick Up").performClick()
        compose.waitForIdle()
        assertEquals(18, picked)

        compose.onNodeWithTag("packages-filter-status-card")
            .performScrollToNode(hasText("Paid and Ready for Delivery"))
        compose.onNodeWithText("Paid and Ready for Delivery").performClick()
        compose.waitForIdle()
        assertEquals(20, picked)
    }
}
