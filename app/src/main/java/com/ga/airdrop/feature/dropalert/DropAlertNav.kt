package com.ga.airdrop.feature.dropalert

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ga.airdrop.core.navigation.Routes

/**
 * Route registrations for the Drop Alert flow.
 *
 * ORCHESTRATOR NOTE: call `dropAlertGraph(navController)` from
 * core/navigation/AppRoot.kt mainGraph. No new route constants required —
 * Routes.DROP_ALERT already exists (push-notification deep-link parity).
 */
fun NavGraphBuilder.dropAlertGraph(navController: NavHostController) {
    composable(Routes.DROP_ALERT) { entry ->
        val viewModel: DropAlertViewModel = viewModel(entry)
        DropAlertScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }
}
