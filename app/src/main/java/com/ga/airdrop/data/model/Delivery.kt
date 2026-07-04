package com.ga.airdrop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mobile mirror of Laravel /api/v1/delivery/* (DeliveryLocationController).

@Serializable
data class DeliveryWarehouse(
    @Serializable(with = FlexibleIntSerializer::class)
    val id: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val address: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val longitude: Double? = null,
    @SerialName("is_primary")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isPrimary: Boolean? = null,
    // Present only on validate-location's nearest_warehouse.
    @SerialName("distance_km")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val distanceKm: Double? = null,
    @SerialName("duration_minutes")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val durationMinutes: Double? = null,
)

@Serializable
data class DeliverySettings(
    @SerialName("max_delivery_distance_km")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val maxDeliveryDistanceKm: Double? = null,
    @SerialName("delivery_enabled")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val deliveryEnabled: Boolean? = null,
    val warehouses: List<DeliveryWarehouse> = emptyList(),
    @SerialName("has_api_key")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val hasApiKey: Boolean? = null,
)

// GET /delivery/settings → {success, data:{settings, google_maps_api_key}}
@Serializable
data class DeliverySettingsPayload(
    val settings: DeliverySettings? = null,
    @SerialName("google_maps_api_key")
    @Serializable(with = FlexibleStringSerializer::class)
    val googleMapsApiKey: String? = null,
)

@Serializable
data class ValidateLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    @SerialName("total_weight_kg") val totalWeightKg: Double? = null,
)

@Serializable
data class DeliveryRange(
    @SerialName("after_km")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val afterKm: Double? = null,
    @SerialName("per_km")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val perKm: Double? = null,
    @SerialName("charge_per_interval")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val chargePerInterval: Double? = null,
)

// POST /delivery/validate-location — flat response, no `data` wrapper.
@Serializable
data class DeliveryValidationResponse(
    val success: Boolean? = null,
    val valid: Boolean? = null,
    val reason: String? = null,
    val message: String? = null,
    @SerialName("nearest_warehouse")
    val nearestWarehouse: DeliveryWarehouse? = null,
    @SerialName("distance_km")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val distanceKm: Double? = null,
    @SerialName("delivery_fee")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val deliveryFee: Double? = null,
    @SerialName("fee_currency")
    @Serializable(with = FlexibleStringSerializer::class)
    val feeCurrency: String? = null,
    @SerialName("delivery_fee_usd")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val deliveryFeeUsd: Double? = null,
    @SerialName("delivery_range")
    val deliveryRange: DeliveryRange? = null,
)

@Serializable
data class DeliveryLocation(
    @Serializable(with = FlexibleStringSerializer::class)
    val address: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val longitude: Double? = null,
    @SerialName("formatted_address")
    @Serializable(with = FlexibleStringSerializer::class)
    val formattedAddress: String? = null,
    // Added server-side by ParishResolver::enrichDeliveryLocation.
    @Serializable(with = FlexibleStringSerializer::class)
    val parish: String? = null,
)

@Serializable
data class SaveDeliveryPreferenceRequest(
    // "pickup" or "delivery".
    @SerialName("delivery_mode") val deliveryMode: String,
    @SerialName("delivery_location") val deliveryLocation: DeliveryLocation? = null,
    @SerialName("pickup_location") val pickupLocation: String? = null,
    @SerialName("total_weight_kg") val totalWeightKg: Double? = null,
)

// `data` of GET /delivery/preference and POST /delivery/save-preference.
@Serializable
data class DeliveryPreference(
    @SerialName("delivery_mode")
    @Serializable(with = FlexibleStringSerializer::class)
    val deliveryMode: String? = null,
    @SerialName("delivery_location")
    val deliveryLocation: DeliveryLocation? = null,
    @SerialName("pickup_location")
    @Serializable(with = FlexibleStringSerializer::class)
    val pickupLocation: String? = null,
)

@Serializable
data class ReverseGeocodeRequest(
    val latitude: Double,
    val longitude: Double,
)

// `data` of POST /delivery/reverse-geocode.
@Serializable
data class ReverseGeocodeResult(
    @Serializable(with = FlexibleStringSerializer::class)
    val address: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val parish: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val longitude: Double? = null,
)

@Serializable
data class SearchPlacesRequest(
    val q: String,
)

@Serializable
data class PlaceResult(
    @Serializable(with = FlexibleStringSerializer::class)
    val address: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val longitude: Double? = null,
    @SerialName("place_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val placeId: String? = null,
)

// `data` of POST /delivery/search-places.
@Serializable
data class PlaceSearchResults(
    val results: List<PlaceResult> = emptyList(),
)
