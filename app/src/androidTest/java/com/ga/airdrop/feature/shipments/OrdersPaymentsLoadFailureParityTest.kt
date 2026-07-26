package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import org.junit.Rule
import org.junit.Test

/**
 * Orders and Payments must not report a FAILED load as "you have none".
 *
 * ⚠️ BOTH SCREENS SWALLOWED THE FAILURE ON PURPOSE, EACH CITING SWIFT PARITY —
 * AND BOTH COMMENTS WERE FALSE.
 *
 * Orders said: "Swift parity: list-load failures fall through to the empty/list
 * state (the old `error` write was never read by OrdersScreen)". Payments said
 * Swift "prints list-load failures and lets the empty/list state render".
 *
 * Swift does the opposite on both:
 *   FigmaOrdersViewController:134    — a failure gets "distinct copy + Retry",
 *                                      separate from "No orders yet"
 *   FigmaPaymentsViewController:329  — emptyLabel.text = loadErrorMessage ?? "No payments found"
 *   FigmaPaymentsViewController:663  — "Couldn't load payments.\nPull down to retry."
 *
 * The Orders comment contained a true observation — the `error` write really
 * was never read by the screen — and drew exactly the wrong conclusion from it.
 * The screen not reading the value was the defect; deleting the write made a
 * failed load tell the customer they had never placed an order.
 *
 * These are pinned on device because both defects were invisible to the
 * ViewModel tests: the state was right, only the render was wrong.
 */
class OrdersPaymentsLoadFailureParityTest {

    @get:Rule
    val compose = createComposeRule()

    private class FailingOrdersRepository : ShipmentsOrdersRepository {
        override suspend fun orders(
            page: Int,
            perPage: Int,
            search: String?,
        ): Result<Paged<ShipmentOrder>> = Result.failure(java.io.IOException("unreachable"))

        override suspend fun orderDetails(orderId: Int): Result<ShipmentOrder> =
            Result.failure(UnsupportedOperationException())

        override suspend fun exchangeRate(): Result<Double> = Result.success(161.0)
    }

    private class FailingPaymentsRepository : ShipmentsPaymentsRepository {
        override suspend fun payments(
            page: Int,
            perPage: Int,
            type: String?,
            search: String?,
        ): Result<Paged<ShipmentPayment>> = Result.failure(java.io.IOException("unreachable"))

        override suspend fun payment(paymentId: Int, refresh: Boolean): Result<ShipmentPayment> =
            Result.failure(UnsupportedOperationException())

        override suspend fun paymentInvoiceUrl(paymentId: Int): Result<String> =
            Result.failure(UnsupportedOperationException())
    }

    @Test
    fun aFailedOrdersLoadIsNotReportedAsHavingNeverOrdered() {
        val viewModel = OrdersViewModel(repo = FailingOrdersRepository())

        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    OrdersScreen(onBack = {}, onNavigate = {}, viewModel = viewModel)
                }
            }
        }

        compose.waitUntil(5_000) { !viewModel.state.value.loading }
        compose.waitForIdle()

        compose.onNodeWithTag("orders-load-error").assertIsDisplayed()
        compose.onNodeWithText("No orders found").assertDoesNotExist()
    }

    @Test
    fun aFailedPaymentsLoadIsNotReportedAsHavingNoPaymentHistory() {
        val viewModel = PaymentsViewModel(repo = FailingPaymentsRepository())

        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    PaymentsScreen(onBack = {}, onNavigate = {}, viewModel = viewModel)
                }
            }
        }

        compose.waitUntil(5_000) { !viewModel.state.value.loading }
        compose.waitForIdle()

        compose.onNodeWithTag("payments-load-error").assertIsDisplayed()
        compose.onNodeWithText("No payments found").assertDoesNotExist()
    }
}
