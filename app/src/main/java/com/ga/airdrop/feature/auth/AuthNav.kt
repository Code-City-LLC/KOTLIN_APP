package com.ga.airdrop.feature.auth

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
 * No new route constants needed — all exist in Routes.kt already.
 *
 * Launch flow mirrors Swift SceneDelegate.swift:57 (FigmaRouteViewController
 * collapses LaunchApp/ChooseYourLook/Onboarding/AuthLanding/SignIn all to
 * FigmaLoginViewController): token → HOME, otherwise → LOGIN directly. The
 * ONBOARDING/AUTH_LANDING/REGISTRATION_SUCCESS destinations stay registered
 * (valid Figma designs, reachable by route) but are not in the launch path,
 * matching Swift's shipped flow.
 */
fun NavGraphBuilder.authExtraGraph(navController: NavHostController) {

    composable(Routes.SPLASH) {
        SplashScreen(
            onFinished = {
                val target = if (AuthTokenStore.tokenFlow.value != null) Routes.HOME
                else Routes.LOGIN
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.ONBOARDING) {
        OnboardingScreen(
            onFinished = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.SIGN_UP) {
        SignUpScreen(
            onBack = { navController.popBackStack() },
            // Swift SignUpViewController.swift:522-523 — success shows an alert
            // then popViewController back to Login (no dedicated success screen).
            onRegistered = {
                navController.popBackStack(Routes.LOGIN, inclusive = false)
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
