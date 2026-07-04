package com.ga.airdrop.feature.more

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ga.airdrop.core.navigation.Routes

/**
 * More-tab route constants that don't exist in Routes.kt yet.
 * RECONCILE (orchestrator): add `const val PAYMENT_METHODS = "paymentMethods"`
 * to Routes.kt — mirrored here so this feature compiles standalone.
 */
object MoreRoutes {
    const val PAYMENT_METHODS = "paymentMethods"
}

/**
 * More feature group, part 1 — hub, Settings, Profile, Documents,
 * Preferences, Notification Settings, Background Images, Payment Methods.
 *
 * RECONCILE (orchestrator): call `moreGraph(navController)` from the
 * mainGraph in core/navigation/AppRoot.kt and drop its `Routes.MORE`
 * PlaceholderScreen registration. Destinations still owned elsewhere:
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
            // Swift FigmaSpecificPages.swift:1737 — logout root-swaps the
            // window to FigmaLoginViewController (the Login form), stack reset.
            onLoggedOut = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            },
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
    composable(MoreRoutes.PAYMENT_METHODS) {
        PaymentMethodsScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigate(it) },
        )
    }
}
