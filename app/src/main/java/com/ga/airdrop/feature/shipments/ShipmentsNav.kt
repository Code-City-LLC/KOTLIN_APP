package com.ga.airdrop.feature.shipments

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ga.airdrop.core.navigation.Routes

/**
 * SHIPMENTS feature graph. The orchestrator wires this into
 * core/navigation/AppRoot.kt mainGraph:
 *
 *     shipmentsGraph(navController)
 *
 * (replacing the `composable(Routes.SHIPMENTS) { PlaceholderScreen(...) }`
 * stub). All routes already exist in Routes.kt — no new constants needed.
 */
fun NavGraphBuilder.shipmentsGraph(navController: NavHostController) {

    // Tab root — Shipments hub.
    composable(Routes.SHIPMENTS) {
        ShipmentsScreen(onNavigate = { navController.navigate(it) })
    }

    composable(Routes.PACKAGES) {
        PackagesScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(
        route = Routes.PACKAGE_DETAILS,
        arguments = listOf(navArgument("packageId") { type = NavType.StringType }),
    ) { entry ->
        PackageDetailsScreen(
            packageId = entry.arguments?.getString("packageId").orEmpty(),
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(Routes.PAYMENTS) {
        PaymentsScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(
        route = Routes.PAYMENT_PACKAGE_DETAILS,
        arguments = listOf(navArgument("paymentId") { type = NavType.StringType }),
    ) { entry ->
        PaymentPackageDetailsScreen(
            paymentId = entry.arguments?.getString("paymentId").orEmpty(),
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = Routes.PRODUCT_PAYMENT_DETAILS,
        arguments = listOf(navArgument("paymentId") { type = NavType.StringType }),
    ) { entry ->
        ProductPaymentDetailsScreen(
            paymentId = entry.arguments?.getString("paymentId").orEmpty(),
            onBack = { navController.popBackStack() },
        )
    }

    composable(Routes.ORDERS) {
        OrdersScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(
        route = Routes.ORDER_DETAILS,
        arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
    ) { entry ->
        OrderDetailsScreen(
            orderId = entry.arguments?.getString("orderId").orEmpty(),
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = Routes.INVOICE_VIEWER,
        arguments = listOf(
            navArgument("url") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("title") {
                type = NavType.StringType
                defaultValue = "Invoice"
            },
        ),
    ) { entry ->
        InvoiceViewerScreen(
            url = entry.arguments?.getString("url").orEmpty(),
            title = entry.arguments?.getString("title") ?: "Invoice",
            onBack = { navController.popBackStack() },
        )
    }
}
