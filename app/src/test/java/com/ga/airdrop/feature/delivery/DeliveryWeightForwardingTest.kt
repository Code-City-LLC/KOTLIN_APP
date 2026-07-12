package com.ga.airdrop.feature.delivery

import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.repo.DeliveryGateway
import com.ga.airdrop.feature.cart.CartStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PR44-replacement gate proofs: (1) the fail-closed cart weight — a partial
 * sum is NEVER produced; (2) the weight reaches the gateway calls verbatim;
 * (3) a late geocode result cannot commit after the marker moved (race guard).
 */
class DeliveryWeightForwardingTest {

    private fun line(id: Int, weightKg: Double?, auction: Boolean = false) =
        CartStore.CartLine(id = id, title = "L$id", qty = 1, priceUsd = 1.0, isAuction = auction, weightKg = weightKg)

    // ── 1. fail-closed weight ───────────────────────────────────────────────

    @Test
    fun `all package lines weighted - the exact sum is produced`() {
        assertEquals(3.86, cartTotalWeightKg(listOf(line(1, 1.36), line(2, 2.5)))!!, 1e-9)
    }

    @Test
    fun `any package line with unknown weight fails closed to null`() {
        // A knowingly understated partial sum must never reach the server.
        assertNull(cartTotalWeightKg(listOf(line(1, 1.36), line(2, null))))
        assertNull(cartTotalWeightKg(listOf(line(1, 1.36), line(2, 0.0))))
        assertNull(cartTotalWeightKg(listOf(line(1, -1.0))))
    }

    @Test
    fun `auction lines are weightless by design and never block the sum`() {
        // Swift parity: auction lines carry no weight; only package lines count.
        assertEquals(1.36, cartTotalWeightKg(listOf(line(1, 1.36), line(2, null, auction = true)))!!, 1e-9)
        // Auction-only carts have no package weight at all.
        assertNull(cartTotalWeightKg(listOf(line(1, null, auction = true))))
        assertNull(cartTotalWeightKg(emptyList()))
    }

    // ── 2. gateway forwarding ───────────────────────────────────────────────

    private open class RecordingGateway : DeliveryGateway {
        var validateWeight: Double? = -99.0
        var saveWeight: Double? = -99.0
        override suspend fun deliverySettings() = Result.failure<DeliverySettingsPayload>(IllegalStateException("unused"))
        override suspend fun validateLocation(
            latitude: Double,
            longitude: Double,
            address: String?,
            totalWeightKg: Double?,
        ): Result<DeliveryValidationResponse> {
            validateWeight = totalWeightKg
            return Result.success(DeliveryValidationResponse(valid = true))
        }
        override suspend fun savePickupPreference(pickupLocation: String?) =
            Result.success(DeliveryPreference())
        override suspend fun saveDeliveryPreference(
            location: DeliveryLocation,
            totalWeightKg: Double?,
        ): Result<DeliveryPreference> {
            saveWeight = totalWeightKg
            return Result.success(DeliveryPreference())
        }
        override suspend fun preference() = Result.failure<DeliveryPreference>(IllegalStateException("unused"))
        override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
            Result.failure<ReverseGeocodeResult>(IllegalStateException("down"))
        override suspend fun searchPlaces(query: String) = Result.success(emptyList<PlaceResult>())
    }

    @Test
    fun `the fail-closed weight is forwarded verbatim into validate and save`() = runBlocking {
        val gateway = RecordingGateway()
        val lines = listOf(line(1, 1.36), line(2, 2.5))
        val weight = cartTotalWeightKg(lines)

        gateway.validateLocation(18.0, -77.0, "Kingston", weight)
        gateway.saveDeliveryPreference(DeliveryLocation(address = "Kingston"), weight)
        assertEquals(3.86, gateway.validateWeight!!, 1e-9)
        assertEquals(3.86, gateway.saveWeight!!, 1e-9)

        // Unknown-weight cart forwards NULL, never a partial number.
        val unknown = cartTotalWeightKg(listOf(line(1, 1.36), line(2, null)))
        gateway.validateLocation(18.0, -77.0, "Kingston", unknown)
        gateway.saveDeliveryPreference(DeliveryLocation(address = "Kingston"), unknown)
        assertNull(gateway.validateWeight)
        assertNull(gateway.saveWeight)
    }

    // ── 3. geocoder chain + race guard ──────────────────────────────────────

    @Test
    fun `laravel result wins - device fallback fires only on failure`() = runBlocking {
        val laravelUp = object : RecordingGateway() {
            override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
                Result.success(ReverseGeocodeResult(address = "Mandeville, Manchester"))
        }
        assertEquals(
            "Mandeville, Manchester",
            resolveGeocodedAddress(laravelUp, { _, _ -> "DEVICE" }, 18.0, -77.0),
        )
        // Laravel down → device geocoder.
        assertEquals(
            "Kingston, Jamaica",
            resolveGeocodedAddress(RecordingGateway(), { _, _ -> "Kingston, Jamaica" }, 18.0, -77.0),
        )
        // Both unavailable → null (card keeps waiting; no raw coord name).
        assertNull(resolveGeocodedAddress(RecordingGateway(), null, 18.0, -77.0))
    }

    @Test
    fun `a late geocode result cannot commit after the marker moved`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var resolved: String? = null
        val chainCoord = 18.0 to -77.0
        var markerCoord: Pair<Double, Double>? = chainCoord

        val job = launch {
            resolved = resolveGeocodedAddress(
                RecordingGateway(),
                { _, _ -> gate.await(); "Late Place" },
                chainCoord.first,
                chainCoord.second,
            )
        }
        yield()
        // The marker moves while the slow device geocode is still in flight.
        markerCoord = 19.0 to -76.0
        gate.complete(Unit)
        job.join()

        assertEquals("Late Place", resolved)
        // The VM's final guard refuses the commit for the stale chain…
        assertEquals(false, geocodeCommitAllowed(markerCoord, chainCoord))
        // …and would allow it had the marker stayed put.
        assertEquals(true, geocodeCommitAllowed(chainCoord, chainCoord))
    }
}
