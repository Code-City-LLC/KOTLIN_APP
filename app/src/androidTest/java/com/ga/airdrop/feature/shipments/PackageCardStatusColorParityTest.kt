package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageCardStatusColorParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun packageCardStatusUsesSwiftStatusColorMapper() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(AirdropTheme.colors.gray150)
                        .padding(20.dp),
                ) {
                    PackageCard(
                        pkg = samplePackage(id = 701, statusName = "Pending"),
                        exchangeRate = 160.0,
                        onClick = {},
                        onToggleCart = {},
                        inCart = false,
                        testTag = "package-pending",
                    )
                    PackageCard(
                        pkg = samplePackage(id = 702, statusName = "Cancelled"),
                        exchangeRate = 160.0,
                        onClick = {},
                        onToggleCart = {},
                        inCart = false,
                        testTag = "package-cancelled",
                    )
                }
            }
        }
        compose.waitForIdle()

        val pending = compose.onNodeWithTag(
            "package-pending-status-value",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertTrue("Pending status should render Swift pending orange", pending.hasPixelNear(PENDING))
        assertFalse("Pending status must not render completed green", pending.hasPixelNear(COMPLETED))

        val cancelled = compose.onNodeWithTag(
            "package-cancelled-status-value",
            useUnmergedTree = true,
        ).captureToImage().asAndroidBitmap()
        assertTrue("Cancelled status should render Swift cancel gray", cancelled.hasPixelNear(CANCELLED))
        assertFalse("Cancelled status must not render completed green", cancelled.hasPixelNear(COMPLETED))
    }

    @Test
    fun packageCardCartAffordanceMatchesSwiftStatusGate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(AirdropTheme.colors.gray150)
                        .padding(20.dp),
                ) {
                    PackageCard(
                        pkg = samplePackage(id = 801, status = "2", statusName = "Shipment Received"),
                        exchangeRate = 160.0,
                        onClick = {},
                        onToggleCart = {},
                        inCart = false,
                        testTag = "package-received",
                        cartToggleTestTag = "package-received-cart",
                    )
                    PackageCard(
                        pkg = samplePackage(id = 802, status = "7", statusName = "Ready for Pickup"),
                        exchangeRate = 160.0,
                        onClick = {},
                        onToggleCart = {},
                        inCart = false,
                        testTag = "package-ready",
                        cartToggleTestTag = "package-ready-cart",
                    )
                    PackageCard(
                        pkg = samplePackage(id = 803, status = "18", statusName = "Paid and Ready for Pick Up"),
                        exchangeRate = 160.0,
                        onClick = {},
                        onToggleCart = {},
                        inCart = true,
                        testTag = "package-paid-ready",
                        cartToggleTestTag = "package-paid-ready-cart",
                    )
                    PackageCard(
                        pkg = samplePackage(id = 804, status = "8", statusName = "Delivered"),
                        exchangeRate = 160.0,
                        onClick = {},
                        onToggleCart = {},
                        inCart = false,
                        testTag = "package-delivered",
                        cartToggleTestTag = "package-delivered-cart",
                    )
                }
            }
        }
        compose.waitForIdle()

        assertEquals(
            "Swift omits add-to-cart for non-ready Shipment Received status",
            0,
            compose.onAllNodesWithTag("package-received-cart", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            "Swift shows add-to-cart for status 7 Ready for Pickup",
            1,
            compose.onAllNodesWithTag("package-ready-cart", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            "Swift shows add-to-cart/check for status 18 Paid and Ready for Pick Up",
            1,
            compose.onAllNodesWithTag("package-paid-ready-cart", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            "Swift omits add-to-cart for Delivered status",
            0,
            compose.onAllNodesWithTag("package-delivered-cart", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    private fun samplePackage(id: Int, status: String? = null, statusName: String): ShipmentPackage =
        ShipmentPackage(
            id = id,
            description = "status color proof",
            weightLbs = 2.0,
            status = status,
            statusName = statusName,
            shippingMethod = "Standard",
            additionalChargesTotal = 0.0,
        )

    private fun Bitmap.hasPixelNear(target: Int, tolerance: Int = 28): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 170) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= tolerance &&
                    kotlin.math.abs(green - targetGreen) <= tolerance &&
                    kotlin.math.abs(blue - targetBlue) <= tolerance
                ) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        const val COMPLETED = 0xFF39A634.toInt()
        const val PENDING = 0xFFF2A813.toInt()
        const val CANCELLED = 0xFFB8B8B8.toInt()
    }
}
