package com.ga.airdrop.feature.delivery

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.data.repo.ActiveDelivery
import com.ga.airdrop.data.repo.TrackedDelivery
import com.ga.airdrop.data.repo.TrackedDeliveryStage
import com.ga.airdrop.feature.shipments.PackageHistoryItem
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryCenterFlowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingAndRetryableErrorAreExplicitStates() {
        val retries = AtomicInteger()
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(
                        loading = false,
                        loadedOnce = true,
                        error = "Delivery service unavailable",
                    ),
                    onBack = {},
                    onRetry = { retries.incrementAndGet() },
                    onRefresh = {},
                    onSelectDelivery = {},
                    onContactUs = {},
                )
            }
        }

        compose.onNodeWithTag(DeliveryCenterTags.ERROR).assertIsDisplayed()
        compose.onNodeWithText("Delivery service unavailable").assertIsDisplayed()
        compose.onNodeWithTag(DeliveryCenterTags.RETRY).performClick()
        compose.runOnIdle { assertEquals(1, retries.get()) }
    }

    @Test
    fun zeroActiveDeliveriesRendersHonestEmptyState() {
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(loading = false, loadedOnce = true),
                    onBack = {},
                    onRetry = {},
                    onRefresh = {},
                    onSelectDelivery = {},
                    onContactUs = {},
                )
            }
        }

        compose.onNodeWithTag(DeliveryCenterTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText("No active deliveries").assertIsDisplayed()
        compose.onNodeWithText("Preparing for Dispatch").assertDoesNotExist()
    }

    @Test
    fun multipleDeliveriesRenderAListAndSelectReturnedPackageId() {
        val selected = AtomicInteger()
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(
                        activeDeliveries = listOf(active(11), active(22)),
                        loading = false,
                        loadedOnce = true,
                    ),
                    onBack = {},
                    onRetry = {},
                    onRefresh = {},
                    onSelectDelivery = { selected.set(it) },
                    onContactUs = {},
                )
            }
        }

        compose.onNodeWithTag(DeliveryCenterTags.LIST).assertIsDisplayed()
        compose.onNodeWithTag(DeliveryCenterTags.row(11)).assertIsDisplayed()
        saveRootScreenshot("delivery_center_contract_list.png")
        compose.onNodeWithTag(DeliveryCenterTags.row(22)).performClick()
        compose.runOnIdle { assertEquals(22, selected.get()) }
    }

    @Test
    fun detailRendersOnlyServerLabelsInExactServerOrder() {
        val delivery = TrackedDelivery(
            status = "out_for_delivery",
            scheduledDate = null,
            assignedAt = null,
            outForDeliveryAt = null,
            deliveredAt = null,
            stages = listOf(
                stage("accepted", "Accepted by dispatch", "done", "2026-07-22T12:00:00Z"),
                stage("road", "Vehicle departed", "current", "2026-07-22T13:00:00Z"),
                stage("handed_over", "Handed to customer", "pending", null),
            ),
        )
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(
                        activeDeliveries = listOf(active(41)),
                        selectedPackageId = 41,
                        delivery = delivery,
                        loading = false,
                        loadedOnce = true,
                    ),
                    onBack = {},
                    onRetry = {},
                    onRefresh = {},
                    onSelectDelivery = {},
                    onContactUs = {},
                )
            }
        }

        compose.onNodeWithTag(DeliveryCenterTags.DETAIL).assertIsDisplayed()
        compose.onNodeWithText("Accepted by dispatch").assertIsDisplayed()
        compose.onNodeWithText("Vehicle departed").assertIsDisplayed()
        compose.onNodeWithText("Handed to customer").assertIsDisplayed()
        compose.onNodeWithText("Preparing for Dispatch").assertDoesNotExist()
        compose.onNodeWithText("Order Confirmed").assertDoesNotExist()

        val acceptedTop = compose.onNodeWithTag(DeliveryCenterTags.stage("accepted"))
            .getUnclippedBoundsInRoot().top
        val roadTop = compose.onNodeWithTag(DeliveryCenterTags.stage("road"))
            .getUnclippedBoundsInRoot().top
        val handedOverTop = compose.onNodeWithTag(DeliveryCenterTags.stage("handed_over"))
            .getUnclippedBoundsInRoot().top
        assertTrue(acceptedTop < roadTop)
        assertTrue(roadTop < handedOverTop)
        saveRootScreenshot("delivery_center_contract_detail.png")
    }

    @Test
    fun nullDetailRendersNoDeliveryInsteadOfInventingProgress() {
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(
                        selectedPackageId = 77,
                        loading = false,
                        loadedOnce = true,
                    ),
                    onBack = {},
                    onRetry = {},
                    onRefresh = {},
                    onSelectDelivery = {},
                    onContactUs = {},
                )
            }
        }

        compose.onNodeWithTag(DeliveryCenterTags.NO_DELIVERY).assertIsDisplayed()
        compose.onNodeWithText("Package #77 does not have a delivery journey to show yet.")
            .assertIsDisplayed()
        compose.onNodeWithText("Preparing for Dispatch").assertDoesNotExist()
    }

    /**
     * The FULL journey: real warehouse history joined to the last mile, and a
     * status that has gone wrong carrying its own way out.
     *
     * Kemar 2026-07-26: Track *"starts at shipment received or drop alerted...
     * It needs to show the REAL statuses"*, and on statuses like Detained at
     * Customs — *show them, with a Contact us action*.
     */
    @Test
    fun detainedAtCustomsIsShownWithItsOwnContactAction() {
        val contacts = AtomicInteger()
        val history = listOf(
            history(2, "Shipment Received", "2026-07-08T00:10:07Z"),
            history(3, "Port of Departure -MIA", "2026-07-12T00:10:07Z"),
            history(9, "Processing at Customs", "2026-07-16T09:00:00Z"),
            history(10, "Detained at Customs", "2026-07-17T09:00:00Z"),
        )
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(
                        activeDeliveries = listOf(active(51)),
                        selectedPackageId = 51,
                        delivery = TrackedDelivery(
                            status = "assigned",
                            scheduledDate = null,
                            assignedAt = null,
                            outForDeliveryAt = null,
                            deliveredAt = null,
                            stages = listOf(stage("assigned", "Driver Assigned", "current", null)),
                        ),
                        history = history,
                        loading = false,
                        loadedOnce = true,
                    ),
                    onBack = {},
                    onRetry = {},
                    onRefresh = {},
                    onSelectDelivery = {},
                    onContactUs = { contacts.incrementAndGet() },
                )
            }
        }

        // The journey begins where it really began.
        compose.onNodeWithText("Shipment Received").assertIsDisplayed()
        compose.onNodeWithText("Port of Departure -MIA").assertIsDisplayed()
        compose.onNodeWithText("Processing at Customs").assertIsDisplayed()
        compose.onNodeWithText("Detained at Customs").assertIsDisplayed()

        // Exactly one row offers help, and it is the detained one.
        compose.onNodeWithTag(DeliveryCenterTags.contactFor("status_10")).assertIsDisplayed()
        compose.onNodeWithTag(DeliveryCenterTags.contactFor("status_9")).assertDoesNotExist()
        compose.onNodeWithTag(DeliveryCenterTags.contactFor("status_2")).assertDoesNotExist()
        saveRootScreenshot("delivery_center_detained_contact.png")

        compose.onNodeWithTag(DeliveryCenterTags.contactFor("status_10")).performClick()
        compose.runOnIdle { assertEquals(1, contacts.get()) }
    }

    /** The warehouse rail must reach the screen even before the last mile does. */
    @Test
    fun warehouseHistoryRendersAheadOfTheLastMile() {
        compose.setContent {
            AirdropTheme {
                DeliveryCenterScreenContent(
                    state = DeliveryCenterUiState(
                        activeDeliveries = listOf(active(61)),
                        selectedPackageId = 61,
                        delivery = TrackedDelivery(
                            status = "assigned",
                            scheduledDate = null,
                            assignedAt = null,
                            outForDeliveryAt = null,
                            deliveredAt = null,
                            stages = listOf(stage("assigned", "Driver Assigned", "current", null)),
                        ),
                        history = listOf(history(2, "Shipment Received", "2026-07-08T00:10:07Z")),
                        loading = false,
                        loadedOnce = true,
                    ),
                    onBack = {},
                    onRetry = {},
                    onRefresh = {},
                    onSelectDelivery = {},
                    onContactUs = {},
                )
            }
        }

        val receivedTop = compose.onNodeWithTag(DeliveryCenterTags.stage("status_2"))
            .getUnclippedBoundsInRoot().top
        val dispatchTop = compose.onNodeWithTag(DeliveryCenterTags.stage("assigned"))
            .getUnclippedBoundsInRoot().top
        assertTrue("Shipment Received must precede the last mile", receivedTop < dispatchTop)
    }

    private fun history(status: Int, name: String, at: String) = PackageHistoryItem(
        status = status,
        statusName = name,
        changedDate = at,
    )

    private fun active(packageId: Int) = ActiveDelivery(
        packageId = packageId,
        trackingCode = "AD-$packageId",
        description = "Package $packageId",
        status = "assigned",
        scheduledDate = null,
        currentStageKey = "assigned",
        updatedAt = null,
    )

    private fun stage(key: String, label: String, state: String, at: String?) =
        TrackedDeliveryStage(key = key, label = label, state = state, at = at)

    private fun saveRootScreenshot(filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AirdropProof")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        )
        var published = false
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { stream ->
            check(
                compose.onNodeWithTag(DeliveryCenterTags.ROOT).captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            )
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0)
            published = true
        } finally {
            if (!published) resolver.delete(uri, null, null)
        }
    }
}
