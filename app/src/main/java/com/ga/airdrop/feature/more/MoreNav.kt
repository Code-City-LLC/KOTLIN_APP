package com.ga.airdrop.feature.more

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.auth.resetToSignedOutRoute

/**
 * More feature group, part 1 — hub, Settings, Profile, Documents,
 * Preferences, Notification Settings, Background Images, Payment Methods.
 *
 * Destinations still owned elsewhere:
 * REFER_A_FRIEND / PROMOTIONS / AUTHORIZED_USERS / SHIPPING_RATES /
 * RESTRICTED_ITEMS / FAQ / TERMS / PRIVACY / ACCOUNT_DELETION (feature/more2),
 * INVOICE_VIEWER (shipments), CART (shop).
 */
fun NavGraphBuilder.moreGraph(navController: NavHostController) {
    composable(Routes.MORE) {
        MoreScreen(onNavigate = { navController.navigate(it) })
    }
    composable(Routes.SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
            // React Native order (Kemar 2026-10-17): SettingsView.onLogout
            // resets the stack to OnboardingView, and the slides end on the
            // auth landing. Swift (FigmaSpecificPages.swift:1737) root-swaps to
            // the Login form instead, and Kotlin used to follow it, playing
            // onboarding only after the next login. The logout armed
            // OnboardingStore before clearing the bearer, and finishing the
            // slides clears it, so they play once. AppRoot's token-loss reset
            // lands on the same route, and resetToSignedOutRoute keeps the
            // second of the two navigations from starting them over.
            onLoggedOut = { navController.resetToSignedOutRoute(Routes.ONBOARDING) },
        )
    }
    composable(Routes.PROFILE) {
        ProfileScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.DOCUMENTS) {
        DocumentsScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }
    composable(Routes.PREFERENCES) {
        PreferencesScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.NOTIFICATION_SETTINGS) {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.BACKGROUNDS) {
        BackgroundImagesScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.ACTIVE_SESSIONS) {
        ActiveSessionsScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.PAYMENT_METHODS) {
        PaymentMethodsScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }
}
