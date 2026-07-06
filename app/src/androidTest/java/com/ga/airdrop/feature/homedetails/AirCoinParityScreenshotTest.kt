package com.ga.airdrop.feature.homedetails

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirCoinParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun balanceUsesSwiftGeometryAndHistoryActionLight() {
        val historyClicks = AtomicInteger()
        setBalanceContent(ThemeController.Mode.LIGHT, historyClicks)

        assertBalanceSwiftGeometry()
        saveNodeScreenshot("aircoin-balance-root", "aircoin_balance_swift_light.png")
        compose.onNodeWithContentDescription("AirCoin history").performClick()
        compose.runOnIdle {
            assertEquals(1, historyClicks.get())
        }
    }

    @Test
    fun balanceUsesSwiftGeometryDark() {
        setBalanceContent(ThemeController.Mode.DARK)

        assertBalanceSwiftGeometry()
        saveNodeScreenshot("aircoin-balance-root", "aircoin_balance_swift_dark.png")
    }

    @Test
    fun redeemAtCounterShowsSwiftQrSheet() {
        setBalanceContent(ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("aircoin-redeem-button").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("aircoin-redeem-sheet").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("aircoin-redeem-sheet-title").assertIsDisplayed()
        compose.onNodeWithText(
            "Show this code to the AirDrop counter agent to apply your AirCoin balance toward your next pickup."
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("AirCoin redemption QR code").assertIsDisplayed()
        compose.onNodeWithText("Code refreshes when this screen opens. Do not share.").assertIsDisplayed()
        assertClose(220f, boundsWidth("aircoin-redeem-qr-card"), "Swift QR card width")
        assertClose(220f, boundsHeight("aircoin-redeem-qr-card"), "Swift QR card height")
    }

    @Test
    fun redeemPayloadQrIsScannable() {
        val payload = airCoinRedeemPayload("AIR2048", nowSeconds = 1_780_000_000L)
        val bitmap = generateAirCoinRedeemQrBitmap(payload, sizePx = 256)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        ).text

        assertEquals("airdrop:redeem?account=AIR2048&t=1780000000", payload)
        assertEquals(payload, decoded)
    }

    @Test
    fun historyUsesSwiftLedgerGeometryAndCopyLight() {
        setHistoryContent(ThemeController.Mode.LIGHT)

        assertHistorySwiftGeometryAndCopy()
        saveNodeScreenshot("aircoin-history-root", "aircoin_history_swift_light.png")
    }

    @Test
    fun historyUsesSwiftLedgerGeometryAndCopyDark() {
        setHistoryContent(ThemeController.Mode.DARK)

        assertHistorySwiftGeometryAndCopy()
        saveNodeScreenshot("aircoin-history-root", "aircoin_history_swift_dark.png")
    }

    private fun setBalanceContent(
        mode: ThemeController.Mode,
        historyClicks: AtomicInteger = AtomicInteger(),
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    AirCoinBalanceContent(
                        state = AirCoinBalanceUiState(
                            status = AirCoinsStatus(
                                accumulated = 234,
                                redeemed = 23,
                                available = 50,
                            ),
                            accountNumber = "AIR2048",
                            userId = 2048,
                        ),
                        onBack = {},
                        onOpenHistory = { historyClicks.incrementAndGet() },
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
                ) {
                    AirCoinHistoryDetailContent(
                        state = AirCoinHistoryUiState(
                            transactions = listOf(
                                AirCoinTransaction(
                                    id = 1,
                                    amount = 25.0,
                                    referenceId = "#24242433",
                                    createdAt = "2025-10-25T00:00:00Z",
                                ),
                                AirCoinTransaction(
                                    id = 2,
                                    amount = -5.0,
                                    referenceId = "#11112222",
                                    createdAt = "2025-10-26 12:30:00",
                                ),
                            ),
                            loadedOnce = true,
                        ),
                        onBack = {},
                        onLoadMore = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertBalanceSwiftGeometry() {
        assertClose(280f, boundsHeight("aircoin-balance-hero-spacer"), "Balance hero spacer")
        assertClose(120f, boundsWidth("aircoin-balance-left-pill"), "Left conversion pill width")
        assertClose(44f, boundsHeight("aircoin-balance-left-pill"), "Left conversion pill height")
        assertClose(120f, boundsWidth("aircoin-balance-right-pill"), "Right conversion pill width")
        assertClose(44f, boundsHeight("aircoin-balance-right-pill"), "Right conversion pill height")
        assertClose(64f, boundsHeight("aircoin-stat-accumulated"), "Accumulated row height")
        assertClose(64f, boundsHeight("aircoin-stat-redeemed"), "Redeemed row height")
        assertClose(64f, boundsHeight("aircoin-stat-available"), "Available row height")
        assertClose(52f, boundsHeight("aircoin-redeem-button"), "Redeem CTA height")
        assertClose(22f, boundsWidth("aircoin-redeem-icon"), "Redeem QR icon width")
        assertClose(22f, boundsHeight("aircoin-redeem-icon"), "Redeem QR icon height")
        assertClose(40f, boundsWidth("aircoin-balance-tip-icon"), "Tip icon width")
        assertClose(40f, boundsHeight("aircoin-balance-tip-icon"), "Tip icon height")
        compose.onNodeWithText("Redeem at counter").assertIsDisplayed()
    }

    private fun assertHistorySwiftGeometryAndCopy() {
        assertClose(170f, boundsHeight("aircoin-history-hero-wrap"), "History hero wrap height")
        assertClose(150f, boundsWidth("aircoin-history-hero-image"), "History hero image width")
        assertClose(150f, boundsHeight("aircoin-history-hero-image"), "History hero image height")
        assertClose(335f, boundsWidth("aircoin-history-table-card"), "History table card width")
        assertAtLeast(48f, boundsHeight("aircoin-history-header-row"), "History header row min height")
        assertAtLeast(48f, boundsHeight("aircoin-history-row-0"), "History body row min height")

        compose.onNodeWithText("Invoice No").assertIsDisplayed()
        compose.onNodeWithText("Air Coin Used").assertIsDisplayed()
        compose.onNodeWithText("Used Date").assertIsDisplayed()
        compose.onNodeWithText("25 Oct 2025").assertIsDisplayed()
        compose.onNodeWithText("26 Oct 2025").assertIsDisplayed()
        compose.onNodeWithText("25").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Invoice No.").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Date").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("+25").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("-5").fetchSemanticsNodes().size)
    }

    private fun saveNodeScreenshot(tag: String, filename: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/aircoins_swift").also { it.mkdirs() }
    }

    private fun boundsWidth(tag: String): Float =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { (it.right - it.left).value }

    private fun boundsHeight(tag: String): Float =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { (it.bottom - it.top).value }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun assertAtLeast(minimum: Float, actual: Float, label: String) {
        assertTrue("$label expected >= $minimum but was $actual", actual >= minimum)
    }
}
