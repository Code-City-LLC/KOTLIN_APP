package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentPackageDetailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun detailsUsesSwiftFixedFooterAndUngroupedPaymentCopyLight() {
        setDetailsContent(ThemeController.Mode.LIGHT)

        assertDetailsSwiftParity()
        saveRootScreenshot("payment_package_details_swift_light.png")
    }

    @Test
    fun detailsUsesSwiftFixedFooterAndUngroupedPaymentCopyDark() {
        setDetailsContent(ThemeController.Mode.DARK)

        assertDetailsSwiftParity()
        saveRootScreenshot("payment_package_details_swift_dark.png")
    }

    @Test
    fun historyUsesSwiftTimelineCopyAndFallbackLight() {
        setHistoryContent(ThemeController.Mode.LIGHT)

        assertHistorySwiftParity()
        saveRootScreenshot("payment_package_history_swift_light.png")
    }

    @Test
    fun historyUsesSwiftTimelineCopyAndFallbackDark() {
        setHistoryContent(ThemeController.Mode.DARK)

        assertHistorySwiftParity()
        saveRootScreenshot("payment_package_history_swift_dark.png")
    }

    private fun setDetailsContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(shipmentsHeaderClearance()))
                        PaymentPackageDetailsContent(
                            state = sampleState(),
                            onCifInfo = {},
                        )
                    }
                    PaymentPackageDetailsFooter(
                        onViewHistory = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    ShipmentsDetailHeader(
                        title = "Packages Payment Details",
                        onBack = {},
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setHistoryContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150)
                ) {
                    PaymentShipmentTimeline(state = sampleState())
                    ShipmentsDetailHeader(
                        title = "View History",
                        onBack = {},
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertDetailsSwiftParity() {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val footer = compose.onNodeWithTag("payment-package-footer").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("payment-package-view-history-button").getUnclippedBoundsInRoot()

        assertClose(96f, boundsHeight(footer), "fixed footer height")
        assertClose(boundsBottom(root), boundsBottom(footer), "footer bottom pin")
        assertClose(50f, boundsHeight(button), "View History button height")
        assertClose(20f, boundsTop(button) - boundsTop(footer) - 1f, "button top inset after divider")

        compose.onNodeWithText("Invoice Amount (Declared Value/Cost)")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("CIF Value")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Amount Paid")
            .performScrollTo()
            .assertIsDisplayed()
        assertTextExists("USD 100.00 / JMD 16100.00")
        compose.onNodeWithText("USD 1 = JMD 161.00")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("USD 100.00 / JMD 16,100.00").fetchSemanticsNodes().size)
    }

    private fun assertHistorySwiftParity() {
        compose.onNodeWithText("View History").assertIsDisplayed()
        compose.onNodeWithText("Paid and Ready for Pick Up").assertIsDisplayed()
        assertTextExists("-")
        assertEquals(0, compose.onAllNodesWithText("Paid and Ready for Pickup").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("N/A").fetchSemanticsNodes().size)

        listOf(1, 2, 3, 4, 7, 18, 8).forEach { statusId ->
            assertAtLeast(
                74f,
                boundsHeight(compose.onNodeWithTag("payment-history-step-$statusId").getUnclippedBoundsInRoot()),
                "timeline row $statusId min height",
            )
        }
    }

    private fun sampleState() = PaymentPackageDetailsUiState(
        loading = false,
        exchangeRate = 160.0,
        payment = ShipmentPayment(
            id = 42,
            invoiceId = "INV-100",
            method = "card",
            totalAmount = 100.0,
            trackingCode = "AD12345678901",
            packageId = 7,
            packageDescription = "test_package",
            packageStatusName = "Paid and Ready for Pick Up",
            exchangeRate = 161.0,
        ),
        detail = ShipmentPackageDetail(
            id = 7,
            status = "18",
            statusName = "Paid and Ready for Pick Up",
            shippingMethod = "Standard",
            trackingCode = "AD12345678901",
            shipper = "Amazon",
            courierNumber = "1Z999",
            description = "wireless_mouse",
            weightLbs = 2.5,
            numberOfPieces = 1,
            amount = 55.0,
            history = listOf(
                PackageHistoryItem(status = 1, changedDate = "2024-01-12T15:14:00Z"),
                PackageHistoryItem(status = 2, changedDate = "2024-01-13 15:14:00"),
            ),
            additionalCharges = mapOf("Customs" to 5.0),
            additionalChargesTotal = 5.0,
            exchangeRate = 160.0,
        ),
    )

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/payment_package_details",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float = (bounds.bottom - bounds.top).value

    private fun boundsTop(bounds: androidx.compose.ui.unit.DpRect): Float = bounds.top.value

    private fun boundsBottom(bounds: androidx.compose.ui.unit.DpRect): Float = bounds.bottom.value

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected at least one visible node with text $text",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    private fun assertAtLeast(expected: Float, actual: Float, label: String) {
        assertTrue("$label expected at least $expected but was $actual", actual + 0.75f >= expected)
    }
}
