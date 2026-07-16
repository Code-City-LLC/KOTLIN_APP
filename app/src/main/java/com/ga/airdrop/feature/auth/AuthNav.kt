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
 * 40006240:*): token → HOME unless an explicit logout armed onboarding for
 * the next login; first run (onboarding not seen) → ONBOARDING carousel +
 * "Choose Your Look" → AUTH_LANDING; returning-but-signed-out →
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
                val target = splashDestination(
                    isAuthenticated = AuthTokenStore.tokenFlow.value != null,
                    onboardingSeen = OnboardingStore.hasSeen(context),
                    onboardingRequiredAfterLogin = OnboardingStore.isRequiredAfterLogin(context),
                )
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.ONBOARDING) {
        OnboardingScreen(
            onFinished = {
                val target = onboardingCompletionDestination(
                    isAuthenticated = AuthTokenStore.tokenFlow.value != null,
                )
                navController.navigate(target) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    composable(Routes.SIGN_UP) {
        SignUpScreen(
            onBack = { navController.popBackStack() },
            // Current Swift: success presents the full-screen Registration
            // Successful glass sheet (verification-link copy + "Back to Log
            // In") instead of the old alert. Route to the previously-orphaned
            // screen (FuchsiaTower Audit #59 F5); SignUp is popped off so back
            // can't return to the submitted form.
            onRegistered = {
                navController.showRegistrationSuccess()
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
                navController.returnToLoginAfterRegistration()
            },
        )
    }
}

/**
 * Explicit-logout onboarding is a post-authentication gate. While signed out,
 * keep that flag armed and route to login even if the first-run flag is false.
 */
internal fun splashDestination(
    isAuthenticated: Boolean,
    onboardingSeen: Boolean,
    onboardingRequiredAfterLogin: Boolean,
): String = when {
    isAuthenticated -> authenticatedEntryDestination(onboardingRequiredAfterLogin)
    onboardingRequiredAfterLogin -> Routes.AUTH_LANDING
    !onboardingSeen -> Routes.ONBOARDING
    else -> Routes.AUTH_LANDING
}

/** One route policy shared by cold-start restore and successful login. */
internal fun authenticatedEntryDestination(onboardingRequired: Boolean): String =
    if (onboardingRequired) Routes.ONBOARDING else Routes.HOME

/** Onboarding is valid both before authentication and after explicit relogin. */
internal fun onboardingCompletionDestination(isAuthenticated: Boolean): String =
    if (isAuthenticated) Routes.HOME else Routes.AUTH_LANDING

internal fun NavHostController.showRegistrationSuccess() {
    com.ga.airdrop.core.session.clearLocalUserSession(context)
    navigate(Routes.REGISTRATION_SUCCESS) {
        popUpTo(Routes.SIGN_UP) { inclusive = true }
        launchSingleTop = true
    }
}

internal fun NavHostController.returnToLoginAfterRegistration() {
    com.ga.airdrop.core.session.clearLocalUserSession(context)
    navigate(Routes.LOGIN) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
