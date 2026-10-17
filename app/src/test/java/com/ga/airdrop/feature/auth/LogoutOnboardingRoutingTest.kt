package com.ga.airdrop.feature.auth

import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kemar 2026-10-17, React Native order: Log Out → onboarding → auth landing
 * (RN SettingsView.onLogout resets the stack to OnboardingView). Kotlin used to
 * send logout to Log In and hold the slides back until the next login.
 *
 * `onboardingRequired` / `onboardingRequiredAfterLogin` is the OnboardingStore
 * flag: armed by an explicit logout before the bearer is cleared, cleared when
 * the slides finish.
 */
class LogoutOnboardingRoutingTest {

    /** Without this, AppRoot's reset raced the logout to the landing screen. */
    @Test
    fun anArmedLogoutSendsTheTokenLossResetToOnboarding() {
        assertEquals(Routes.ONBOARDING, tokenLossDestination(onboardingRequired = true))
    }

    /** A bearer lost any other way (a refresh answered 401) arms nothing. */
    @Test
    fun anyOtherLostBearerStillResetsToTheAuthLanding() {
        assertEquals(Routes.AUTH_LANDING, tokenLossDestination(onboardingRequired = false))
    }

    /**
     * The app closed partway through the post-logout slides. They resume now,
     * signed out. They used to be held back until the next login.
     */
    @Test
    fun aSignedOutColdStartResumesAnUnfinishedLogoutOnboarding() {
        assertEquals(
            Routes.ONBOARDING,
            splashDestination(
                isAuthenticated = false,
                onboardingSeen = true,
                onboardingRequiredAfterLogin = true,
            ),
        )
        assertEquals(
            Routes.ONBOARDING,
            splashDestination(
                isAuthenticated = false,
                onboardingSeen = false,
                onboardingRequiredAfterLogin = true,
            ),
        )
    }

    /** Only the armed-flag path changed. */
    @Test
    fun everyOtherLaunchKeepsItsRoute() {
        // First run.
        assertEquals(
            Routes.ONBOARDING,
            splashDestination(
                isAuthenticated = false,
                onboardingSeen = false,
                onboardingRequiredAfterLogin = false,
            ),
        )
        // Returning and signed out, onboarding already seen.
        assertEquals(
            Routes.AUTH_LANDING,
            splashDestination(
                isAuthenticated = false,
                onboardingSeen = true,
                onboardingRequiredAfterLogin = false,
            ),
        )
        // Signed in.
        assertEquals(
            Routes.HOME,
            splashDestination(
                isAuthenticated = true,
                onboardingSeen = true,
                onboardingRequiredAfterLogin = false,
            ),
        )
    }

    /**
     * The post-logout slides end signed out on the auth landing, and finishing
     * them cleared the flag, so the next login goes straight Home. A logout
     * plays them once.
     */
    @Test
    fun finishedLogoutSlidesNeverReplayAtTheNextLogin() {
        assertEquals(Routes.AUTH_LANDING, onboardingCompletionDestination(isAuthenticated = false))
        assertEquals(Routes.HOME, authenticatedEntryDestination(onboardingRequired = false))
    }
}
