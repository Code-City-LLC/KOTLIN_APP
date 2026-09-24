package com.ga.airdrop.core.push

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A server that encoded the success_url placeholder (Laravel a4d6940d0) sends
 * `session_id=%7BCHECKOUT_SESSION_ID%7D`, which Stripe leaves as
 * `{CHECKOUT_SESSION_ID}`. That return, and one with no id at all, must still
 * navigate to the payment-return host — which verifies the pending checkout.
 */
@RunWith(AndroidJUnit4::class)
class PaymentReturnPlaceholderRouteTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theRawPlaceholderReachesThePaymentReturnHost() {
        assertEquals(
            "{CHECKOUT_SESSION_ID}",
            hostArgumentFor("airdrop://payment-success?session_id={CHECKOUT_SESSION_ID}"),
        )
    }

    @Test
    fun theEncodedPlaceholderReachesThePaymentReturnHost() {
        assertEquals(
            "{CHECKOUT_SESSION_ID}",
            hostArgumentFor("airdrop://payment-success?session_id=%7BCHECKOUT_SESSION_ID%7D"),
        )
    }

    @Test
    fun aReturnWithoutASessionIdReachesThePaymentReturnHost() {
        assertEquals("", hostArgumentFor("airdrop://payment-success"))
    }

    private fun hostArgumentFor(link: String): String? {
        val route = requireNotNull(PushDeepLink.resolveUri(Uri.parse(link)))
        var received: String? = null
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "start") {
                composable("start") {}
                composable(
                    Routes.PAYMENT_RETURN,
                    arguments = listOf(
                        navArgument("sessionId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry -> received = entry.arguments?.getString("sessionId").orEmpty() }
            }
        }
        compose.runOnIdle { navController.navigate(route) }
        compose.waitForIdle()
        return received
    }
}
