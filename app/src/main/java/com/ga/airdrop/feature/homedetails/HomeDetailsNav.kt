package com.ga.airdrop.feature.homedetails

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ga.airdrop.core.navigation.Routes

/**
 * Home drill-down destinations. Registered from AppRoot's mainGraph:
 *   homeDetailsGraph(navController)
 *
 * WAREHOUSES additionally accepts an optional `?type=` arg
 * ("standard" | "seadrop" | "express") so callers can preselect a tab, the
 * way Swift routes WarehouseView with a `detail` payload; plain
 * navigate(Routes.WAREHOUSES) still works (defaults to Standard).
 *
 * AirCoin transaction ledger is pushed from the balance page's top-right icon
 * (Swift FigmaAirCoinTransactionsViewController / RN AirCoinHistoryView).
 */
fun NavGraphBuilder.homeDetailsGraph(navController: NavHostController) {
    composable(
        route = Routes.WAREHOUSES + "?type={type}",
        arguments = listOf(
            navArgument("type") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
    ) { entry ->
        WarehousesScreen(
            onBack = { navController.popBackStack() },
            initialType = entry.arguments?.getString("type"),
        )
    }

    composable(Routes.SERVICES) {
        ServicesScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.SALES_TAXES) {
        SalesTaxesScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.GOLD_PRIORITY) {
        GoldPriorityScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.NOTIFICATIONS) {
        NotificationsScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(Routes.AIRCOIN_HISTORY) {
        AirCoinBalanceScreen(
            onBack = { navController.popBackStack() },
            onOpenHistory = { navController.navigate(Routes.AIRCOIN_HISTORY_DETAIL) },
        )
    }

    composable(Routes.AIRCOIN_HISTORY_DETAIL) {
        AirCoinHistoryDetailScreen(onBack = { navController.popBackStack() })
    }
}
