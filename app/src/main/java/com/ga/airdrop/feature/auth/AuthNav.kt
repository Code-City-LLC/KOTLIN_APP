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
 * No new route constants needed — all exist in Routes.kt already.
 *
 * Launch flow (RN LaunchAppView; Figma "Onboarding - Design Done"
 * 40006240:*): token → HOME; first run (onboarding not seen) → ONBOARDING
 * carousel + "Choose Your Look" → AUTH_LANDING; returning-but-signed-out →
 * AUTH_LANDING. Swift's SceneDelegate collapsed this straight to login, but the
 * Onboarding + Choose-Your-Look + AuthLanding are shipped Figma designs, so
 * Figma wins where Swift dropped them (per Kemar's Government-Charges ruling).
 * NOTE: AppRoot's reactive-logout effect must EXCLUDE SPLASH + ONBOARDING or it
 * yanks the first-run flow to AUTH_LANDING the moment it sees token == null.
 */
fun NavGraphBuilder.authExtraGraph(navController: NavHostController) {

    composable(Routes.SPLASH) {
        val context = LocalContext.current
        SplashScreen(
            onFinished = {
                val target = when {
                    AuthTokenStore.tokenFlow.value != null -> Routes.HOME
                    !OnboardingStore.hasSeen(context) -> Routes.ONBOARDING
                    else -> Routes.AUTH_LANDING
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
