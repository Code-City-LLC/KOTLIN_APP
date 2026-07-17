package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.repo.PaymentsRepository
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Post-Stripe payment-return verification — Swift SceneDelegate.swift:432-620
 * parity. The Stripe Custom Tab redirects to
 * `airdrop://payment-success?session_id=…`; this verifies the session
 * server-side before celebrating.
 *
 * Retry semantics (load-bearing, do not collapse): a SUCCESSFUL response that
 * says not-paid is AUTHORITATIVE — return NotPaid immediately, never retry.
 * Only thrown/network failures retry (3 attempts, 800ms/1600ms backoff).
 * After 3 failures the payment MAY have gone through — Unconfirmed must never
 * imply failure.
 */
sealed interface PaymentReturnResult {
    data class Success(val orderReference: String?, val formattedAmount: String?) :
        PaymentReturnResult

    data class NotPaid(val statusText: String) : PaymentReturnResult
    data class Unconfirmed(val detail: String) : PaymentReturnResult
}

class PaymentReturnViewModel(
    private val payments: PaymentsRepository = PaymentsRepository(ApiClient.service),
) : ViewModel() {

    suspend fun verify(sessionId: String): PaymentReturnResult =
        verifySession(sessionId) { payments.checkoutSessionStatus(it) }
}

/**
 * The verify core, injectable for plain-JVM tests. [fetch] is
 * PaymentsRepository.checkoutSessionStatus in production.
 */
internal suspend fun verifySession(
    sessionId: String,
    retryDelayMs: (attempt: Int) -> Long = { (it + 1) * 800L },
    fetch: suspend (String) -> Result<com.ga.airdrop.data.model.CheckoutSessionStatus>,
): PaymentReturnResult {
    if (sessionId.isBlank()) {
        return PaymentReturnResult.Unconfirmed(
            "We didn't receive a Stripe session id on return.",
        )
    }
    var lastError: Throwable? = null
    repeat(3) { attempt ->
        fetch(sessionId)
            .onSuccess { s ->
                val paid = s.paymentStatus?.lowercase() == "paid" ||
                    s.status?.lowercase() == "paid"
                return if (paid) {
                    // amount_total is MAJOR units — no /100 (Swift parity).
                    val amount = if (s.amountTotal != null && s.currency != null) {
                        String.format(
                            Locale.US,
                            "%s %.2f",
                            s.currency!!.uppercase(Locale.US),
                            s.amountTotal,
                        )
                    } else {
                        null
                    }
                    PaymentReturnResult.Success(sessionId, amount)
                } else {
                    PaymentReturnResult.NotPaid(s.paymentStatus ?: s.status ?: "unknown")
                }
            }
            .onFailure { e ->
                lastError = e
                if (attempt < 2) delay(retryDelayMs(attempt))
            }
    }
    return PaymentReturnResult.Unconfirmed(lastError?.message ?: "network error")
}

/**
 * Spinner while the session verifies, then dispatches exactly once. Hosted at
 * [com.ga.airdrop.core.navigation.Routes.PAYMENT_RETURN].
 *
 * A paid session navigates straight to the success screen. Not-paid and
 * unconfirmed outcomes first explain themselves in an alert (Swift
 * SceneDelegate parity — it never teleports the user without saying why),
 * then run the caller's navigation when the alert is dismissed.
 */
@Composable
fun PaymentReturnHost(
    sessionId: String,
    onPaid: (ref: String?, amount: String?) -> Unit,
    onNotPaid: (statusText: String) -> Unit,
    onUnconfirmed: (detail: String) -> Unit,
    viewModel: PaymentReturnViewModel = viewModel(),
) {
    PaymentReturnContent(
        sessionId = sessionId,
        verify = viewModel::verify,
        onPaid = onPaid,
        onNotPaid = onNotPaid,
        onUnconfirmed = onUnconfirmed,
    )
}

/** Injectable production content used by the hosted-return instrumentation tests. */
@Composable
internal fun PaymentReturnContent(
    sessionId: String,
    verify: suspend (String) -> PaymentReturnResult,
    onPaid: (ref: String?, amount: String?) -> Unit,
    onNotPaid: (statusText: String) -> Unit,
    onUnconfirmed: (detail: String) -> Unit,
) {
    var pendingAlert by remember { mutableStateOf<PaymentReturnResult?>(null) }

    LaunchedEffect(sessionId) {
        when (val result = verify(sessionId)) {
            is PaymentReturnResult.Success -> onPaid(result.orderReference, result.formattedAmount)
            // Alert first; navigation runs on dismiss.
            is PaymentReturnResult.NotPaid -> pendingAlert = result
            is PaymentReturnResult.Unconfirmed -> pendingAlert = result
        }
    }

    when (val alert = pendingAlert) {
        is PaymentReturnResult.NotPaid -> PaymentOutcomeAlert(
            // Swift: "Payment incomplete" — authoritative not-paid answer.
            title = "Payment incomplete",
            message = "Stripe reports status \"${alert.statusText}\". Try again from the cart.",
            onDismiss = {
                pendingAlert = null
                onNotPaid(alert.statusText)
            },
        )
        is PaymentReturnResult.Unconfirmed -> PaymentOutcomeAlert(
            // Swift: never imply failure — the charge may have gone through,
            // and implying failure tempts the customer to pay twice.
            title = "Couldn't confirm payment",
            message = "Your payment may have completed — please check your Shipments " +
                "before paying again. (${alert.detail})",
            onDismiss = {
                pendingAlert = null
                onUnconfirmed(alert.detail)
            },
        )
        else -> Unit
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray150)
            .testTag("payment-return-verifying"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AirdropTheme.colors.orangeMain)
    }
}

/**
 * Stripe cancel_url landing (airdrop://payment-cancelled). Swift SceneDelegate
 * parity: tell the user nothing was charged before showing the intact cart.
 */
@Composable
fun PaymentCancelledHost(onDone: () -> Unit) {
    PaymentOutcomeAlert(
        title = "Payment cancelled",
        message = "No payment was completed. Your cart is still available.",
        onDismiss = onDone,
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray150)
            .testTag("payment-cancelled-host"),
    )
}

/** One-button outcome alert, Swift presentPaymentResultAlert counterpart. */
@Composable
private fun PaymentOutcomeAlert(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = { Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle) },
        text = { Text(text = message, style = AirdropType.body2, color = colors.textDescription) },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("payment-outcome-ok"),
            ) {
                Text(text = "OK", style = AirdropType.button, color = AirdropTheme.colors.orangeMain)
            }
        },
        modifier = Modifier.testTag("payment-outcome-alert"),
    )
}
