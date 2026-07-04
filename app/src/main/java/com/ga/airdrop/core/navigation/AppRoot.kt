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
import com.ga.airdrop.feature.calculator.calculatorGraph
import com.ga.airdrop.feature.dropalert.dropAlertGraph
import com.ga.airdrop.feature.homedetails.homeDetailsGraph
import com.ga.airdrop.feature.more.moreGraph
import com.ga.airdrop.feature.more2.more2Graph
import com.ga.airdrop.feature.shipments.shipmentsGraph
import com.ga.airdrop.feature.shop.shopGraph

/**
 * App entry. Mirrors the Swift SceneDelegate + FigmaRouteResolver:
 * token → Home tab shell, else auth landing. One shared back stack with the
 * 5 tab roots as destinations (same as iOS's single navigation stack); the
 * glass bottom bar overlays tab-root content.
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val token by AuthTokenStore.tokenFlow.collectAsState()
    val startDestination = if (token != null) Routes.HOME else Routes.AUTH_LANDING

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = when (currentRoute) {
        Routes.HOME -> AirdropTab.Home
        Routes.SHIPMENTS -> AirdropTab.Shipments
        Routes.SHOP -> AirdropTab.Shop
        Routes.CONTACTS -> AirdropTab.Help
        Routes.MORE -> AirdropTab.More
        else -> null
    }

    // Push-notification deep links (route + referenceID) land here once the
    // graph is composed — mirrors FigmaRouteResolver deep-linking.
    val pendingPush by com.ga.airdrop.core.push.PushDeepLink.pending.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingPush, token) {
        if (pendingPush != null && token != null) {
            com.ga.airdrop.core.push.PushDeepLink.consume()?.let { navController.navigate(it) }
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
            mainGraph(navController)
        }
        if (currentTab != null) {
            AirdropBottomBar(
                selected = currentTab,
                onSelect = { tab -> navController.switchTab(tab) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.authGraph(navController: NavHostController) {
    composable(Routes.AUTH_LANDING) {
        AuthLandingScreen(
            onLogin = { navController.navigate(Routes.LOGIN) },
            onSignUp = { navController.navigate(Routes.SIGN_UP) },
        )
    }
    composable(Routes.LOGIN) {
        LoginScreen(
            onLoggedIn = {
                navController.navigate(Routes.HOME) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onRegister = { navController.navigate(Routes.SIGN_UP) },
            onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
        )
    }
    composable(Routes.SIGN_UP) { PlaceholderScreen("Sign Up") }
    composable(Routes.FORGOT_PASSWORD) { PlaceholderScreen("Forgot Password") }
}

private fun androidx.navigation.NavGraphBuilder.mainGraph(navController: NavHostController) {
    composable(Routes.HOME) {
        com.ga.airdrop.feature.home.HomeScreen(onNavigate = { navController.navigate(it) })
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
    // More hub + drill-downs (registers Routes.MORE itself).
    moreGraph(navController)
    more2Graph(navController)
    homeDetailsGraph(navController)
}

/** Tab switch = replace the current tab root, keeping Home as anchor. */
fun NavHostController.switchTab(tab: AirdropTab) {
    val route = when (tab) {
        AirdropTab.Home -> Routes.HOME
        AirdropTab.Shipments -> Routes.SHIPMENTS
        AirdropTab.Shop -> Routes.SHOP
        AirdropTab.Help -> Routes.CONTACTS
        AirdropTab.More -> Routes.MORE
    }
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
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
