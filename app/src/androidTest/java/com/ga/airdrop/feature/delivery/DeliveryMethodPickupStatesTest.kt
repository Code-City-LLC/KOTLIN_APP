package com.ga.airdrop.feature.delivery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.data.model.DeliveryWarehouse
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pickup list has three states, and this exists because it briefly had one.
 *
 * Deleting the four invented fallback warehouses was right — their IDs were
 * offset against the server's, so a customer tapping "Kingston" had Montego Bay
 * persisted against their order. But the replacement set an error on the view
 * model that **no composable read**, so a failed /delivery/settings rendered
 * the heading "Select Pickup Location *" with nothing under it: no spinner, no
 * message, no retry — and the Continue button then told the customer to select
 * a warehouse from a list with no rows. A dead end at checkout.
 *
 * Loading and failure were also indistinguishable, because there was no loading
 * flag at all: the first paint of a slow network looked exactly like a failure.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryMethodPickupStatesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aFailedPickupListOffersAWorkingRetry() {
        val retries = AtomicInteger()
        compose.setContent {
            AirdropTheme {
                PickupSection(
                    warehouses = emptyList(),
                    selectedWarehouseId = null,
                    onWarehouseSelected = {},
                    loading = false,
                    error = "We couldn't load the pickup locations.",
                    onRetry = { retries.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithTag("pickup-locations-error").assertIsDisplayed()
        compose.onNodeWithTag("pickup-locations-retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries.get()) }
    }

    /** A slow network must not look like a failure. */
    @Test
    fun loadingIsDistinctFromFailure() {
        compose.setContent {
            AirdropTheme {
                PickupSection(
                    warehouses = emptyList(),
                    selectedWarehouseId = null,
                    onWarehouseSelected = {},
                    loading = true,
                    error = null,
                    onRetry = {},
                )
            }
        }

        assertEquals(
            "a load in flight is not an error",
            0,
            compose.onAllNodesWithTag("pickup-locations-error").fetchSemanticsNodes().size,
        )
    }

    /** With rows, neither the spinner nor the error may appear. */
    @Test
    fun aLoadedListShowsRowsAndNothingElse() {
        compose.setContent {
            AirdropTheme {
                PickupSection(
                    warehouses = listOf(
                        DeliveryWarehouse(
                            id = 2, name = "Kingston",
                            address = "Unit 19 Pristine Plaza, 15 Eastwood Park Rd, Kingston, Jamaica",
                            latitude = 18.0129377, longitude = -76.79937405, isPrimary = false,
                        ),
                    ),
                    selectedWarehouseId = 2,
                    onWarehouseSelected = {},
                    loading = false,
                    error = null,
                    onRetry = {},
                )
            }
        }

        assertEquals(0, compose.onAllNodesWithTag("pickup-locations-error").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("pickup-locations-retry").fetchSemanticsNodes().size)
    }
}
