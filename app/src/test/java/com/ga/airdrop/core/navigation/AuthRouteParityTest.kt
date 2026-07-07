package com.ga.airdrop.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Test

class AuthRouteParityTest {

    @Test
    fun chooseLookIsNotResetByNullTokenReactiveLogout() {
        assertFalse(shouldResetToAuthLanding(token = null, currentRoute = Routes.CHOOSE_LOOK))
    }
}
