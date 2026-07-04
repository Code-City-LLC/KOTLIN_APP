package com.ga.airdrop.feature.auth

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.navigation.Routes

/**
 * AUTH feature group extras — registration for the orchestrator's NavHost:
 *
 *     authExtraGraph(navController)
 *
 * Registers SPLASH, ONBOARDING, SIGN_UP, FORGOT_PASSWORD and
 * REGISTRATION_SUCCESS (AUTH_LANDING and LOGIN stay in AppRoot's authGraph).
 * No new route constants needed — all exist in Routes.kt already. Splash
 * mirrors the Swift SceneDelegate routing: token → HOME, first run →
 * ONBOARDING, otherwise → AUTH_LANDING.
 */
fun NavGraphBuilder.authExtraGraph(navController: NavHostController) {

    composable(Routes.SPLASH) {
        val context = LocalContext.current
        SplashScreen(
            onFinished = {
                val target = when {
                    AuthTokenStore.tokenFlow.value != null -> Routes.HOME
                    OnboardingStore.hasSeen(context) -> Routes.AUTH_LANDING
                    else -> Routes.ONBOARDING
                }
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.ONBOARDING) {
        OnboardingScreen(
            onFinished = {
                navController.navigate(Routes.AUTH_LANDING) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.SIGN_UP) {
        SignUpScreen(
            onBack = { navController.popBackStack() },
            onRegistered = {
                navController.navigate(Routes.REGISTRATION_SUCCESS) {
                    popUpTo(Routes.SIGN_UP) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.FORGOT_PASSWORD) {
        ForgotPasswordScreen(
            onBackToLogin = { navController.popBackStack() },
        )
    }

    composable(Routes.REGISTRATION_SUCCESS) {
        RegistrationSuccessScreen(
            onLogin = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.AUTH_LANDING)
                    launchSingleTop = true
                }
            },
        )
    }
}
