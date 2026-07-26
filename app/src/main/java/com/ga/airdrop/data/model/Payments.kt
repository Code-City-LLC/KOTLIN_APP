package com.ga.airdrop.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PaymentPackage(
    val description: String? = null,
    @SerialName("status_name") val statusName: String? = null,
)

@Serializable
data class Payment(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @SerialName("invoice_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val invoiceId: String? = null,
    @SerialName("payment_type")
    @Serializable(with = FlexibleStringSerializer::class)
    val paymentType: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val method: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val currency: String? = null,
    @SerialName("total_amount")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val totalAmount: Double? = null,
    @SerialName("paid_amount")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val paidAmount: Double? = null,
    @SerialName("tracking_code")
    @Serializable(with = FlexibleStringSerializer::class)
    val trackingCode: String? = null,
    @SerialName("payment_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val paymentDate: String? = null,
    @SerialName("package_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val packageId: Int? = null,
    // Product payments carry the order id (Swift receives it on the same row).
    @SerialName("order_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val orderId: Int? = null,
    @SerialName("exchange_rate")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val exchangeRate: Double? = null,
    @SerialName("package")
    val paymentPackage: PaymentPackage? = null,
) {
    val packageDescription: String? get() = paymentPackage?.description
    val packageStatusName: String? get() = paymentPackage?.statusName
    val displayTitle: String get() = invoiceId ?: trackingCode ?: "Payment #${id ?: 0}"
}

@Serializable
data class CreateCheckoutRequest(
    @SerialName("package_ids") val packageIds: List<Int>,
    val currency: String,
    @SerialName("is_auction") val isAuction: Boolean,
    @SerialName("return_url") val returnUrl: String? = null,
)

/** Non-null signal that makes Laravel issue the mobile Stripe deep links. */
const val MOBILE_CHECKOUT_RETURN_URL = "airdrop://payment-success"

@Serializable
data class CheckoutResponse(
    @SerialName("checkout_url") val checkoutUrl: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    @SerialName("package_count") val packageCount: Int? = null,
)

@Serializable
data class CheckoutSessionStatus(
    @SerialName("session_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val sessionId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val status: String? = null,
    @SerialName("payment_status")
    @Serializable(with = FlexibleStringSerializer::class)
    val paymentStatus: String? = null,
    @SerialName("invoice_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val invoiceId: Int? = null,
    @SerialName("amount_total")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val amountTotal: Double? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val currency: String? = null,
    /**
     * The packages this checkout actually settled — see [PaymentIntentStatus.packageIds]
     * for where the ids come from and why the Stripe metadata is the wrong source.
     *
     * ⚠️ THIS is the model the app verifies through. `PaymentReturnViewModel`
     * calls `checkoutSessionStatus`, not `paymentIntentStatus`, so adding the
     * field only to the PaymentIntent model would have left Kemar's
     * "fetch the real packages" screen with an empty list on the path that
     * actually runs — and an empty list is indistinguishable from "nothing was
     * paid for", so it would have silently kept the static rail with no error.
     */
    @SerialName("package_ids")
    val packageIds: List<Int> = emptyList(),
)

// POST /payments/create-payment-sheet — Stripe PaymentSheet bundle
// (Laravel StripePaymentService::createPaymentSheet). `amount` is in cents.
@Serializable
data class PaymentSheetConfig(
    @Serializable(with = FlexibleStringSerializer::class)
    val customer: String? = null,
    @SerialName("ephemeral_key")
    @Serializable(with = FlexibleStringSerializer::class)
    val ephemeralKey: String? = null,
    @SerialName("payment_intent")
    @Serializable(with = FlexibleStringSerializer::class)
    val paymentIntent: String? = null,
    @SerialName("payment_intent_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val paymentIntentId: String? = null,
    @SerialName("publishable_key")
    @Serializable(with = FlexibleStringSerializer::class)
    val publishableKey: String? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val amount: Long? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val currency: String? = null,
)

// GET /payments/payment-intent/{id}/status — server verification after
// PaymentSheet completes; mirrors the hosted-checkout status field shape.
@Serializable
data class PaymentIntentStatus(
    @Serializable(with = FlexibleStringSerializer::class)
    val status: String? = null,
    @SerialName("payment_status")
    @Serializable(with = FlexibleStringSerializer::class)
    val paymentStatus: String? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val amount: Long? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val currency: String? = null,
    @SerialName("invoice_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val invoiceId: String? = null,
    /**
     * The packages this payment ACTUALLY settled (BronzeMountain 92e739ce).
     *
     * Kemar asked that the post-checkout screen show the real packages instead
     * of a static rail; this is the field that makes that possible, and it
     * rides both verification paths so it does not matter which one ran:
     * `GET payments/payment-intent/{pi}/status` and `GET payments/{sessionId}/status`.
     * (The handoff quoted `payments/session/{cs}/status`; BrightHarbor checked
     * and that route does not exist. Kotlin already calls the correct one —
     * see AirdropApiService.checkoutSessionStatus.)
     *
     * ⚠️ NOT the Stripe `packages` metadata, which is a deliberate trap here.
     * That metadata holds tracking codes and is written at checkout — BEFORE
     * payment — so it records what the customer *intended* to buy. These ids
     * come from `packages.packages_invoice_id`, stamped by the fulfilment
     * webhook, and record what was actually paid for. The two diverge on a
     * partial fulfilment, and a post-checkout screen must show the second or it
     * shows the customer packages they did not pay for.
     *
     * Always an array, never null — an unpaid or unfulfilled payment returns
     * `[]` — so callers iterate without a null check. Defaulted to empty so an
     * older server that omits the key still decodes.
     */
    @SerialName("package_ids")
    val packageIds: List<Int> = emptyList(),
)

// GET /payments/{id}/invoice JSON envelope: {data:{url|file_url}},
// {data:"https://..."}, {url} or {file_url}. Raw binary bodies are handled
// by the repository fallback.
@Serializable(with = InvoiceUrlEnvelopeSerializer::class)
data class InvoiceUrlEnvelope(
    val url: String? = null,
)

object InvoiceUrlEnvelopeSerializer : KSerializer<InvoiceUrlEnvelope> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ga.airdrop.data.model.InvoiceUrlEnvelope")

    override fun serialize(encoder: Encoder, value: InvoiceUrlEnvelope) =
        throw UnsupportedOperationException("InvoiceUrlEnvelope is decode-only")

    override fun deserialize(decoder: Decoder): InvoiceUrlEnvelope {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement() as? JsonObject ?: return InvoiceUrlEnvelope()
        val data = obj["data"]
        val url = when (data) {
            is JsonObject -> data.flexString("url", "file_url")
            is JsonPrimitive -> parseFlexString(data)
            else -> null
        } ?: obj.flexString("url", "file_url")
        return InvoiceUrlEnvelope(url)
    }
}
