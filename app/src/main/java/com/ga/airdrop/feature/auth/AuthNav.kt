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
 * Registers SPLASH, CHOOSE_LOOK, ONBOARDING, SIGN_UP, FORGOT_PASSWORD and
 * REGISTRATION_SUCCESS (AUTH_LANDING and LOGIN stay in AppRoot's authGraph).
 * No new route constants needed — all exist in Routes.kt already.
 *
 * Launch flow (Swift FigmaOnboardingViewController): token → HOME; first run
 * (onboarding not seen) → CHOOSE_LOOK → ONBOARDING carousel → AUTH_LANDING;
 * returning-but-signed-out → AUTH_LANDING. Swift takes precedence for flow.
 * NOTE: AppRoot's reactive-logout effect must EXCLUDE these auth routes or it
 * yanks the first-run flow to AUTH_LANDING when token == null.
 */
fun NavGraphBuilder.authExtraGraph(navController: NavHostController) {

    composable(Routes.SPLASH) {
        val context = LocalContext.current
        SplashScreen(
            onFinished = {
                val target = when {
                    AuthTokenStore.tokenFlow.value != null -> Routes.HOME
                    !OnboardingStore.hasSeen(context) -> Routes.CHOOSE_LOOK
                    else -> Routes.AUTH_LANDING
                }
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.CHOOSE_LOOK) {
        ChooseYourLookScreen(
            onContinue = {
                navController.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.CHOOSE_LOOK) { inclusive = true }
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
            // Swift SignUpViewController.swift:522-523 — success shows an alert
            // then returns to Login. From the Landing → Sign Up path LOGIN is
            // not on the back stack, so a bare popBackStack is a no-op and the
            // success dialog dead-ends (WORK ORDER B1) — fall back to
            // navigating to Login explicitly.
            onRegistered = {
                if (!navController.popBackStack(Routes.LOGIN, inclusive = false)) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.AUTH_LANDING)
                        launchSingleTop = true
                    }
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
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}
