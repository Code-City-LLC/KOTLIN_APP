package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
class PaymentsOrdersParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()

    @Test
    fun paymentsListUsesSwiftHeaderDownloadAndFilterLight() {
        setPaymentsContent(ThemeController.Mode.LIGHT, FakePaymentsRepository())

        assertPaymentsSwiftChrome()
        saveRootScreenshot("payments_swift_light.png")
    }

    @Test
    fun paymentsListUsesSwiftHeaderDownloadAndFilterDark() {
        setPaymentsContent(ThemeController.Mode.DARK, FakePaymentsRepository())

        assertPaymentsSwiftChrome()
        saveRootScreenshot("payments_swift_dark.png")
    }

    @Test
    fun paymentsListLoadFailureRendersEmptyStateWithoutModal() {
        setPaymentsContent(
            mode = ThemeController.Mode.LIGHT,
            repo = FakePaymentsRepository(
                paymentsResult = Result.failure(IllegalStateException("list offline")),
            ),
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("No payments found").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No payments found").assertIsDisplayed()
        assertTextMissing("Download failed")
        assertTextMissing("list offline")
        assertTextMissing("OK")
    }

    @Test
    fun invoiceDownloadFailureShowsSwiftAlertTitle() {
        setPaymentsContent(
            mode = ThemeController.Mode.LIGHT,
            repo = FakePaymentsRepository(
                invoiceResult = Result.failure(IllegalStateException("invoice denied")),
            ),
        )

        compose.onNodeWithContentDescription("Download invoice").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Download failed").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Download failed").assertIsDisplayed()
        compose.onNodeWithText("invoice denied").assertIsDisplayed()
        assertTrue("Invoice failure should stay on the Payments screen", navigatedRoutes.isEmpty())

        compose.onNodeWithText("OK").performClick()
        assertTextMissing("Download failed")
    }

    @Test
    fun ordersHeaderUsesSwiftFigmaAccessoryLight() {
        setOrdersContent(ThemeController.Mode.LIGHT, FakeOrdersRepository())

        assertOrdersSwiftChrome()
        saveRootScreenshot("orders_swift_light.png")
    }

    @Test
    fun ordersHeaderUsesSwiftFigmaAccessoryDark() {
        setOrdersContent(ThemeController.Mode.DARK, FakeOrdersRepository())

        assertOrdersSwiftChrome()
        saveRootScreenshot("orders_swift_dark.png")
    }

    private fun setPaymentsContent(
        mode: ThemeController.Mode,
        repo: FakePaymentsRepository,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        navigatedRoutes.clear()
        val viewModel = PaymentsViewModel(repo)
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    PaymentsScreen(
                        onBack = {},
                        onNavigate = navigatedRoutes::add,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Payments").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun setOrdersContent(
        mode: ThemeController.Mode,
        repo: FakeOrdersRepository,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        val viewModel = OrdersViewModel(repo)
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray100)
                ) {
                    OrdersScreen(
                        onBack = {},
                        onNavigate = navigatedRoutes::add,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Studio Monitor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertPaymentsSwiftChrome() {
        compose.onNodeWithText("Payments").assertIsDisplayed()
        compose.onNodeWithTag("payments-header-more").assertIsDisplayed()
        compose.onNodeWithText("Search by payment description").assertIsDisplayed()
        compose.onNodeWithText("INV-900").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download invoice").assertIsDisplayed()

        compose.onNodeWithTag("payments-header-more").performClick()
        compose.onNodeWithText("Filter payments").assertIsDisplayed()
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithText("Package").assertIsDisplayed()
        compose.onNodeWithText("Product").assertIsDisplayed()
        compose.onNodeWithText("All").performClick()
        compose.waitForIdle()
    }

    private fun assertOrdersSwiftChrome() {
        compose.onNodeWithText("Orders").assertIsDisplayed()
        compose.onNodeWithTag("orders-header-more").assertIsDisplayed()
        compose.onNodeWithText("Search by Order Description").assertIsDisplayed()
        compose.onNodeWithText("Studio Monitor").assertIsDisplayed()
        compose.onNodeWithText("Processing").assertIsDisplayed()
    }

    private class FakePaymentsRepository(
        private val payment: ShipmentPayment = samplePayment(),
        private val paymentsResult: Result<List<ShipmentPayment>> = Result.success(listOf(payment)),
        private val invoiceResult: Result<String> = Result.success("https://example.test/invoice.pdf"),
    ) : ShipmentsPaymentsRepository {
        override suspend fun payments(page: Int, perPage: Int, type: String?, search: String?) =
            paymentsResult

        override suspend fun payment(paymentId: Int) = Result.success(payment)

        override suspend fun paymentInvoiceUrl(paymentId: Int): Result<String> {
            assertEquals(payment.id, paymentId)
            return invoiceResult
        }
    }

    private class FakeOrdersRepository(
        private val order: ShipmentOrder = sampleOrder(),
    ) : ShipmentsOrdersRepository {
        override suspend fun orders(page: Int, perPage: Int, search: String?) =
            Result.success(listOf(order))

        override suspend fun orderDetails(orderId: Int) = Result.success(order)

        override suspend fun exchangeRate() = Result.success(161.0)
    }

    private companion object {
        fun samplePayment() = ShipmentPayment(
            id = 900,
            invoiceId = "INV-900",
            paymentType = "package",
            method = "card",
            totalAmount = 42.5,
            trackingCode = "AR000000043525",
            paymentDate = "2024-01-12T15:14:00Z",
            packageId = 7,
            packageDescription = "Plastic phone case",
        )

        fun sampleOrder() = ShipmentOrder(
            id = 900,
            orderNumber = "ORD-900",
            title = "Studio Monitor",
            status = "pending",
            orderStatus = "processing",
            createdAt = "2024-01-12T15:14:00Z",
            customerName = "Best Buy",
            weightLbs = 2.5,
            invoiceAmountUsd = 403.35,
            exchangeRate = 161.0,
        )
    }

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
            "screenshots/payments_orders",
        )
        dir.mkdirs()
        return dir
    }

    private fun assertTextMissing(text: String) {
        assertEquals(0, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
    }
}
