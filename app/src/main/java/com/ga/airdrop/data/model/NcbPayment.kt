package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NCB PowerTranz JMD checkout (Swift FigmaCheckoutPaymentMethodViewController +
 * FigmaNcbThreeDSViewController parity). Two-step, 3-D Secure:
 *   1. POST /payments/create-ncb-session — card + billing + delivery context →
 *      {spi_token, redirect_data(HTML 3DS challenge), checkout_id}.
 *   2. Render redirect_data in a WebView; the issuer 3DS challenge redirects to
 *      the ncb-3ds-callback; the app extracts the spi_token.
 *   3. POST /payments/ncb-complete-payment {spi_token} → {invoice_id}.
 *
 * Card fields are sent over TLS to Laravel, which forwards them to PowerTranz;
 * the app NEVER persists them (cleared from memory right after the request).
 */
@Serializable
data class CreateNcbSessionRequest(
    @SerialName("package_ids") val packageIds: List<Int>,
    // NO defaults on currency/is_auction: AirdropJson uses encodeDefaults=false, which
    // omits any property whose value equals its default. "JMD"/false are exactly the
    // values we send, so a default here silently drops these required fields from the
    // wire → Laravel 422. (Matches the Stripe CreateCheckoutRequest discipline.)
    val currency: String,
    @SerialName("is_auction") val isAuction: Boolean,
    // Billing (Laravel: required, country in US|JM)
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val address: String,
    val city: String,
    val country: String,
    // Card (Laravel: card_number 13-19, card_month size 2, card_year size 4, cvv 3-4)
    @SerialName("card_name") val cardName: String,
    @SerialName("card_number") val cardNumber: String,
    @SerialName("card_month") val cardMonth: String,
    @SerialName("card_year") val cardYear: String,
    @SerialName("card_cvv") val cardCvv: String,
    // Delivery context (same shape the hosted-checkout metadata uses)
    @SerialName("delivery_mode") val deliveryMode: String,
    @SerialName("delivery_location") val deliveryLocation: NcbDeliveryLocation? = null,
    @SerialName("pickup_location") val pickupLocation: String? = null,
    @SerialName("delivery_distance_km") val deliveryDistanceKm: Double? = null,
    @SerialName("delivery_charge_total") val deliveryChargeTotal: Double? = null,
    @SerialName("delivery_charge_currency") val deliveryChargeCurrency: String? = null,
)

@Serializable
data class NcbDeliveryLocation(
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class NcbSessionResponse(
    @SerialName("spi_token")
    @Serializable(with = FlexibleStringSerializer::class)
    val spiToken: String? = null,
    // PowerTranz HTML/redirect blob to load into a WebView (NOT a URL to navigate to).
    @SerialName("redirect_data")
    @Serializable(with = FlexibleStringSerializer::class)
    val redirectData: String? = null,
    @SerialName("checkout_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val checkoutId: String? = null,
)

@Serializable
data class NcbCompleteRequest(
    @SerialName("spi_token") val spiToken: String,
)

@Serializable
data class NcbCompleteResponse(
    @SerialName("invoice_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val invoiceId: String? = null,
)
