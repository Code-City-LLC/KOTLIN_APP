package com.ga.airdrop.feature.calculator

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ga.airdrop.core.navigation.Routes

/**
 * Route registrations for the Shipping Calculator flow.
 *
 * ORCHESTRATOR NOTE: call `calculatorGraph(navController)` from
 * core/navigation/AppRoot.kt mainGraph.
 *
 * The calculation result travels via [CalculatorViewModel] scoped to the
 * Routes.CALCULATOR back-stack entry (no serialization) — the same values the
 * Swift results-VC initializer receives. The results screen pops itself if
 * that entry (and thus the result) is gone.
 *
 * There is deliberately NO Government Charges destination: Swift ships no such
 * screen, and the results-screen disclaimer link is a dead label (Swift wins).
 */
fun NavGraphBuilder.calculatorGraph(navController: NavHostController) {
    composable(Routes.CALCULATOR) { entry ->
        val viewModel: CalculatorViewModel = viewModel(entry)
        CalculatorScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onShowResults = { navController.navigate(Routes.CALCULATOR_RESULTS) },
        )
    }

    composable(Routes.CALCULATOR_RESULTS) { entry ->
        val parentEntry = remember(entry) { navController.getBackStackEntry(Routes.CALCULATOR) }
        val viewModel: CalculatorViewModel = viewModel(parentEntry)
        // Swift results VC footer has only Drop Alert + Make Payment; the
        // disclaimer "Click the link" is a dead label (no navigation).
        CalculatorResultsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDropAlert = { navController.navigate(Routes.DROP_ALERT) },
            onMakePayment = { navController.navigate(Routes.CART) },
        )
    }
}
