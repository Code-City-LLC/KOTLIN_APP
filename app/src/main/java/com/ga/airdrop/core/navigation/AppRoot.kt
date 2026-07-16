package com.ga.airdrop.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.designsystem.components.AirdropBottomBar
import com.ga.airdrop.core.designsystem.components.AirdropTab
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.feature.auth.AuthLandingScreen
import com.ga.airdrop.feature.auth.LoginScreen
import com.ga.airdrop.feature.auth.OnboardingStore
import com.ga.airdrop.feature.auth.authenticatedEntryDestination
import com.ga.airdrop.feature.auth.authExtraGraph
import com.ga.airdrop.feature.calculator.calculatorGraph
import com.ga.airdrop.feature.dropalert.dropAlertGraph
import com.ga.airdrop.feature.home.HomeScreen
import com.ga.airdrop.feature.home.HomeViewModel
import com.ga.airdrop.feature.homedetails.homeDetailsGraph
import com.ga.airdrop.feature.more.moreGraph
import com.ga.airdrop.feature.more2.more2Graph
import com.ga.airdrop.feature.shipments.shipmentsGraph
import com.ga.airdrop.feature.shop.shopGraph

/** Routes where a null bearer is expected — exempt from reactive logout. */
private val AUTH_GRAPH_ROUTES = setOf(
    Routes.SPLASH,
    Routes.ONBOARDING,
    Routes.AUTH_LANDING,
    Routes.LOGIN,
    Routes.SIGN_UP,
    Routes.FORGOT_PASSWORD,
    Routes.REGISTRATION_SUCCESS,
)

/**
 * App entry. Mirrors the Swift SceneDelegate + FigmaRouteResolver:
 * token → Home tab shell, else auth landing. One shared back stack with the
 * 5 tab roots as destinations (same as iOS's single navigation stack); the
 * glass bottom bar overlays tab-root content.
 */
@Composable
fun AppRoot(
    homeViewModel: HomeViewModel? = null,
    navigationUnlocked: Boolean = true,
) {
    val navController = rememberNavController()
    val token by AuthTokenStore.tokenFlow.collectAsState()
    // Splash decides: token → Home, first run → Onboarding, else → Landing
    // (mirrors RN LaunchAppView + Swift SceneDelegate).
    val startDestination = Routes.SPLASH

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = when (currentRoute) {
        Routes.HOME -> AirdropTab.Home
        Routes.SHIPMENTS -> AirdropTab.Shipments
        Routes.SHOP -> AirdropTab.Shop
        // Swift keeps the tab bar (Shop active) on the Auction and Feature
        // Products full-list screens.
        Routes.AUCTION, Routes.FEATURED_PRODUCTS -> AirdropTab.Shop
        Routes.CONTACTS -> AirdropTab.Help
        Routes.MORE -> AirdropTab.More
        else -> null
    }

    // Push-notification deep links (route + referenceID) land here once the
    // graph is composed — mirrors FigmaRouteResolver deep-linking.
    val pendingPush by com.ga.airdrop.core.push.PushDeepLink.pending.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingPush, token, navigationUnlocked) {
        if (pendingPush != null && navigationUnlocked) {
            consumePendingPushIfUnlocked(navigationUnlocked, AuthTokenStore.snapshot())
                ?.let { navController.navigate(it) }
        }
    }

    // Reactive logout — Swift SceneDelegate.handleAPISessionInvalidated parity
    // (BUG_AUDIT C6): when the bearer disappears while the user is inside the
    // authenticated graph (401 sweep, failed foreground refresh, account
    // deletion), reset to the auth landing instead of leaving dead screens.
    // Auth-graph routes are exempt so splash routing and the explicit
    // logout/deletion navigations don't double-fire.
    androidx.compose.runtime.LaunchedEffect(token, currentRoute) {
        if (shouldResetToAuthLanding(token, currentRoute)) {
            navController.navigate(Routes.AUTH_LANDING) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray200)
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            authGraph(navController)
            mainGraph(navController, homeViewModel)
        }
        if (currentTab != null) {
            AirdropBottomBar(
                selected = currentTab,
                onSelect = { tab ->
                    if (tab != currentTab) {
                        navController.switchTab(tab)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

internal fun consumePendingPushIfUnlocked(
    navigationUnlocked: Boolean,
    snapshot: AuthTokenStore.Snapshot,
): String? = if (navigationUnlocked) {
    com.ga.airdrop.core.push.PushDeepLink.consume(snapshot)
} else {
    null
}

internal fun shouldResetToAuthLanding(token: String?, currentRoute: String?): Boolean =
    token == null && currentRoute != null && currentRoute !in AUTH_GRAPH_ROUTES

private fun androidx.navigation.NavGraphBuilder.authGraph(navController: NavHostController) {
    composable(Routes.AUTH_LANDING) {
        AuthLandingScreen(
            onLogin = { navController.navigate(Routes.LOGIN) },
            onSignUp = { navController.navigate(Routes.SIGN_UP) },
        )
    }
    composable(Routes.LOGIN) {
        val context = LocalContext.current
        LoginScreen(
            onLoggedIn = {
                val target = authenticatedEntryDestination(
                    OnboardingStore.isRequiredAfterLogin(context),
                )
                navController.navigate(target) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onRegister = { navController.navigate(Routes.SIGN_UP) },
            onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
        )
    }
    // Splash, onboarding, sign-up, forgot-password, registration-success.
    authExtraGraph(navController)
}

private fun androidx.navigation.NavGraphBuilder.mainGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel?,
) {
    composable(Routes.HOME) {
        if (homeViewModel != null) {
            HomeScreen(
                onNavigate = { navController.navigate(it) },
                viewModel = homeViewModel,
            )
        } else {
            HomeScreen(onNavigate = { navController.navigate(it) })
        }
    }
    // Shipments hub + drill-downs (registers Routes.SHIPMENTS itself).
    shipmentsGraph(navController)
    // Shop + Cart destinations (registers Routes.SHOP itself).
    shopGraph(navController)
    calculatorGraph(navController)
    dropAlertGraph(navController)
    composable(Routes.CONTACTS) {
        com.ga.airdrop.feature.contacts.ContactsScreen(onNavigate = { navController.navigate(it) })
    }
    composable(Routes.LIVE_CHAT) {
        com.ga.airdrop.feature.contacts.LiveAgentChatScreen(onBack = { navController.popBackStack() })
    }
    // More hub + drill-downs (registers Routes.MORE itself).
    moreGraph(navController)
    more2Graph(navController)
    homeDetailsGraph(navController)

    // Stripe hosted-checkout return (Swift SceneDelegate:432 parity): verify
    // the session, then celebrate / bounce back. Cart is cleared ONLY on
    // verified paid (+ defensively on Done) — never on cancel/not-paid/
    // unconfirmed.
    composable(
        Routes.PAYMENT_RETURN,
        arguments = listOf(
            androidx.navigation.navArgument("sessionId") {
                type = androidx.navigation.NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        com.ga.airdrop.feature.cart.PaymentReturnHost(
            sessionId = entry.arguments?.getString("sessionId").orEmpty(),
            onPaid = { ref, amount ->
                com.ga.airdrop.feature.cart.CartStore.clear()
                navController.navigate(Routes.paymentSuccess(ref, amount)) {
                    // Pop through the cart so Back from Success never lands on
                    // a stale Delivery Method / cleared cart (verify finding).
                    popUpTo(Routes.CART) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNotPaid = {
                navController.navigate(Routes.CART) {
                    // Replace the old CART→DELIVERY_METHOD sandwich with a
                    // single fresh cart (contents intact).
                    popUpTo(Routes.CART) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onUnconfirmed = {
                navController.navigate(Routes.SHIPMENTS) {
                    popUpTo(Routes.PAYMENT_RETURN) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
    // Stripe cancel_url deeplink (airdrop://payment-cancelled): explain first
    // (Swift SceneDelegate "Payment cancelled" alert), then land on the cart
    // with its contents intact.
    composable(Routes.PAYMENT_CANCELLED) {
        com.ga.airdrop.feature.cart.PaymentCancelledHost(
            onDone = {
                navController.navigate(Routes.CART) {
                    popUpTo(Routes.PAYMENT_CANCELLED) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
    composable(
        Routes.PAYMENT_SUCCESS,
        arguments = listOf(
            androidx.navigation.navArgument("ref") {
                type = androidx.navigation.NavType.StringType
                defaultValue = ""
            },
            androidx.navigation.navArgument("amount") {
                type = androidx.navigation.NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        com.ga.airdrop.feature.cart.PaymentSuccessScreen(
            orderReference = entry.arguments?.getString("ref")?.takeIf { it.isNotBlank() },
            formattedAmount = entry.arguments?.getString("amount")?.takeIf { it.isNotBlank() },
            onDone = {
                com.ga.airdrop.feature.cart.CartStore.clear()
                navController.navigate(Routes.HOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}

/** Tab switch = Swift FigmaRouteResolver.switchToTabRoute root replacement. */
fun NavHostController.switchTab(tab: AirdropTab) {
    val route = when (tab) {
        AirdropTab.Home -> Routes.HOME
        AirdropTab.Shipments -> Routes.SHIPMENTS
        AirdropTab.Shop -> Routes.SHOP
        AirdropTab.Help -> Routes.CONTACTS
        AirdropTab.More -> Routes.MORE
    }
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/** Temporary stand-in while screens are being built out; never shipped. */
@Composable
internal fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray200),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = AirdropType.h5,
            color = AirdropTheme.colors.textDarkTitle,
        )
    }
}
