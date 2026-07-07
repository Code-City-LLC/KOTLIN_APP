package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.BrandPalette
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
 */
@Composable
fun PaymentReturnHost(
    sessionId: String,
    onPaid: (ref: String?, amount: String?) -> Unit,
    onNotPaid: (statusText: String) -> Unit,
    onUnconfirmed: (detail: String) -> Unit,
    viewModel: PaymentReturnViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) {
        when (val result = viewModel.verify(sessionId)) {
            is PaymentReturnResult.Success -> onPaid(result.orderReference, result.formattedAmount)
            is PaymentReturnResult.NotPaid -> onNotPaid(result.statusText)
            is PaymentReturnResult.Unconfirmed -> onUnconfirmed(result.detail)
        }
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
