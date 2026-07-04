package com.ga.airdrop.feature.more2

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ga.airdrop.core.navigation.Routes

/**
 * MORE feature group, part 2 — registration for the orchestrator's mainGraph:
 *
 *     more2Graph(navController)
 *
 * Registers AUTHORIZED_USERS, ADD_AUTHORIZED_USER (editId arg),
 * AUTHORIZED_USER_DETAIL, REFER_A_FRIEND, INVITE_FRIEND, PROMOTIONS,
 * SHIPPING_RATES, TERMS, PRIVACY, FAQ, RESTRICTED_ITEMS, ACCOUNT_DELETION and
 * ACCOUNT_DELETION_REASON. No new route constants needed — all exist in
 * Routes.kt already (the restricted-items info variant is internal state, not
 * a route).
 */
fun NavGraphBuilder.more2Graph(navController: NavHostController) {

    composable(Routes.AUTHORIZED_USERS) {
        AuthorizedUsersScreen(
            onBack = { navController.popBackStack() },
            onAddUser = { navController.navigate(Routes.addAuthorizedUser()) },
            onOpenDetail = { id ->
                navController.navigate(Routes.authorizedUserDetail(id.toString()))
            },
        )
    }

    composable(
        route = Routes.ADD_AUTHORIZED_USER,
        arguments = listOf(
            navArgument("editId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        val editId = entry.arguments?.getString("editId")
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
        AddAuthorizedUserScreen(
            editId = editId,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = Routes.AUTHORIZED_USER_DETAIL,
        arguments = listOf(navArgument("userId") { type = NavType.StringType }),
    ) { entry ->
        val userId = entry.arguments?.getString("userId")?.toIntOrNull() ?: 0
        // Swift FigmaAuthorizedUserDetailViewController.swift:146-148 — the
        // detail surface is read-only (Activate/Deactivate + Delete only); no
        // Edit affordance. Matches RN; no iOS-only edit button.
        AuthorizedUserDetailScreen(
            userId = userId,
            onBack = { navController.popBackStack() },
        )
    }

    composable(Routes.REFER_A_FRIEND) {
        ReferAFriendScreen(
            onBack = { navController.popBackStack() },
            onInviteFriend = { navController.navigate(Routes.INVITE_FRIEND) },
        )
    }

    composable(Routes.INVITE_FRIEND) {
        InviteFriendScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.PROMOTIONS) {
        PromotionsScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.SHIPPING_RATES) {
        ShippingRatesScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(Routes.TERMS) {
        TermsScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.PRIVACY) {
        PrivacyPolicyScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.FAQ) {
        FaqScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.RESTRICTED_ITEMS) {
        RestrictedItemsScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.ACCOUNT_DELETION) {
        AccountDeletionScreen(
            onBack = { navController.popBackStack() },
            onVerified = { navController.navigate(Routes.ACCOUNT_DELETION_REASON) },
        )
    }

    composable(Routes.ACCOUNT_DELETION_REASON) {
        AccountDeletionReasonScreen(
            onBack = { navController.popBackStack() },
            onDeleted = {
                // Swift FigmaAccountDeletionReasonViewController.swift:422 —
                // completeDeactivation root-swaps to FigmaLoginViewController
                // (Login form), exactly like logout.
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
    }
}
