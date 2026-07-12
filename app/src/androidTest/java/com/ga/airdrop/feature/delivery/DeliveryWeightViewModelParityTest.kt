package com.ga.airdrop.feature.delivery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.repo.DeliveryGateway
import com.ga.airdrop.feature.cart.CartStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryWeightViewModelParityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun clearCart() = instrumentation.runOnMainSync { CartStore.clear() }

    @Test
    fun viewModelForwardsExactKnownWeightThroughValidateAndSave() {
        val gateway = RecordingGateway()
        val viewModel = createViewModel(
            gateway,
            listOf(line(1, 1.36), line(2, 2.5)),
        )

        selectDeliveryLocation(viewModel)
        waitUntil { gateway.validateCalls == 1 }
        instrumentation.runOnMainSync { viewModel.onContinue() }
        waitUntil { gateway.saveCalls == 1 }

        assertEquals(3.86, gateway.validateWeight!!, 1e-9)
        assertEquals(3.86, gateway.saveWeight!!, 1e-9)
    }

    @Test
    fun viewModelForwardsNullWhenAnyPackageWeightIsUnknown() {
        val gateway = RecordingGateway()
        val viewModel = createViewModel(
            gateway,
            listOf(line(1, 1.36), line(2, null)),
        )

        selectDeliveryLocation(viewModel)
        waitUntil { gateway.validateCalls == 1 }
        instrumentation.runOnMainSync { viewModel.onContinue() }
        waitUntil { gateway.saveCalls == 1 }

        assertNull(gateway.validateWeight)
        assertNull(gateway.saveWeight)
    }

    @Test
    fun lateDeviceGeocodeCannotOverwriteTheMovedMarker() {
        val gateway = RecordingGateway()
        val viewModel = createViewModel(gateway, listOf(line(1, 1.0)))
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        viewModel.deviceGeocoder = { latitude, _ ->
            if (latitude == 18.0) {
                firstStarted.countDown()
                withContext(NonCancellable) {
                    releaseFirst.await(5, TimeUnit.SECONDS)
                }
                "Late Place"
            } else {
                "Current Place"
            }
        }

        instrumentation.runOnMainSync { viewModel.onMapPointPicked(18.0, -77.0) }
        assertTrue("first geocoder started", firstStarted.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { viewModel.onMapPointPicked(19.0, -76.0) }
        waitUntil { viewModel.state.value.validatedAddress == "Current Place" }
        releaseFirst.countDown()
        Thread.sleep(200)

        assertEquals(19.0 to -76.0, viewModel.state.value.markerCoord)
        assertEquals("Current Place", viewModel.state.value.validatedAddress)
    }

    private fun createViewModel(
        gateway: RecordingGateway,
        lines: List<CartStore.CartLine>,
    ): DeliveryMethodViewModel {
        lateinit var viewModel: DeliveryMethodViewModel
        instrumentation.runOnMainSync {
            CartStore.init(instrumentation.targetContext)
            CartStore.clear()
            lines.forEach(CartStore::add)
            viewModel = DeliveryMethodViewModel(repo = gateway)
        }
        return viewModel
    }

    private fun selectDeliveryLocation(viewModel: DeliveryMethodViewModel) {
        instrumentation.runOnMainSync {
            viewModel.onModeSelected(DeliveryMode.Delivery)
            viewModel.onSearchResultPicked(
                PlaceResult(address = "Kingston, Jamaica", latitude = 18.0, longitude = -77.0),
            )
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition was not met within ${timeoutMs}ms", predicate())
    }

    private fun line(id: Int, weightKg: Double?) = CartStore.CartLine(
        id = id,
        packageId = id,
        title = "Package $id",
        weightKg = weightKg,
    )

    private class RecordingGateway : DeliveryGateway {
        @Volatile var validateCalls = 0
        @Volatile var saveCalls = 0
        @Volatile var validateWeight: Double? = -1.0
        @Volatile var saveWeight: Double? = -1.0

        override suspend fun deliverySettings() = Result.success(DeliverySettingsPayload())

        override suspend fun validateLocation(
            latitude: Double,
            longitude: Double,
            address: String?,
            totalWeightKg: Double?,
        ): Result<DeliveryValidationResponse> {
            validateWeight = totalWeightKg
            validateCalls++
            return Result.success(DeliveryValidationResponse(valid = true))
        }

        override suspend fun savePickupPreference(pickupLocation: String?) =
            Result.success(DeliveryPreference())

        override suspend fun saveDeliveryPreference(
            location: DeliveryLocation,
            totalWeightKg: Double?,
        ): Result<DeliveryPreference> {
            saveWeight = totalWeightKg
            saveCalls++
            return Result.success(DeliveryPreference(deliveryMode = "delivery"))
        }

        override suspend fun preference() = Result.failure<DeliveryPreference>(IllegalStateException("unset"))

        override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
            Result.failure<ReverseGeocodeResult>(IllegalStateException("use device fallback"))

        override suspend fun searchPlaces(query: String) = Result.success(emptyList<PlaceResult>())
    }
}
