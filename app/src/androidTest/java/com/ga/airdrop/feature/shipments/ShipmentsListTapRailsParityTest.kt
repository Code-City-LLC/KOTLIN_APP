package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.cart.CartStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShipmentsListTapRailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()

    @Test
    fun packagesListCardAndCartToggleKeepSwiftRails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CartStore.clear()
        }

        try {
            val packageRepo = FakePackagesRepository()
            setContent {
                PackagesScreen(
                    onBack = {},
                    onNavigate = navigatedRoutes::add,
                    viewModel = PackagesViewModel(packageRepo, FakeHubRepository()),
                )
            }

            waitForText("Swift package 101")
            compose.onNodeWithTag("packages-list-cart-toggle-101", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.runOnIdle {
                assertEquals("Cart toggle should not navigate away from Packages", emptyList<String>(), navigatedRoutes)
                assertEquals(1, CartStore.count)
                assertEquals(101, CartStore.items.value.single().id)
            }

            compose.onNodeWithTag("packages-list-card-101").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.packageDetails("101"), navigatedRoutes.lastOrNull())
            }
        } finally {
            instrumentation.runOnMainSync { CartStore.clear() }
        }
    }

    @Test
    fun paymentsListCardsKeepPackageAndProductDetailRails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        setContent {
            PaymentsScreen(
                onBack = {},
                onNavigate = navigatedRoutes::add,
                viewModel = PaymentsViewModel(FakePaymentsRepository()),
            )
        }

        waitForText("Package payment")
        compose.onNodeWithTag("payments-list-card-201").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Routes.paymentPackageDetails("201"), navigatedRoutes.lastOrNull())
        }

        compose.onNodeWithTag("payments-list-card-202").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Routes.productPaymentDetails("202"), navigatedRoutes.lastOrNull())
        }
    }

    @Test
    fun ordersListCardKeepsSwiftOrderDetailRail() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        setContent {
            OrdersScreen(
                onBack = {},
                onNavigate = navigatedRoutes::add,
                viewModel = OrdersViewModel(FakeOrdersRepository()),
            )
        }

        waitForText("Swift order 301")
        compose.onNodeWithTag("orders-list-card-301").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Routes.orderDetails("301"), navigatedRoutes.lastOrNull())
        }
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        navigatedRoutes.clear()
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    content()
                }
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private class FakeHubRepository : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(160.625)
        override suspend fun summary() = Result.success(ShipmentsSummary())
        override suspend fun packagesShortlist() = Result.success(emptyList<ShipmentPackage>())
        override suspend fun paymentsShortlist() = Result.success(emptyList<ShipmentPayment>())
        override suspend fun ordersShortlist() = Result.success(emptyList<ShipmentOrder>())
    }

    private class FakePackagesRepository : ShipmentsPackagesRepository {
        override suspend fun packages(
            page: Int,
            perPage: Int,
            status: Int?,
            search: String?,
        ) = Result.success(listOf(samplePackage()))

        override suspend fun packageDetails(packageId: String) =
            Result.failure<ShipmentPackageDetail>(UnsupportedOperationException())

        override suspend fun packageStatuses() = Result.success(ShipmentStatusCatalog.defaults)

        override suspend fun uploadInvoices(packageId: String, files: List<InvoiceUploadFile>) =
            Result.failure<Unit>(UnsupportedOperationException())

        override suspend fun deleteInvoice(packageId: String, invoiceId: Int) =
            Result.failure<Unit>(UnsupportedOperationException())
    }

    private class FakePaymentsRepository : ShipmentsPaymentsRepository {
        override suspend fun payments(
            page: Int,
            perPage: Int,
            type: String?,
            search: String?,
        ) = Result.success(
            listOf(
                samplePayment(id = 201, type = "package", description = "Package payment"),
                samplePayment(id = 202, type = "product", description = "Product payment"),
            )
        )

        override suspend fun payment(paymentId: Int) = Result.success(samplePayment(paymentId))

        override suspend fun paymentInvoiceUrl(paymentId: Int) =
            Result.success("https://example.test/invoice-$paymentId.pdf")
    }

    private class FakeOrdersRepository : ShipmentsOrdersRepository {
        override suspend fun orders(page: Int, perPage: Int, search: String?) =
            Result.success(listOf(sampleOrder()))

        override suspend fun orderDetails(orderId: Int) = Result.success(sampleOrder(orderId))

        override suspend fun exchangeRate() = Result.success(160.625)
    }

    private companion object {
        fun samplePackage() = ShipmentPackage(
            id = 101,
            description = "Swift package 101",
            weightLbs = 1.3,
            statusName = "Ready for Pick-Up",
            shippingMethod = "Standard",
            additionalChargesTotal = 50.0,
        )

        fun samplePayment(
            id: Int,
            type: String = "package",
            description: String = "Package payment",
        ) = ShipmentPayment(
            id = id,
            invoiceId = "INV-$id",
            paymentType = type,
            totalAmount = 50.0,
            trackingCode = "ARD$id",
            paymentDate = "2024-01-12T15:14:00Z",
            packageId = 101,
            orderId = 301,
            packageDescription = description,
        )

        fun sampleOrder(orderId: Int = 301) = ShipmentOrder(
            id = orderId,
            orderNumber = "ORD-$orderId",
            title = "Swift order $orderId",
            status = "pending",
            orderStatus = "processing",
            invoiceAmountUsd = 403.35,
        )
    }
}
