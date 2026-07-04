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
 * ORCHESTRATOR NOTES:
 *  • Call `calculatorGraph(navController)` from core/navigation/AppRoot.kt mainGraph.
 *  • NEW route constant needed in Routes.kt:
 *      const val CALCULATOR_GOVERNMENT_CHARGES = "calculatorGovernmentCharges"
 *    (registered here with the same literal; the deep-link table does not use it).
 *
 * The calculation result travels via [CalculatorViewModel] scoped to the
 * Routes.CALCULATOR back-stack entry (no serialization) — the same values the
 * Swift results-VC initializer receives. Results/GovCharges pop themselves if
 * that entry (and thus the result) is gone.
 */
const val CALCULATOR_GOVERNMENT_CHARGES = "calculatorGovernmentCharges"

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
        CalculatorResultsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onGovernmentCharges = { navController.navigate(CALCULATOR_GOVERNMENT_CHARGES) },
            onDropAlert = { navController.navigate(Routes.DROP_ALERT) },
            onMakePayment = { navController.navigate(Routes.CART) },
        )
    }

    composable(CALCULATOR_GOVERNMENT_CHARGES) { entry ->
        val parentEntry = remember(entry) { navController.getBackStackEntry(Routes.CALCULATOR) }
        val viewModel: CalculatorViewModel = viewModel(parentEntry)
        GovernmentChargesScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onBackToCalculator = {
                navController.popBackStack(route = Routes.CALCULATOR, inclusive = false)
            },
            onRestrictedItems = { navController.navigate(Routes.RESTRICTED_ITEMS) },
        )
    }
}
