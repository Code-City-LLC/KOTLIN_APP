package com.ga.airdrop.feature.cart

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaymentOutcomeParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun notPaidExplainsBeforeNavigating() {
        var navigated = false
        compose.setContent {
            AirdropTheme {
                PaymentReturnContent(
                    sessionId = "cs_unpaid",
                    verify = { PaymentReturnResult.NotPaid("unpaid") },
                    onPaid = { _, _ -> error("not paid") },
                    onNotPaid = { navigated = true },
                    onUnconfirmed = { error("not unconfirmed") },
                )
            }
        }

        compose.onNodeWithText("Payment incomplete").assertIsDisplayed()
        compose.onNodeWithText("Stripe reports status \"unpaid\". Try again from the cart.")
            .assertIsDisplayed()
        compose.runOnIdle { assertFalse(navigated) }
        compose.onNodeWithTag("payment-outcome-ok").performClick()
        compose.runOnIdle { assertTrue(navigated) }
    }

    @Test
    fun unconfirmedWarnsAgainstDoublePaymentBeforeNavigating() {
        var detail: String? = null
        compose.setContent {
            AirdropTheme {
                PaymentReturnContent(
                    sessionId = "cs_unknown",
                    verify = { PaymentReturnResult.Unconfirmed("network down") },
                    onPaid = { _, _ -> error("not paid") },
                    onNotPaid = { error("not unpaid") },
                    onUnconfirmed = { detail = it },
                )
            }
        }

        compose.onNodeWithText("Couldn't confirm payment").assertIsDisplayed()
        compose.onNodeWithText(
            "Your payment may have completed — please check your Shipments before paying again. " +
                "(network down)",
        ).assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, detail) }
        compose.onNodeWithTag("payment-outcome-ok").performClick()
        compose.runOnIdle { assertEquals("network down", detail) }
    }

    @Test
    fun paidDispatchesImmediatelyWithoutAnOutcomeAlert() {
        var paidRef: String? = null
        compose.setContent {
            AirdropTheme {
                PaymentReturnContent(
                    sessionId = "cs_paid",
                    verify = { PaymentReturnResult.Success("cs_paid", "USD 12.00") },
                    onPaid = { ref, _ -> paidRef = ref },
                    onNotPaid = { error("not unpaid") },
                    onUnconfirmed = { error("not unconfirmed") },
                )
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { assertEquals("cs_paid", paidRef) }
        compose.onNodeWithTag("payment-outcome-alert").assertDoesNotExist()
    }

    @Test
    fun cancelledExplainsThatTheCartRemainsBeforeNavigating() {
        var done = false
        compose.setContent {
            AirdropTheme { PaymentCancelledHost(onDone = { done = true }) }
        }

        compose.onNodeWithText("Payment cancelled").assertIsDisplayed()
        compose.onNodeWithText("No payment was completed. Your cart is still available.")
            .assertIsDisplayed()
        compose.runOnIdle { assertFalse(done) }
        compose.onNodeWithTag("payment-outcome-ok").performClick()
        compose.runOnIdle { assertTrue(done) }
    }
}
