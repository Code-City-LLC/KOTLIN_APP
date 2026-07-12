package com.ga.airdrop.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationNavigationParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun landingEntryRemovesSignUpBeforeShowingSuccess() {
        val nav = setHarness(Routes.AUTH_LANDING)
        assertSuccessReplacesSignUp(nav, expectedPrevious = Routes.AUTH_LANDING)
    }

    @Test
    fun loginEntryRemovesSignUpBeforeShowingSuccess() {
        val nav = setHarness(Routes.LOGIN)
        assertSuccessReplacesSignUp(nav, expectedPrevious = Routes.LOGIN)
    }

    @Test
    fun successCtaReplacesTheEntireStackWithLogin() {
        val nav = setHarness(Routes.AUTH_LANDING)
        compose.runOnIdle {
            nav.navigate(Routes.SIGN_UP)
            nav.showRegistrationSuccess()
            nav.returnToLoginAfterRegistration()
        }

        assertEquals(Routes.LOGIN, nav.currentDestination?.route)
        compose.runOnIdle { assertEquals(false, nav.popBackStack()) }
    }

    private fun assertSuccessReplacesSignUp(nav: NavHostController, expectedPrevious: String) {
        compose.runOnIdle {
            nav.navigate(Routes.SIGN_UP)
            nav.showRegistrationSuccess()
        }
        assertEquals(Routes.REGISTRATION_SUCCESS, nav.currentDestination?.route)
        compose.runOnIdle { nav.popBackStack() }
        assertEquals(expectedPrevious, nav.currentDestination?.route)
    }

    private fun setHarness(start: String): NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            RegistrationHarness(nav, start)
        }
        compose.waitForIdle()
        return nav
    }
}

@Composable
private fun RegistrationHarness(nav: NavHostController, start: String) {
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.AUTH_LANDING) {}
        composable(Routes.LOGIN) {}
        composable(Routes.SIGN_UP) {}
        composable(Routes.REGISTRATION_SUCCESS) {}
    }
}
