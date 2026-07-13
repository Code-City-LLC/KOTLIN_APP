package com.ga.airdrop.feature.homedetails

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.ga.airdrop.core.designsystem.theme.darkAirdropColors
import com.ga.airdrop.core.designsystem.theme.lightAirdropColors
import com.ga.airdrop.data.model.AirCoinTransaction
import com.ga.airdrop.data.model.AirCoinsStatus
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
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
    fun balanceUsesFigmaGeometryAndHistoryActionLight() {
        val historyClicks = AtomicInteger()
        setBalanceContent(ThemeController.Mode.LIGHT, historyClicks)

        assertBalanceFigmaGeometry()
        saveNodeScreenshot("aircoin-balance-root", "aircoin_balance_figma_light.png")
        compose.onNodeWithContentDescription("AirCoin history").performClick()
        compose.runOnIdle {
            assertEquals(1, historyClicks.get())
        }
    }

    @Test
    fun balanceUsesFigmaGeometryDark() {
        setBalanceContent(ThemeController.Mode.DARK)

        assertBalanceFigmaGeometry()
        saveNodeScreenshot("aircoin-balance-root", "aircoin_balance_figma_dark.png")
    }

    @Test
    fun historyUsesFigmaLedgerGeometryAndCopyLight() {
        setHistoryContent(ThemeController.Mode.LIGHT)

        assertHistoryFigmaGeometryAndCopy()
        assertHistoryRenderedColors(ThemeController.Mode.LIGHT)
        saveNodeScreenshot("aircoin-history-root", "aircoin_history_figma_light.png")
    }

    @Test
    fun historyUsesFigmaLedgerGeometryAndCopyDark() {
        setHistoryContent(ThemeController.Mode.DARK)

        assertHistoryFigmaGeometryAndCopy()
        assertHistoryRenderedColors(ThemeController.Mode.DARK)
        saveNodeScreenshot("aircoin-history-root", "aircoin_history_figma_dark.png")
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
                                    amount = 25.0,
                                    referenceId = "#24242433",
                                    createdAt = "2025-10-25 12:30:00",
                                ),
                                AirCoinTransaction(
                                    id = 3,
                                    amount = 25.0,
                                    referenceId = "#24242433",
                                    createdAt = "2025-10-25",
                                ),
                                AirCoinTransaction(
                                    id = 4,
                                    amount = 25.0,
                                    referenceId = "#24242433",
                                    createdAt = "2025-10-25T13:14:00Z",
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

    private fun assertBalanceFigmaGeometry() {
        assertClose(325f, boundsHeight("aircoin-balance-hero-spacer"), "Balance hero spacer")
        assertClose(336f, boundsWidth("aircoin-balance-conversion-row"), "Conversion row width")
        assertClose(51f, boundsHeight("aircoin-balance-conversion-row"), "Conversion row height")
        assertClose(142.5f, boundsWidth("aircoin-balance-left-pill"), "Left conversion pill width")
        assertClose(51f, boundsHeight("aircoin-balance-left-pill"), "Left conversion pill height")
        assertClose(51f, boundsWidth("aircoin-balance-arrow-circle"), "Arrow circle width")
        assertClose(51f, boundsHeight("aircoin-balance-arrow-circle"), "Arrow circle height")
        assertClose(142.5f, boundsWidth("aircoin-balance-right-pill"), "Right conversion pill width")
        assertClose(51f, boundsHeight("aircoin-balance-right-pill"), "Right conversion pill height")
        assertClose(336f, boundsWidth("aircoin-balance-stats-card"), "Stats card width")
        assertClose(170f, boundsHeight("aircoin-balance-stats-card"), "Stats card height")
        assertClose(40f, boundsHeight("aircoin-stat-accumulated"), "Accumulated row height")
        assertClose(40f, boundsHeight("aircoin-stat-redeemed"), "Redeemed row height")
        assertClose(40f, boundsHeight("aircoin-stat-available"), "Available row height")
        assertClose(336f, boundsWidth("aircoin-balance-tip-card"), "Tip card width")
        assertClose(82f, boundsHeight("aircoin-balance-tip-card"), "Tip card height")
        assertClose(50f, boundsWidth("aircoin-balance-tip-icon"), "Tip icon width")
        assertClose(50f, boundsHeight("aircoin-balance-tip-icon"), "Tip icon height")
        assertAbsent("Redeem at counter")
    }

    private fun assertHistoryFigmaGeometryAndCopy() {
        val heroBounds = compose.onNodeWithTag("aircoin-history-hero-wrap", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cardBounds = compose.onNodeWithTag("aircoin-history-table-card", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertClose(332f, boundsHeight("aircoin-history-hero-wrap"), "History hero wrap height")
        assertClose(375f, boundsWidth("aircoin-history-hero-image"), "History hero image width")
        assertClose(332f, boundsHeight("aircoin-history-hero-image"), "History hero image height")
        assertClose(345f, boundsWidth("aircoin-history-table-card"), "History table card width")
        assertClose(206f, boundsHeight("aircoin-history-table-card"), "History table card height")
        assertClose(43f, boundsHeight("aircoin-history-header-row"), "History header row height")
        assertClose(40f, boundsHeight("aircoin-history-row-0"), "History body row height")
        assertClose(1f, boundsHeight("aircoin-history-divider-1"), "History body divider height")
        assertClose(
            -55f,
            (cardBounds.top - heroBounds.bottom).value,
            "Swift History hero/table overlap",
        )

        compose.onNodeWithText("Invoice No.").assertIsDisplayed()
        compose.onNodeWithText("Air Coin Used").assertIsDisplayed()
        compose.onNodeWithText("Date").assertIsDisplayed()
        assertEquals(4, compose.onAllNodesWithText("25 Oct 2025").fetchSemanticsNodes().size)
        assertEquals(4, compose.onAllNodesWithText("25").fetchSemanticsNodes().size)
        assertAbsent("Invoice No")
        assertAbsent("Used Date")
        assertAbsent("25Oct 2025")
        assertEquals(0, compose.onAllNodesWithText("+25").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("-25").fetchSemanticsNodes().size)
    }

    private fun assertHistoryRenderedColors(mode: ThemeController.Mode) {
        val colors = if (mode == ThemeController.Mode.DARK) darkAirdropColors else lightAirdropColors
        val root = compose.onNodeWithTag("aircoin-history-root").captureToImage().asAndroidBitmap()
        val card = compose.onNodeWithTag("aircoin-history-table-card").captureToImage().asAndroidBitmap()

        assertPixelNear(
            bitmap = root,
            xDp = 8f,
            yDp = 790f,
            expectedArgb = colors.gray100.toArgb(),
            label = "$mode History lower page surface",
        )
        assertPixelNear(
            bitmap = card,
            xDp = 8f,
            yDp = 24f,
            expectedArgb = colors.gray200.toArgb(),
            label = "$mode History ledger header fill",
        )
        assertPixelNear(
            bitmap = card,
            xDp = 8f,
            yDp = 72f,
            expectedArgb = colors.gray100.toArgb(),
            label = "$mode History ledger body fill",
        )
        assertHairlineEdge(
            bitmap = card,
            expectedArgb = colors.cardHairline.toArgb(),
            label = "$mode History ledger hairline",
        )
    }

    private fun assertPixelNear(
        bitmap: Bitmap,
        xDp: Float,
        yDp: Float,
        expectedArgb: Int,
        label: String,
    ) {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
            .density
        val x = (xDp * density).toInt().coerceIn(0, bitmap.width - 1)
        val y = (yDp * density).toInt().coerceIn(0, bitmap.height - 1)
        val actual = bitmap.getPixel(x, y)
        assertTrue(
            "$label expected=${expectedArgb.toUInt().toString(16)} " +
                "actual=${actual.toUInt().toString(16)} at=($x,$y)",
            actual.isNear(expectedArgb),
        )
    }

    private fun assertHairlineEdge(bitmap: Bitmap, expectedArgb: Int, label: String) {
        val inset = bitmap.height / 5
        val edgeBand = minOf(2, bitmap.width / 4).coerceAtLeast(1)
        var matches = 0
        for (y in inset until bitmap.height - inset) {
            for (x in 0 until edgeBand) {
                if (bitmap.getPixel(x, y).isNear(expectedArgb)) matches += 1
                if (bitmap.getPixel(bitmap.width - 1 - x, y).isNear(expectedArgb)) matches += 1
            }
        }
        assertTrue("$label must render on both straight edges; matches=$matches", matches >= 20)
    }

    private fun Int.isNear(target: Int, tolerance: Int = 4): Boolean =
        abs(((this shr 24) and 0xFF) - ((target shr 24) and 0xFF)) <= tolerance &&
            abs(((this shr 16) and 0xFF) - ((target shr 16) and 0xFF)) <= tolerance &&
            abs(((this shr 8) and 0xFF) - ((target shr 8) and 0xFF)) <= tolerance &&
            abs((this and 0xFF) - (target and 0xFF)) <= tolerance

    private fun saveNodeScreenshot(tag: String, filename: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/aircoins_figma").also { it.mkdirs() }
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
        assertEquals(label, expected, actual, 2f)
    }

    private fun assertAbsent(text: String) {
        assertEquals(0, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
    }
}
