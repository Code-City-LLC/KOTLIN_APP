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
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
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

    data class NotPaid(val statusText: String, val terminal: Boolean = false) : PaymentReturnResult
    data class Unconfirmed(val detail: String) : PaymentReturnResult
}

class PaymentReturnViewModel(
    private val payments: PaymentsRepository = PaymentsRepository(ApiClient.service),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    /** Bare Stripe cancel URLs recover the one persisted exact session. */
    suspend fun verifyPendingCancellation(): PaymentReturnResult {
        val owner = sessionBoundary.capture()
            ?: return PaymentReturnResult.Unconfirmed("The checkout owner is no longer signed in.")
        val pending = CheckoutFlowStore.pending(owner)
            ?: return PaymentReturnResult.Unconfirmed("No exact pending checkout could be recovered.")
        return verify(pending.checkoutSessionId)
    }

    suspend fun verify(sessionId: String): PaymentReturnResult {
        val owner = sessionBoundary.capture()
            ?: return PaymentReturnResult.Unconfirmed("The checkout owner is no longer signed in.")
        if (CheckoutFlowStore.pending(sessionId, owner) == null) {
            return PaymentReturnResult.Unconfirmed("This checkout session is not pending for the signed-in account.")
        }
        val requestOwner = sessionBoundary.requestOwner(owner)
            ?: return PaymentReturnResult.Unconfirmed("The checkout owner changed before verification.")
        val result = verifySession(sessionId) {
            payments.checkoutSessionStatus(it, requestOwner.provenance)
        }
        var committed: PaymentReturnResult? = null
        val applied = sessionBoundary.runWhileCurrent(owner) {
            if (CheckoutFlowStore.pending(sessionId, owner) == null) {
                return@runWhileCurrent false
            }
            when (result) {
                is PaymentReturnResult.Success -> {
                    if (!commitVerifiedPaidCheckout(sessionId, owner)) {
                        return@runWhileCurrent false
                    }
                }
                is PaymentReturnResult.NotPaid -> if (result.terminal) {
                    if (!CheckoutFlowStore.releaseTerminalNotPaid(sessionId, owner)) {
                        return@runWhileCurrent false
                    }
                }
                is PaymentReturnResult.Unconfirmed -> Unit
            }
            committed = result
            true
        }
        return if (applied) committed ?: PaymentReturnResult.Unconfirmed(
            "The checkout result could not be committed.",
        ) else PaymentReturnResult.Unconfirmed("The checkout owner changed during verification.")
    }
}

/** Crash-recoverable two-phase paid commit; caller holds the auth transition lock. */
internal fun commitVerifiedPaidCheckout(
    sessionId: String,
    owner: com.ga.airdrop.core.session.AuthenticatedSessionOwner,
): Boolean {
    val paidKeys = CheckoutFlowStore.pending(sessionId, owner)?.cartKeys?.toSet() ?: return false
    // Cross-prefs crash safety: remove exact rows durably first; only then
    // durably consume pending. A crash between commits leaves pending replayable.
    if (!CartStore.removePaidKeysDurably(paidKeys)) return false
    return CheckoutFlowStore.consumePaid(sessionId, owner) != null
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
                if (s.sessionId.isNullOrBlank() || s.sessionId != sessionId) {
                    return PaymentReturnResult.Unconfirmed(
                        "The payment response did not match this checkout session.",
                    )
                }
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
                    val statusText = s.paymentStatus ?: s.status ?: "unknown"
                    PaymentReturnResult.NotPaid(
                        statusText = statusText,
                        terminal = isTerminalNonPaidStatus(s.status, s.paymentStatus),
                    )
                }
            }
            .onFailure { e ->
                lastError = e
                if (attempt < 2) delay(retryDelayMs(attempt))
            }
    }
    return PaymentReturnResult.Unconfirmed(lastError?.message ?: "network error")
}

internal fun isTerminalNonPaidStatus(status: String?, paymentStatus: String?): Boolean {
    val normalizedStatus = status?.trim()?.lowercase()
    val normalizedPayment = paymentStatus?.trim()?.lowercase()
    return normalizedStatus in setOf("expired", "cancelled", "canceled", "failed", "unpaid") ||
        (normalizedStatus == "complete" && normalizedPayment != "paid")
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
            title = if (alert.terminal) "Payment incomplete" else "Payment still pending",
            message = if (alert.terminal) {
                "Stripe reports status \"${alert.statusText}\". Try again from the cart."
            } else {
                "Stripe still reports status \"${alert.statusText}\". Check Shipments before paying again."
            },
            onDismiss = {
                pendingAlert = null
                if (alert.terminal) {
                    onNotPaid(alert.statusText)
                } else {
                    onUnconfirmed("Stripe still reports ${alert.statusText}; checkout remains pending.")
                }
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
        CircularProgressIndicator(color = BrandPalette.OrangeMain)
    }
}

/**
 * Stripe cancel_url landing (airdrop://payment-cancelled). Swift SceneDelegate
 * parity: tell the user nothing was charged before showing the intact cart.
 */
@Composable
fun PaymentCancelledHost(
    onTerminalNotPaid: () -> Unit,
    onUnconfirmed: () -> Unit,
    onPaid: (ref: String?, amount: String?) -> Unit = { _, _ -> },
    verify: (suspend () -> PaymentReturnResult)? = null,
    viewModel: PaymentReturnViewModel = viewModel(),
) {
    var result by remember { mutableStateOf<PaymentReturnResult?>(null) }
    LaunchedEffect(Unit) {
        result = verify?.invoke() ?: viewModel.verifyPendingCancellation()
    }
    when (val outcome = result) {
        is PaymentReturnResult.Success -> LaunchedEffect(outcome) {
            onPaid(outcome.orderReference, outcome.formattedAmount)
        }
        is PaymentReturnResult.NotPaid -> PaymentOutcomeAlert(
            title = if (outcome.terminal) "Payment cancelled" else "Couldn't confirm cancellation",
            message = if (outcome.terminal) {
                "Stripe confirmed the checkout is ${outcome.statusText}. Your cart is available to retry."
            } else {
                "Stripe still reports ${outcome.statusText}. This checkout remains pending to prevent a duplicate payment."
            },
            onDismiss = if (outcome.terminal) onTerminalNotPaid else onUnconfirmed,
        )
        is PaymentReturnResult.Unconfirmed -> PaymentOutcomeAlert(
            title = "Couldn't confirm cancellation",
            message = "The checkout remains pending to prevent a duplicate payment. " + outcome.detail,
            onDismiss = onUnconfirmed,
        )
        null -> Unit
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(AirdropTheme.colors.gray150)
            .testTag("payment-cancelled-host"),
        contentAlignment = Alignment.Center,
    ) {
        if (result == null) CircularProgressIndicator(color = BrandPalette.OrangeMain)
    }
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
                Text(text = "OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
            }
        },
        modifier = Modifier.testTag("payment-outcome-alert"),
    )
}
