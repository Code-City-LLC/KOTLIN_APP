package com.ga.airdrop.feature.more2

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.shop.ShopProductHandoffStore
import com.ga.airdrop.feature.shop.launchExternalUrl
import com.ga.airdrop.feature.shop.routeSlug

/**
 * MORE feature group, part 2 — registration for the orchestrator's mainGraph:
 *
 *     more2Graph(navController)
 *
 * Registers AUTHORIZED_USERS, ADD_AUTHORIZED_USER (editId arg),
 * AUTHORIZED_USER_DETAIL, REFER_A_FRIEND, INVITE_FRIEND, REFERRED_FRIENDS,
 * PROMOTIONS, SHIPPING_RATES, TERMS, PRIVACY, FAQ, RESTRICTED_ITEMS,
 * ACCOUNT_DELETION and ACCOUNT_DELETION_REASON.
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
        // Swift FigmaAuthorizedUserDetailViewController.swift:131-141 exposes an
        // Edit button (directive outranks RN parity) → the prefilled editor.
        AuthorizedUserDetailScreen(
            userId = userId,
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.addAuthorizedUser(userId.toString())) },
        )
    }

    composable(Routes.REFER_A_FRIEND) {
        ReferAFriendScreen(
            onBack = { navController.popBackStack() },
            onInviteFriend = { navController.navigate(Routes.INVITE_FRIEND) },
        )
    }

    composable(Routes.INVITE_FRIEND) {
        InviteFriendScreen(
            onBack = { navController.popBackStack() },
            onSaved = {
                navController.popBackStack()
            },
            onViewReferralHistory = {
                navController.navigate(Routes.REFERRED_FRIENDS)
            },
        )
    }

    composable(Routes.REFERRED_FRIENDS) {
        ReferredFriendsScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.PROMOTIONS) {
        val context = LocalContext.current
        PromotionsScreen(
            onBack = { navController.popBackStack() },
            onOpenAmazon = { url -> launchExternalUrl(context, url) },
            onOpenSale = { product ->
                ShopProductHandoffStore.put(product)
                navController.navigate(
                    Routes.auctionProductDetails(product.routeSlug, featured = false),
                )
            },
        )
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
        FaqScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(Routes.RESTRICTED_ITEMS) {
        RestrictedItemsScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.ABOUT) {
        AboutScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }

    composable(Routes.ACCOUNT_DELETION) {
        AccountDeletionScreen(
            onBack = { navController.popBackStack() },
            onVerified = { navController.navigate(Routes.ACCOUNT_DELETION_REASON) },
        )
    }

    composable(Routes.ACTIVE_SESSIONS) {
        ActiveSessionsScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.ACCOUNT_DELETION_REASON) {
        AccountDeletionReasonScreen(
            onBack = { navController.popBackStack() },
            onVerificationMissing = {
                navController.navigate(Routes.ACCOUNT_DELETION) {
                    popUpTo(Routes.ACCOUNT_DELETION_REASON) { inclusive = true }
                    launchSingleTop = true
                }
            },
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
