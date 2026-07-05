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
class ShipmentsHubTapRailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()

    @Test
    fun hubSummaryCardsViewMoreCardsAndCartToggleKeepSwiftRails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
            CartStore.init(context)
            CartStore.clear()
        }

        try {
            setShipmentsContent(FakeHubRepository())

            listOf(
                "shipments-summary-track-shipment" to Routes.PACKAGES,
                "shipments-summary-packages" to Routes.PACKAGES,
                "shipments-summary-payments" to Routes.PAYMENTS,
                "shipments-summary-orders" to Routes.ORDERS,
            ).forEach { (tag, route) ->
                compose.onNodeWithTag(tag).performClick()
                compose.runOnIdle {
                    assertEquals(route, navigatedRoutes.lastOrNull())
                }
            }

            val routeCountBeforeCart = navigatedRoutes.size
            compose.onNodeWithTag("shipments-package-cart-toggle-101")
                .performClick()
            compose.runOnIdle {
                assertEquals(routeCountBeforeCart, navigatedRoutes.size)
                assertEquals(1, CartStore.count)
                assertEquals(101, CartStore.items.value.single().id)
            }

            compose.onNodeWithTag("shipments-package-card-101").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.packageDetails("101"), navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-packages-view-more").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.PACKAGES, navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-orders-view-more").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.ORDERS, navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-payments-view-more").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.PAYMENTS, navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-payment-card-201").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.paymentPackageDetails("201"), navigatedRoutes.lastOrNull())
            }

            compose.onNodeWithTag("shipments-payment-card-202").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(Routes.productPaymentDetails("202"), navigatedRoutes.lastOrNull())
            }
        } finally {
            instrumentation.runOnMainSync {
                CartStore.clear()
            }
        }
    }

    @Test
    fun orderCardOpensSwiftDetailRoute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }

        setShipmentsContent(
            FakeHubRepository(
                packages = emptyList(),
                payments = emptyList(),
                orders = listOf(FakeHubRepository.sampleOrder()),
                readyText = "Apple 2023 MacBook Pro Laptop M3 chip",
            )
        )

        compose.onNodeWithTag("shipments-order-card-301").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Routes.orderDetails("301"), navigatedRoutes.lastOrNull())
        }
    }

    private fun setShipmentsContent(repo: FakeHubRepository) {
        navigatedRoutes.clear()
        val viewModel = ShipmentsViewModel(repo)
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    ShipmentsScreen(
                        onNavigate = navigatedRoutes::add,
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(repo.readyText).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private class FakeHubRepository(
        private val packages: List<ShipmentPackage> = listOf(samplePackage()),
        private val payments: List<ShipmentPayment> = samplePayments(),
        private val orders: List<ShipmentOrder> = listOf(sampleOrder()),
        val readyText: String = "Ready for Pick-Up",
    ) : ShipmentsHubRepository {
        override suspend fun exchangeRate() = Result.success(161.0)

        override suspend fun summary() = Result.success(
            ShipmentsSummary(
                totalShipments = 7,
                totalPackages = 34,
                totalPayments = 234,
                totalOrders = 3,
            )
        )

        override suspend fun packagesShortlist() = Result.success(packages)

        override suspend fun paymentsShortlist() = Result.success(payments)

        override suspend fun ordersShortlist() = Result.success(orders)

        companion object {
            fun samplePackage() =
                ShipmentPackage(
                    id = 101,
                    description = "scrubber/earpod",
                    weightLbs = 1.3,
                    statusName = "Ready for Pick-Up",
                    shippingMethod = "Standard",
                    additionalChargesTotal = 50.0,
                )

            fun samplePayments() = listOf(
                ShipmentPayment(
                    id = 201,
                    invoiceId = "INV-201",
                    paymentType = "package",
                    totalAmount = 50.0,
                    trackingCode = "ARD000000201",
                    paymentDate = "2024-01-12T15:14:00Z",
                    packageId = 101,
                    packageDescription = "Package payment",
                ),
                ShipmentPayment(
                    id = 202,
                    invoiceId = "INV-202",
                    paymentType = "product",
                    totalAmount = 1550.0,
                    trackingCode = "ARD000000202",
                    paymentDate = "2024-01-13T15:14:00Z",
                    orderId = 301,
                    packageDescription = "Product payment",
                ),
            )

            fun sampleOrder() =
                ShipmentOrder(
                    id = 301,
                    orderNumber = "ORD-301",
                    title = "Apple 2023 MacBook Pro Laptop M3 chip",
                    status = "pending",
                    orderStatus = "order placed",
                    invoiceAmountUsd = 1550.0,
                )
        }
    }
}
