package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.data.model.CheckoutSessionStatus
import com.ga.airdrop.data.model.CreateCheckoutRequest
import com.ga.airdrop.data.model.InvoiceUrlEnvelope
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.Payment
import com.ga.airdrop.data.model.PaymentIntentStatus
import com.ga.airdrop.data.model.PaymentSheetConfig
import java.io.File

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

    suspend fun createCheckout(
        packageIds: List<Int>,
        currency: String,
        isAuction: Boolean = true,
    ): Result<CheckoutResponse> = apiResult {
        val envelope = service.createCheckout(
            CreateCheckoutRequest(packageIds = packageIds, currency = currency, isAuction = isAuction),
        )
        val data = envelope.data
        if (envelope.success == false || data == null || data.checkoutUrl.isNullOrEmpty()) {
            error(envelope.message ?: "Failed to create checkout session")
        }
        data
    }

    suspend fun checkoutSessionStatus(sessionId: String): Result<CheckoutSessionStatus> = apiResult {
        val envelope = service.checkoutSessionStatus(sessionId)
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to fetch checkout session status")
        }
        envelope.data
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
