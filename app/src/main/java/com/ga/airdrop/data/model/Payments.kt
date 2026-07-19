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

// ─── NCB PowerTranz (JMD) checkout — mirrors PaymentController::createNcbCheckout
//     and Swift AirdropAPI NcbSessionRequest. The Laravel payload is FLAT:
//     billing + card + delivery fields sit at the top level, delivery_location
//     is a nested object. Card data is sent over TLS to Laravel which forwards
//     it to PowerTranz; the app never stores it. ────────────────────────────────

@Serializable
data class NcbDeliveryLocation(
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class CreateNcbSessionRequest(
    @SerialName("package_ids") val packageIds: List<Int>,
    val currency: String, // must be "JMD"
    @SerialName("is_auction") val isAuction: Boolean,
    // billing
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val address: String,
    val city: String,
    val country: String, // "US" or "JM"
    // card
    @SerialName("card_name") val cardName: String,
    @SerialName("card_number") val cardNumber: String,
    @SerialName("card_month") val cardMonth: String,
    @SerialName("card_year") val cardYear: String,
    @SerialName("card_cvv") val cardCvv: String,
    // delivery context (optional — pickup omits delivery_location)
    @SerialName("delivery_mode") val deliveryMode: String? = null,
    @SerialName("delivery_location") val deliveryLocation: NcbDeliveryLocation? = null,
    @SerialName("pickup_location") val pickupLocation: String? = null,
    @SerialName("delivery_distance_km") val deliveryDistanceKm: Double? = null,
    @SerialName("delivery_charge_total") val deliveryChargeTotal: Double? = null,
    @SerialName("delivery_charge_currency") val deliveryChargeCurrency: String? = null,
)

/**
 * Response from /api/v1/payments/create-ncb-session. `redirect_data` is HTML the
 * WebView must render to run 3DS; `spi_token` is posted to
 * /ncb-complete-payment after the 3DS callback returns.
 */
@Serializable
data class NcbSessionResponse(
    @SerialName("spi_token")
    @Serializable(with = FlexibleStringSerializer::class)
    val spiToken: String? = null,
    @SerialName("redirect_data")
    @Serializable(with = FlexibleStringSerializer::class)
    val redirectData: String? = null,
    @SerialName("checkout_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val checkoutId: String? = null,
)

@Serializable
data class NcbCompletePaymentRequest(
    @SerialName("spi_token") val spiToken: String,
)

@Serializable
data class NcbCompletePaymentResponse(
    @SerialName("invoice_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val invoiceId: Int? = null,
)

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
