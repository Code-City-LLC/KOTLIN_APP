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
 * Swift results-VC initializer receives. Results/GovCharges pop themselves if
 * that entry (and thus the result) is gone.
 *
 * The results-screen disclaimer link navigates to Government Charges (Figma
 * 40001817:20681), which shows the customs-duty-inclusive breakdown. Figma
 * ships that screen even though Swift does not — Figma is the visual source of
 * truth, so the destination is live.
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
        CalculatorResultsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDropAlert = { navController.navigate(Routes.DROP_ALERT) },
            onMakePayment = { navController.navigate(Routes.CART) },
            onGovernmentCharges = { navController.navigate(Routes.CALCULATOR_GOVERNMENT_CHARGES) },
        )
    }

    composable(Routes.CALCULATOR_GOVERNMENT_CHARGES) { entry ->
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
