package com.ga.airdrop.feature.cart

import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.data.model.CreateNcbSessionRequest
import com.ga.airdrop.data.model.NcbDeliveryLocation
import java.util.Locale

/**
 * NCB PowerTranz (JMD) checkout logic — exact port of the Swift payment-method
 * VC's `validatedNcbCheckoutInput` + `FigmaNcbThreeDSViewController` callback
 * matcher (FigmaCartViewController.swift 4947–5330). Pure and plain-JVM
 * testable; the ViewModel supplies flow/billing state and a clock year.
 *
 * Wire shape is pinned separately by NcbCheckoutContractTest; this file owns
 * VALIDATION semantics: card number 13–19 digits, expiry MM/YY (or MMYYYY)
 * with a current-or-later year, CVV 3–4 digits, billing address + city
 * required, and billing country restricted to Jamaica / United States —
 * Laravel's `country in:US,JM` validator, mirrored client-side so the
 * customer hears it before the card call.
 */

/** What the card-entry screen collects; PAN/CVV never enter saved state. */
data class NcbCardFields(
    val cardName: String = "",
    val cardNumber: String = "",
    val expiry: String = "",
    val cvv: String = "",
)

sealed interface NcbCheckoutInput {
    data class Ready(val request: CreateNcbSessionRequest) : NcbCheckoutInput
    data class Invalid(val message: String) : NcbCheckoutInput
}

internal fun ncbDigitsOnly(value: String?): String =
    value.orEmpty().trim().filter(Char::isDigit)

/** Swift parseExpiry: "MM/YY" or "MM/YYYY" (separators ignored). */
internal fun parseNcbExpiry(raw: String, currentYear: Int): Pair<String, String>? {
    val digits = ncbDigitsOnly(raw)
    val (monthRaw, yearRaw) = when (digits.length) {
        4 -> digits.take(2) to "20" + digits.takeLast(2)
        6 -> digits.take(2) to digits.takeLast(4)
        else -> return null
    }
    val month = monthRaw.toIntOrNull() ?: return null
    val year = yearRaw.toIntOrNull() ?: return null
    if (month !in 1..12 || year < currentYear) return null
    return String.format(Locale.US, "%02d", month) to yearRaw
}

/** Swift ncbCountryCode: resolve any display/name/ISO form, allow JM/US only. */
internal fun resolveNcbCountryCode(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val code = (CountryCatalog.entry(value)?.isoCode ?: value).uppercase(Locale.US)
    return code.takeIf { it in NCB_SUPPORTED_COUNTRIES }
}

private val NCB_SUPPORTED_COUNTRIES = setOf("JM", "US")

/** Swift resolvedNameParts: billing profile names first, else split cardName. */
internal fun resolveNcbNameParts(
    cardName: String,
    billingFirst: String,
    billingLast: String,
): Pair<String, String> {
    val first = billingFirst.trim()
    val last = billingLast.trim()
    if (first.isNotEmpty() && last.isNotEmpty()) return first to last
    val parts = cardName.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (parts.size >= 2) return parts.first() to parts.drop(1).joinToString(" ")
    return (first.ifEmpty { cardName }) to (last.ifEmpty { cardName })
}

/**
 * Validate everything and build the exact `create-ncb-session` request.
 * Delivery context comes from the owned CheckoutFlow (delivery step already
 * ran); Laravel requires `delivery_mode`, and `delivery_location` when the
 * mode is delivery.
 */
internal fun buildNcbCheckoutInput(
    packageIds: List<Int>,
    isAuction: Boolean,
    billing: CartBillingForm,
    card: NcbCardFields,
    deliveryMode: String?,
    deliveryAddress: String?,
    deliveryLatitude: Double?,
    deliveryLongitude: Double?,
    pickupLocation: String?,
    deliveryFee: Double?,
    deliveryFeeCurrency: String?,
    currentYear: Int,
): NcbCheckoutInput {
    val cardName = card.cardName.trim()
    val cardNumber = ncbDigitsOnly(card.cardNumber)
    val cvv = ncbDigitsOnly(card.cvv)
    val address = billing.address1.trim()
    val city = billing.city.trim()

    if (packageIds.isEmpty()) return NcbCheckoutInput.Invalid("Your cart is empty.")
    if (cardName.isEmpty()) return NcbCheckoutInput.Invalid("Enter the cardholder name.")
    if (cardNumber.length !in 13..19) {
        return NcbCheckoutInput.Invalid("Enter a valid card number.")
    }
    val expiry = parseNcbExpiry(card.expiry, currentYear)
        ?: return NcbCheckoutInput.Invalid("Enter the expiry date as MM/YY.")
    if (cvv.length !in 3..4) return NcbCheckoutInput.Invalid("Enter a valid CVV.")
    if (address.isEmpty()) return NcbCheckoutInput.Invalid("Enter the billing address.")
    if (city.isEmpty()) {
        return NcbCheckoutInput.Invalid(
            "City is required from Profile Information before JMD checkout.",
        )
    }
    val countryCode = resolveNcbCountryCode(billing.country)
        ?: return NcbCheckoutInput.Invalid(
            "NCB checkout currently supports Jamaica and United States billing addresses.",
        )
    val mode = deliveryMode?.trim()?.lowercase(Locale.US)
        ?: return NcbCheckoutInput.Invalid(
            "Choose pickup or delivery before paying. Return to Delivery Method.",
        )
    val location = if (mode == "delivery") {
        val lat = deliveryLatitude
        val lng = deliveryLongitude
        val locAddress = deliveryAddress?.trim().orEmpty()
        if (lat == null || lng == null || locAddress.isEmpty()) {
            return NcbCheckoutInput.Invalid(
                "Your delivery location is incomplete. Return to Delivery Method.",
            )
        }
        NcbDeliveryLocation(address = locAddress, latitude = lat, longitude = lng)
    } else {
        null
    }

    val names = resolveNcbNameParts(cardName, billing.firstName, billing.lastName)
    return NcbCheckoutInput.Ready(
        CreateNcbSessionRequest(
            packageIds = packageIds,
            currency = CheckoutCurrency.JMD.wireValue,
            isAuction = isAuction,
            firstName = names.first,
            lastName = names.second,
            address = address,
            city = city,
            country = countryCode,
            cardName = cardName,
            cardNumber = cardNumber,
            cardMonth = expiry.first,
            cardYear = expiry.second,
            cardCvv = cvv,
            deliveryMode = mode,
            deliveryLocation = location,
            pickupLocation = pickupLocation?.trim()?.takeIf(String::isNotEmpty),
            deliveryDistanceKm = null,
            deliveryChargeTotal = deliveryFee,
            deliveryChargeCurrency = deliveryFeeCurrency
                ?.trim()?.uppercase(Locale.US)?.takeIf { it == "USD" || it == "JMD" },
        ),
    )
}

/**
 * Swift isNcbThreeDSCallback: the 3DS flow is finished when the WebView
 * navigates to the merchant callback — a URL containing `ncb-3ds-callback` /
 * `ncb_3ds_callback`, or carrying a `spi_token`-ish query parameter.
 */
internal fun isNcbThreeDsCallback(url: String?): Boolean {
    val absolute = url?.trim()?.lowercase(Locale.US) ?: return false
    if (absolute.isEmpty()) return false
    if ("ncb-3ds-callback" in absolute || "ncb_3ds_callback" in absolute) return true
    val query = absolute.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return false
    return query.split('&').any { param ->
        param.substringBefore('=')
            .replace("_", "")
            .replace("-", "") == "spitoken"
    }
}

/** Base origin for 3DS HTML (`loadDataWithBaseURL`) — API base minus /api/v1. */
internal fun ncbWebBaseUrl(apiBaseUrl: String): String {
    val trimmed = apiBaseUrl.trim().trimEnd('/')
    return trimmed.removeSuffix("/api/v1").ifEmpty { trimmed }
}
