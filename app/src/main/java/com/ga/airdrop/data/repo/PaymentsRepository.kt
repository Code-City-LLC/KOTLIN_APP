package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.data.model.CreateNcbSessionRequest
import com.ga.airdrop.data.model.NcbCompletePaymentRequest
import com.ga.airdrop.data.model.NcbCompletePaymentResponse
import com.ga.airdrop.data.model.NcbSessionResponse
import com.ga.airdrop.data.model.CheckoutSessionStatus
import com.ga.airdrop.data.model.CreateCheckoutRequest
import com.ga.airdrop.data.model.InvoiceUrlEnvelope
import com.ga.airdrop.data.model.MOBILE_CHECKOUT_RETURN_URL
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.model.PaymentIntentStatus
import com.ga.airdrop.data.model.PaymentSheetConfig
import java.io.File
import java.util.Locale

sealed interface InvoiceLocation {
    data class Remote(val url: String) : InvoiceLocation
    data class Local(val file: File) : InvoiceLocation
}

class PaymentsRepository(private val service: AirdropApiService) {

    suspend fun payments(
        page: Int = 1,
        perPage: Int = 15,
        type: String? = null,
        search: String? = null,
    ): Result<Paginated<Payment>> = apiResult {
        service.payments(
            page = page,
            perPage = perPage,
            sortBy = "payment_date",
            sortOrder = "desc",
            type = type?.takeIf { it.isNotEmpty() && it != "all" },
            search = normalizedSearch(search),
        )
    }

    suspend fun paymentsShortlist(): Result<List<Payment>> =
        payments(page = 1, perPage = 6).map { it.items }

    // GET /payments/{id}/invoice answers with a JSON envelope containing a
    // URL, or with raw PDF/image bytes (legacy route) which are spooled to
    // cacheDir, mirroring Swift's invoiceURL(invoiceID:).
    suspend fun paymentInvoice(invoiceId: Int, cacheDir: File): Result<InvoiceLocation> = apiResult {
        val bytes = service.paymentInvoice(invoiceId).bytes()
        val remoteUrl = runCatching {
            AirdropJson.decodeFromString(InvoiceUrlEnvelope.serializer(), bytes.decodeToString())
        }.getOrNull()?.url
        if (!remoteUrl.isNullOrBlank()) {
            InvoiceLocation.Remote(remoteUrl)
        } else {
            val file = File(cacheDir, "invoice-$invoiceId.pdf")
            if (file.exists()) file.delete()
            file.writeBytes(bytes)
            InvoiceLocation.Local(file)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createCheckout(
        packageIds: List<Int>,
        currency: String,
        isAuction: Boolean = true,
        userNote: String? = null,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<CheckoutResponse> {
        val normalizedCurrency = currency.trim().uppercase(Locale.US)
        if (normalizedCurrency != "USD") {
            return Result.failure(IllegalArgumentException("Stripe checkout is available in USD only"))
        }
        if (packageIds.isEmpty() || packageIds.any { it <= 0 }) {
            return Result.failure(IllegalArgumentException("Checkout package IDs must be positive"))
        }
        return apiResult {
        val envelope = service.createCheckout(
            authRevision = expectedSession.revision.toString(),
            sessionId = expectedSession.sessionId,
            body = CreateCheckoutRequest(
                packageIds = packageIds,
                currency = normalizedCurrency,
                isAuction = isAuction,
                returnUrl = MOBILE_CHECKOUT_RETURN_URL,
            ),
        )
        val data = envelope.data
        if (envelope.success == false || data == null || data.checkoutUrl.isNullOrEmpty()) {
            error(envelope.message ?: "Failed to create checkout session")
        }
        data
    }
    }

    /**
     * NCB PowerTranz (JMD) — step 1: create the 3DS session. Mirrors
     * PaymentController::createNcbCheckout. USD is Stripe-only; this endpoint
     * requires JMD. Returns {spi_token, redirect_data, checkout_id}; the caller
     * renders redirect_data in a WebView for 3DS, then calls
     * [ncbCompletePayment] with the spi_token.
     */
    suspend fun createNcbSession(
        request: CreateNcbSessionRequest,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<NcbSessionResponse> {
        if (request.currency.trim().uppercase(Locale.US) != "JMD") {
            return Result.failure(IllegalArgumentException("NCB PowerTranz is available in JMD only"))
        }
        if (request.packageIds.isEmpty() || request.packageIds.any { it <= 0 }) {
            return Result.failure(IllegalArgumentException("Checkout package IDs must be positive"))
        }
        return apiResult {
            val envelope = service.createNcbSession(
                authRevision = expectedSession.revision.toString(),
                sessionId = expectedSession.sessionId,
                body = request,
            )
            val data = envelope.data
            if (envelope.success == false || data == null ||
                data.spiToken.isNullOrEmpty() || data.redirectData.isNullOrEmpty()
            ) {
                error(envelope.message ?: "Failed to start JMD checkout")
            }
            data
        }
    }

    /** NCB PowerTranz (JMD) — step 2: complete after the 3DS round-trip. */
    suspend fun ncbCompletePayment(
        spiToken: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<NcbCompletePaymentResponse> {
        val token = spiToken.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Missing NCB spi_token"))
        }
        return apiResult {
            val envelope = service.ncbCompletePayment(
                authRevision = expectedSession.revision.toString(),
                sessionId = expectedSession.sessionId,
                body = NcbCompletePaymentRequest(spiToken = token),
            )
            val data = envelope.data
            if (envelope.success == false || data == null || data.invoiceId == null) {
                error(envelope.message ?: "JMD payment could not be confirmed")
            }
            data
        }
    }

    suspend fun checkoutSessionStatus(
        sessionId: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<CheckoutSessionStatus> {
        if (sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Checkout session ID is required"))
        }
        return apiResult {
        val envelope = service.checkoutSessionStatus(
            authRevision = expectedSession.revision.toString(),
            ownerSessionId = expectedSession.sessionId,
            sessionId = sessionId,
        )
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to fetch checkout session status")
        }
        envelope.data
    }
    }

    suspend fun createPaymentSheet(
        packageIds: List<Int>,
        currency: String,
        isAuction: Boolean = false,
    ): Result<PaymentSheetConfig> = apiResult {
        val envelope = service.createPaymentSheet(
            CreateCheckoutRequest(packageIds = packageIds, currency = currency, isAuction = isAuction),
        )
        val data = envelope.data
        if (envelope.success == false || data == null || data.paymentIntent.isNullOrEmpty()) {
            error(envelope.message ?: "Unable to process the payment request. Please try again.")
        }
        data
    }

    suspend fun paymentIntentStatus(paymentIntentId: String): Result<PaymentIntentStatus> = apiResult {
        val envelope = service.paymentIntentStatus(paymentIntentId)
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to fetch payment status")
        }
        envelope.data
    }
}
