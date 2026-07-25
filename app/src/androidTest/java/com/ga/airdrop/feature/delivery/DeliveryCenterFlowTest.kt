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
