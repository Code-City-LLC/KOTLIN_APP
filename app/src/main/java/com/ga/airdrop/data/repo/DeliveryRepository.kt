package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeRequest
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.model.SaveDeliveryPreferenceRequest
import com.ga.airdrop.data.model.SearchPlacesRequest
import com.ga.airdrop.data.model.ValidateLocationRequest

/**
 * Seam over the delivery endpoints so the ViewModel's suspend flows are
 * testable with a recording fake (same pattern as CustomerTierGateway).
 */
interface DeliveryGateway {
    suspend fun deliverySettings(): Result<DeliverySettingsPayload>
    suspend fun validateLocation(
        latitude: Double,
        longitude: Double,
        address: String? = null,
        totalWeightKg: Double? = null,
    ): Result<DeliveryValidationResponse>
    suspend fun savePickupPreference(pickupLocation: String?): Result<DeliveryPreference>
    suspend fun saveDeliveryPreference(
        location: DeliveryLocation,
        totalWeightKg: Double? = null,
    ): Result<DeliveryPreference>
    suspend fun preference(): Result<DeliveryPreference>
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<ReverseGeocodeResult>
    suspend fun searchPlaces(query: String): Result<List<PlaceResult>>
}

class DeliveryRepository(private val service: AirdropApiService) : DeliveryGateway {

    override suspend fun deliverySettings(): Result<DeliverySettingsPayload> = apiResult {
        val envelope = service.deliverySettings()
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to load delivery settings")
        }
        envelope.data
    }

    override suspend fun validateLocation(
        latitude: Double,
        longitude: Double,
        address: String?,
        totalWeightKg: Double?,
    ): Result<DeliveryValidationResponse> = apiResult {
        service.validateDeliveryLocation(
            ValidateLocationRequest(
                latitude = latitude,
                longitude = longitude,
                address = address,
                totalWeightKg = totalWeightKg,
            ),
        )
    }

    override suspend fun savePickupPreference(pickupLocation: String?): Result<DeliveryPreference> =
        savePreference(
            SaveDeliveryPreferenceRequest(
                deliveryMode = "pickup",
                pickupLocation = pickupLocation,
            ),
        )

    override suspend fun saveDeliveryPreference(
        location: DeliveryLocation,
        totalWeightKg: Double?,
    ): Result<DeliveryPreference> = savePreference(
        SaveDeliveryPreferenceRequest(
            deliveryMode = "delivery",
            deliveryLocation = location,
            totalWeightKg = totalWeightKg,
        ),
    )

    suspend fun savePreference(request: SaveDeliveryPreferenceRequest): Result<DeliveryPreference> =
        apiResult {
            val envelope = service.saveDeliveryPreference(request)
            if (envelope.success == false || envelope.data == null) {
                error(envelope.message ?: "Failed to save delivery preference")
            }
            envelope.data
        }

    override suspend fun preference(): Result<DeliveryPreference> = apiResult {
        val envelope = service.deliveryPreference()
        if (envelope.success == false || envelope.data == null) {
            error(envelope.message ?: "Failed to get delivery preference")
        }
        envelope.data
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<ReverseGeocodeResult> =
        apiResult {
            val envelope = service.reverseGeocode(
                ReverseGeocodeRequest(latitude = latitude, longitude = longitude),
            )
            if (envelope.success == false || envelope.data == null) {
                error(envelope.message ?: "Failed to reverse-geocode coordinates")
            }
            envelope.data
        }

    override suspend fun searchPlaces(query: String): Result<List<PlaceResult>> = apiResult {
        val envelope = service.searchPlaces(SearchPlacesRequest(q = query.trim()))
        if (envelope.success == false) {
            error(envelope.message ?: "Search service unavailable")
        }
        envelope.data?.results ?: emptyList()
    }
}
