package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.CheckoutResponse
import com.ga.airdrop.data.model.CheckoutSessionStatus
import com.ga.airdrop.data.model.CreateCheckoutRequest
import com.ga.airdrop.data.model.InvoiceUrlEnvelope
import com.ga.airdrop.data.model.Payment
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
    ): Result<List<Payment>> = apiResult {
        service.payments(
            page = page,
            perPage = perPage,
            sortBy = "payment_date",
            sortOrder = "desc",
            type = type?.takeIf { it.isNotEmpty() && it != "all" },
            search = normalizedSearch(search),
        ).items
    }

    suspend fun paymentsShortlist(): Result<List<Payment>> = payments(page = 1, perPage = 6)

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
}
