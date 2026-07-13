package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.repo.DeliveryGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused recovery of the safe geocoder/display behavior from PR47. */
class DeliveryGeocoderParityTest {

    @Test
    fun `Laravel address wins without invoking device fallback`() = runBlocking {
        var deviceCalls = 0
        val result = resolveGeocodedAddress(
            gateway = FakeGateway(reverseResult = Result.success(ReverseGeocodeResult(address = "Kingston"))),
            deviceGeocoder = { _, _ -> deviceCalls++; "Device" },
            latitude = 18.0,
            longitude = -76.8,
        )
        assertEquals("Kingston", result)
        assertEquals(0, deviceCalls)
    }

    @Test
    fun `device fallback resolves when Laravel has no address`() = runBlocking {
        val result = resolveGeocodedAddress(
            gateway = FakeGateway(reverseResult = Result.failure(IllegalStateException("offline"))),
            deviceGeocoder = { _, _ -> "St Andrew, Jamaica" },
            latitude = 18.0,
            longitude = -76.8,
        )
        assertEquals("St Andrew, Jamaica", result)
    }

    @Test
    fun `stale marker result cannot commit`() {
        assertTrue(geocodeCommitAllowed(18.0 to -76.8, 18.0 to -76.8))
        assertFalse(geocodeCommitAllowed(18.1 to -76.9, 18.0 to -76.8))
    }

    @Test
    fun `placemark formatting follows Swift order and adjacent dedupe`() {
        assertEquals(
            "Devon House, Kingston, Jamaica, Kingston",
            formatDevicePlaceName(" Devon House ", "Kingston", "Kingston", "Jamaica", "Kingston"),
        )
    }

    @Test
    fun `search rendering is capped at five results`() {
        val results = (1..7).map { PlaceResult(address = "Result $it") }
        assertEquals((1..5).map { "Result $it" }, visibleDeliverySearchResults(results).map { it.address })
    }

    private class FakeGateway(
        private val reverseResult: Result<ReverseGeocodeResult>,
    ) : DeliveryGateway {
        override suspend fun deliverySettings() = Result.success(DeliverySettingsPayload())
        override suspend fun validateLocation(
            latitude: Double,
            longitude: Double,
            address: String?,
            totalWeightKg: Double?,
        ) = Result.success(DeliveryValidationResponse(valid = true))
        override suspend fun savePickupPreference(pickupLocation: String?) = Result.success(DeliveryPreference())
        override suspend fun saveDeliveryPreference(location: DeliveryLocation, totalWeightKg: Double?) =
            Result.success(DeliveryPreference())
        override suspend fun preference() = Result.failure<DeliveryPreference>(IllegalStateException("unset"))
        override suspend fun reverseGeocode(latitude: Double, longitude: Double) = reverseResult
        override suspend fun searchPlaces(query: String) = Result.success(emptyList<PlaceResult>())
    }
}
