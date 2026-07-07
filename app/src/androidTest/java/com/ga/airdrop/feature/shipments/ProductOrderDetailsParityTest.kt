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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductOrderDetailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun productPaymentUsesSwiftHeroAndUngroupedAmountsLight() {
        val orderRepo = setProductPaymentContent(ThemeController.Mode.LIGHT)

        saveRootScreenshot("product_payment_details_swift_light.png")
        assertProductPaymentSwiftParity()
        assertEquals(
            "Swift product payment details fetches /orders/{payment.id}, not payment.order_id/package_id",
            42,
            orderRepo.lastOrderDetailId.get(),
        )
    }

    @Test
    fun productPaymentUsesSwiftHeroAndUngroupedAmountsDark() {
        val orderRepo = setProductPaymentContent(ThemeController.Mode.DARK)

        saveRootScreenshot("product_payment_details_swift_dark.png")
        assertProductPaymentSwiftParity()
        assertEquals(
            "Swift product payment details fetches /orders/{payment.id}, not payment.order_id/package_id",
            42,
            orderRepo.lastOrderDetailId.get(),
        )
    }

    @Test
    fun orderDetailsUsesSwiftHeroAndPreservesGroupedTotalLight() {
        setOrderDetailsContent(ThemeController.Mode.LIGHT)

        saveRootScreenshot("order_details_swift_light.png")
        assertOrderDetailsSwiftParity()
    }

    @Test
    fun orderDetailsUsesSwiftHeroAndPreservesGroupedTotalDark() {
        setOrderDetailsContent(ThemeController.Mode.DARK)

        saveRootScreenshot("order_details_swift_dark.png")
        assertOrderDetailsSwiftParity()
    }

    private fun setProductPaymentContent(mode: ThemeController.Mode): FakeOrdersRepository {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        val payment = samplePayment()
        val order = sampleProductOrder()
        val orderRepo = FakeOrdersRepository(order)
        val viewModel = ProductPaymentDetailsViewModel(
            paymentId = payment.id.toString(),
            paymentsRepo = FakePaymentsRepository(payment),
            ordersRepo = orderRepo,
        )
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150)
                ) {
                    ProductPaymentDetailsScreen(
                        paymentId = payment.id.toString(),
                        onBack = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Product Summary").fetchSemanticsNodes().isNotEmpty()
        }
        return orderRepo
    }

    private fun setOrderDetailsContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        val order = sampleOrder()
        val viewModel = OrderDetailsViewModel(
            orderId = order.id.toString(),
            repo = FakeOrdersRepository(order),
        )
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray150)
                ) {
                    OrderDetailsScreen(
                        orderId = order.id.toString(),
                        onBack = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Order Summary").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertProductPaymentSwiftParity() {
        val wrap = compose.onNodeWithTag("product-payment-hero-wrap").getUnclippedBoundsInRoot()
        val image = compose.onNodeWithTag("product-payment-hero-image").getUnclippedBoundsInRoot()

        assertClose(375f, boundsWidth(wrap), "product payment hero wrap width")
        assertClose(219f, boundsHeight(wrap), "product payment Swift hero wrap height")
        assertClose(315f, boundsWidth(image), "product payment Swift image width after 30dp insets")
        assertClose(159f, boundsHeight(image), "product payment Swift image height")

        compose.onNodeWithText("Payment Summary")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Amount Paid")
            .performScrollTo()
            .assertIsDisplayed()
        assertTextExists("USD 100.00 / JMD 16100.00")
        assertTextExists("USD 1 = JMD 161.00")
        assertEquals(
            0,
            compose.onAllNodesWithText("USD 100.00 / JMD 16,100.00").fetchSemanticsNodes().size,
        )
        assertEquals("-", ShipmentsFormat.usdJmdPlainPositive(0.0, 161.0))
    }

    private fun assertOrderDetailsSwiftParity() {
        val wrap = compose.onNodeWithTag("order-details-hero-wrap").getUnclippedBoundsInRoot()
        val image = compose.onNodeWithTag("order-details-hero-image").getUnclippedBoundsInRoot()

        assertClose(375f, boundsWidth(wrap), "order details hero wrap width")
        assertClose(209f, boundsHeight(wrap), "order details Swift hero wrap height")
        assertClose(335f, boundsWidth(image), "order details Swift image width after 20dp insets")
        assertClose(169f, boundsHeight(image), "order details Swift image height")

        compose.onNodeWithText("Order Summary")
            .performScrollTo()
            .assertIsDisplayed()
        assertTextExists("USD 403.35 / JMD 64,939.35")
        assertEquals(
            0,
            compose.onAllNodesWithText("USD 403.35 / JMD 64939.35").fetchSemanticsNodes().size,
        )
    }

    private fun samplePayment() = ShipmentPayment(
        id = 42,
        invoiceId = "INV-100",
        paymentType = "product",
        method = "card",
        totalAmount = 100.0,
        // Deliberately different from payment.id. Swift ignores both fields for
        // this screen and fetches the order with the tapped payment id.
        orderId = 7,
        packageId = 7,
        exchangeRate = 161.0,
    )

    private fun sampleProductOrder() = ShipmentOrder(
        id = 42,
        orderNumber = "ORD-42",
        title = "Wireless Speaker",
        status = "pending",
        orderStatus = "processing",
        createdAt = "2024-01-12T15:14:00Z",
        customerName = "Amazon",
        invoiceAmountUsd = 100.0,
        paymentMethod = "card",
        invoiceId = "INV-100",
        productName = "Wireless Speaker",
        regularPriceUsd = 150.0,
        salePriceUsd = 100.0,
        purchasedAt = "2024-01-12T15:14:00Z",
        productStatus = "processing",
        exchangeRate = 161.0,
    )

    private fun sampleOrder() = ShipmentOrder(
        id = 8,
        orderNumber = "ORD-8",
        title = "Studio Monitor",
        status = "pending",
        orderStatus = "processing",
        createdAt = "2024-01-12T15:14:00Z",
        customerName = "Best Buy",
        weightLbs = 2.5,
        invoiceAmountUsd = 403.35,
        exchangeRate = 161.0,
    )

    private class FakePaymentsRepository(
        private val payment: ShipmentPayment,
    ) : ShipmentsPaymentsRepository {
        override suspend fun payments(page: Int, perPage: Int, type: String?, search: String?) =
            Result.success(Paged(listOf(payment)))

        override suspend fun payment(paymentId: Int) = Result.success(payment)

        override suspend fun paymentInvoiceUrl(paymentId: Int) =
            Result.success("https://example.test/invoice.pdf")
    }

    private class FakeOrdersRepository(
        private val order: ShipmentOrder,
    ) : ShipmentsOrdersRepository {
        val lastOrderDetailId = AtomicReference<Int>()

        override suspend fun orders(page: Int, perPage: Int, search: String?) =
            Result.success(Paged(listOf(order)))

        override suspend fun orderDetails(orderId: Int): Result<ShipmentOrder> {
            lastOrderDetailId.set(orderId)
            return Result.success(order)
        }

        override suspend fun exchangeRate() = Result.success(161.0)
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
            "screenshots/product_order_details",
        )
        dir.mkdirs()
        return dir
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected at least one visible node with text $text",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assertClose(expected: Float, actual: Float, label: String, tolerance: Float = 1.5f) {
        assertTrue("$label expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
